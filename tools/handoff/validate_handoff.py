#!/usr/bin/env python3
"""Walidator docs/HANDOFF.md (tylko odczyt, stdlib, bez sieci).

Sprawdza strukturę i wykrywalne rozbieżności; NIE dowodzi, że opis jest prawdziwy ani aktualny.
Git jest czytany wyłącznie przez `git --no-optional-locks` (rev-parse, cat-file, diff, status, ls-files).
Kod wyjścia: 0 = brak ERROR (mogą być WARN), 1 = są ERROR, 2 = błąd użycia (np. brak pliku).

Użycie: python3 tools/handoff/validate_handoff.py [--file docs/HANDOFF.md] [--repo .] [--now ISO]
Format pól: docs/HANDOFF.md, blok ```handoff z liniami `klucz: wartość`.
"""

import argparse
import os
import re
import subprocess
import sys
from datetime import date, datetime, timedelta, timezone

REQUIRED = (
    "updated_at", "status", "branch", "pr", "base_sha", "verified_code_sha", "dirty_tree", "dirty_files",
    "outside_handoff_changes", "task_state", "plan_point", "done", "unfinished", "next_action", "blockers",
    "do_not_touch",
)
REPEATABLE = ("test",)
STATUSES = ("plan", "active", "blocked", "ready_for_review", "done")
TASK_STATES = ("none", "implemented", "tested", "awaiting_device", "awaiting_merge")
RESULTS = ("PASS", "FAIL", "NOT_RUN")
HANDOFF_FILES = ("docs/HANDOFF.md", "docs/HISTORIA-SESJI.md")
# Jawna lista plików dokumentacyjnych, których zmiana NIE unieważnia testów kodu. Skrypty, konfiguracja,
# zależności, workflow CI i całe .claude/ (settings, hooki, skille, komendy) są poza listą.
DOCS_ONLY_FILES = HANDOFF_FILES + ("docs/PLAN-1.0.md", "docs/PLAN-FINALNY.md", "README.md")
STALE_AFTER = timedelta(hours=48)
FUTURE_SLACK = timedelta(minutes=5)

SHA_RE = re.compile(r"^[0-9a-f]{40}$")
DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}[+-]\d{2}:\d{2}$")
PR_RE = re.compile(r"^https://github\.com/[\w.-]+/[\w.-]+/pull/\d+$")
SECRET_RES = (
    re.compile(r"(?i)(password|passwd|token|api[_-]?key|secret)\s*=\s*\S{6,}"),
    re.compile(r"://[^/\s:@]+:[^/\s@]+@"),
)


class Result:
    def __init__(self):
        self.errors, self.warnings = [], []

    def error(self, code, text):
        self.errors.append(f"{code}: {text}")

    def warn(self, code, text):
        self.warnings.append(f"{code}: {text}")


def warsaw_offset(utc_dt):
    """Offset Europe/Warsaw dla chwili (reguła UE: ostatnia niedziela marca/października, 01:00 UTC)."""
    def last_sunday(month):
        d = date(utc_dt.year, month, 31)
        return d - timedelta(days=(d.weekday() + 1) % 7)
    start = datetime.combine(last_sunday(3), datetime.min.time(), tzinfo=timezone.utc) + timedelta(hours=1)
    end = datetime.combine(last_sunday(10), datetime.min.time(), tzinfo=timezone.utc) + timedelta(hours=1)
    return timedelta(hours=2) if start <= utc_dt.astimezone(timezone.utc) < end else timedelta(hours=1)


def parse_block(text, res):
    lines, inside, found = [], False, False
    for raw in text.splitlines():
        if not inside and raw.strip() == "```handoff":
            inside = found = True
        elif inside and raw.strip() == "```":
            break
        elif inside:
            lines.append(raw)
    if not found:
        res.error("no_block", "brak bloku ```handoff w dokumencie")
        return None
    fields, tests = {}, []
    for raw in lines:
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        key, sep, value = line.partition(":")
        key, value = key.strip(), value.strip()
        if not sep:
            res.error("bad_line", f"linia bez 'klucz: wartość': {line[:60]}")
        elif key in REPEATABLE:
            tests.append(value)
        elif key in fields:
            res.error("duplicate_field", f"pole '{key}' występuje więcej niż raz")
        elif key not in REQUIRED:
            res.warn("unknown_field", f"nieznane pole '{key}'")
        else:
            fields[key] = value
    return fields, tests


def git(repo, *args):
    env = dict(os.environ, GIT_OPTIONAL_LOCKS="0")
    try:
        p = subprocess.run(["git", "--no-optional-locks", "-C", str(repo), *args], capture_output=True,
                           text=True, env=env, timeout=30)
    except (OSError, subprocess.TimeoutExpired):
        return None
    return p.stdout if p.returncode == 0 else None


def has_commit(repo, sha):
    return git(repo, "cat-file", "-e", f"{sha}^{{commit}}") is not None


def worktree_changes(repo):
    """Ścieżki zmienione w drzewie roboczym (śledzone i nieśledzone) wg `status -z`."""
    out = git(repo, "status", "--porcelain", "-z", "--untracked-files=all")
    paths, tokens, i = [], (out or "").split("\0"), 0
    while i < len(tokens):
        t = tokens[i]
        if len(t) > 3:
            paths.append(t[3:])
            if t[0] in "RC":
                i += 1  # kolejny token to ścieżka źródłowa
        i += 1
    return paths


def check_fields(f, res, now):
    if f["status"] not in STATUSES:
        res.error("bad_status", f"status '{f['status']}' spoza {', '.join(STATUSES)}")
    if f["task_state"] not in TASK_STATES:
        res.error("bad_task_state", f"task_state '{f['task_state']}' spoza {', '.join(TASK_STATES)}")
    if f["pr"] != "none" and not PR_RE.match(f["pr"]):
        res.error("bad_pr", "pr to 'none' albo URL https://github.com/<o>/<r>/pull/<n>")
    if not SHA_RE.match(f["base_sha"]):
        res.error("bad_sha", "base_sha musi być pełnym SHA (40 znaków hex)")
    if f["verified_code_sha"] != "none" and not SHA_RE.match(f["verified_code_sha"]):
        res.error("bad_sha", "verified_code_sha musi być pełnym SHA (40 znaków hex) albo 'none'")
    check_date(f["updated_at"], res, now)
    check_dirty_fields(f, res)
    if f["status"] == "done" and f["task_state"] in ("awaiting_device", "awaiting_merge"):
        res.warn("unmet_criterion", f"status done, ale task_state={f['task_state']}: kryterium niespełnione")
    for k, v in f.items():
        if any(r.search(v) for r in SECRET_RES):
            res.warn("secret", f"pole '{k}' wygląda na zawierające sekret; usuń go z dokumentu")


def check_date(value, res, now):
    if not DATE_RE.match(value):
        res.error("bad_date", "updated_at: oczekiwano ISO 8601 z sekundami i offsetem, np. 2026-11-07T14:00:00+01:00")
        return
    try:
        dt = datetime.fromisoformat(value)
    except ValueError:
        res.error("bad_date", f"updated_at '{value}' nie jest poprawną datą")
        return
    if dt.utcoffset() != warsaw_offset(dt):
        res.error("bad_date", f"offset {value[-6:]} nie zgadza się z Europe/Warsaw dla tej daty")
    elif now - dt > STALE_AFTER:
        res.warn("stale", f"updated_at starszy niż 48 h ({value})")
    elif dt - now > FUTURE_SLACK:
        res.warn("future_date", f"updated_at z przyszłości ({value})")


def check_dirty_fields(f, res):
    tree, files, outside = f["dirty_tree"], f["dirty_files"], f["outside_handoff_changes"]
    if tree not in ("clean", "dirty"):
        res.error("dirty_inconsistent", "dirty_tree to 'clean' albo 'dirty'")
    elif tree == "clean" and (files != "none" or outside != "none"):
        res.error("dirty_inconsistent", "dirty_tree=clean, ale dirty_files/outside_handoff_changes nie są 'none'")
    elif tree == "dirty" and files == "none":
        res.error("dirty_inconsistent", "dirty_tree=dirty wymaga listy w dirty_files")


def check_tests(f, tests, res, repo, git_ok):
    if not tests:
        res.warn("no_tests", "brak wierszy test: (jeśli nie uruchamiano: 'test: <kontrola> | NOT_RUN | none | powód')")
    has_sha_pass = False
    for raw in tests:
        parts = [p.strip() for p in raw.split(" | ", 3)]
        if len(parts) != 4 or not all(parts[:2]):
            res.error("bad_test_row", f"wiersz testu: 'komenda | WYNIK | SHA lub working_tree | dowód': {raw[:60]}")
            continue
        cmd, result, sha, evidence = parts
        if result not in RESULTS:
            res.error("bad_test_row", f"wynik '{result}' spoza {', '.join(RESULTS)} ({cmd})")
            continue
        if not evidence:
            res.error("bad_test_row", f"{result} wymaga dowodu lub powodu ({cmd})")
        if result != "NOT_RUN" and sha == "none":
            res.error("bad_test_row", f"{result} wymaga SHA lub working_tree ({cmd})")
        elif sha not in ("none", "working_tree") and not SHA_RE.match(sha):
            res.error("bad_sha", f"SHA w wierszu testu '{cmd}' musi mieć 40 znaków hex, 'working_tree' lub 'none'")
        elif SHA_RE.match(sha) and git_ok and not has_commit(repo, sha):
            res.warn("sha_unavailable", f"SHA testu '{cmd}' niedostępny lokalnie (płytki klon lub brak fetch)")
        if result == "PASS" and SHA_RE.match(sha):
            has_sha_pass = True
        if f["status"] == "done" and result == "FAIL":
            res.error("test_inconsistent", f"status done przy FAIL: {cmd}")
        elif f["status"] == "done" and result == "NOT_RUN":
            res.warn("test_inconsistent", f"status done przy NOT_RUN: {cmd}")
    if f["status"] in ("ready_for_review", "done") and tests and not has_sha_pass:
        res.warn("not_proof", "brak PASS na konkretnym SHA; working_tree nie jest dowodem dla commita")


def check_git(f, res, repo):
    if git(repo, "rev-parse", "--is-inside-work-tree") is None:
        res.warn("no_git", "brak repozytorium git w --repo; pominięto kontrole git")
        return False
    branch = (git(repo, "branch", "--show-current") or "").strip()
    if branch != f["branch"]:
        res.warn("branch_differs", f"HANDOFF: '{f['branch']}', bieżąca gałąź: '{branch or 'detached HEAD'}'")
    changed = [p for p in worktree_changes(repo) if p not in HANDOFF_FILES]
    if changed:
        res.warn("dirty_tree", f"drzewo robocze ma zmiany poza plikami handoff/historii: "
                              f"{', '.join(changed[:5])}{' …' if len(changed) > 5 else ''}")
    if f["verified_code_sha"] == "none":
        res.warn("no_verified_sha", "brak verified_code_sha: nie wiadomo, który kod był sprawdzony")
    unavailable = False
    for key in ("base_sha", "verified_code_sha"):
        sha = f[key]
        if SHA_RE.match(sha) and not has_commit(repo, sha):
            res.warn("sha_unavailable", f"{key} niedostępny lokalnie (płytki klon lub brak fetch)")
            unavailable = unavailable or key == "verified_code_sha"
    sha = f["verified_code_sha"]
    if SHA_RE.match(sha) and not unavailable:
        diff = git(repo, "diff", "--name-only", "-z", sha) or ""
        touched = set(diff.split("\0")) | set(worktree_changes(repo))
        code = sorted(p for p in touched if p and p not in DOCS_ONLY_FILES)
        if code:
            res.warn("code_changed", f"zmiany od verified_code_sha poza listą dokumentów (oceń ponownie): "
                                     f"{', '.join(code[:5])}{' …' if len(code) > 5 else ''}")
    return True


def validate(text, repo, now):
    res = Result()
    parsed = parse_block(text, res)
    if parsed is None:
        return res
    fields, tests = parsed
    for key in REQUIRED:
        if key not in fields:
            res.error("missing_field", f"brak pola '{key}'")
    if res.errors and any(m.startswith("missing_field") for m in res.errors):
        return res
    check_fields(fields, res, now)
    git_ok = check_git(fields, res, repo)
    check_tests(fields, tests, res, repo, git_ok)
    return res


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--repo", default=".")
    ap.add_argument("--file", default=None, help="domyślnie <repo>/docs/HANDOFF.md")
    ap.add_argument("--now", default=None, help="ISO 8601 z offsetem (do testów)")
    args = ap.parse_args(argv)
    path = args.file or os.path.join(args.repo, "docs", "HANDOFF.md")
    try:
        with open(path, encoding="utf-8") as fh:
            text = fh.read()
    except OSError as exc:
        print(f"ERROR: nie można odczytać {path}: {exc}", file=sys.stderr)
        return 2
    now = datetime.fromisoformat(args.now) if args.now else datetime.now(timezone.utc)
    res = validate(text, args.repo, now)
    for m in res.errors:
        print(f"ERROR {m}")
    for m in res.warnings:
        print(f"WARN  {m}")
    print(f"{'FAIL' if res.errors else 'OK'}: {len(res.errors)} błędów, {len(res.warnings)} ostrzeżeń "
          "(struktura i wykrywalne rozbieżności; to nie dowód prawdziwości opisu)")
    return 1 if res.errors else 0


if __name__ == "__main__":
    sys.exit(main())
