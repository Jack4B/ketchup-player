"""Testy walidatora docs/HANDOFF.md (stdlib, offline).

Uruchom: python3 -B -m unittest discover -s tools/handoff -p 'test_*.py'
Obecne CI (.github/workflows/ci.yml) tych testów NIE uruchamia.
"""

import hashlib
import subprocess
import sys
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import validate_handoff as vh  # noqa: E402

SCRIPT = HERE / "validate_handoff.py"
UTC = timezone.utc
NOW = datetime(2026, 11, 7, 15, 0, 0, tzinfo=timezone(timedelta(hours=1)))
GOOD_DATE = "2026-11-07T14:00:00+01:00"
FAKE_SHA = "a" * 40


def git(repo, *args):
    cmd = ["git", "-C", str(repo), "-c", "user.name=t", "-c", "user.email=t@example.invalid",
           "-c", "commit.gpgsign=false", *args]
    return subprocess.run(cmd, check=True, capture_output=True, text=True).stdout.strip()


def commit_file(repo, rel, content, msg):
    path = Path(repo) / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    git(repo, "add", rel)
    git(repo, "commit", "-m", msg)
    return git(repo, "rev-parse", "HEAD")


def make_repo(root):
    repo = Path(root) / "repo"
    repo.mkdir()
    git(repo, "init", "-q", "-b", "main")
    c1 = commit_file(repo, "app/Main.kt", "fun main() {}\n", "code")
    return repo, c1


def handoff(c1, remove=(), **over):
    f = {
        "updated_at": GOOD_DATE, "status": "active", "branch": "main", "pr": "none",
        "base_sha": c1, "verified_code_sha": c1, "dirty_tree": "clean", "dirty_files": "none",
        "outside_handoff_changes": "none", "task_state": "implemented", "plan_point": "PLAN-1.0 1.3",
        "done": "walidator", "unfinished": "none", "next_action": "przeglad diffu", "blockers": "none",
        "do_not_touch": "kod aplikacji",
    }
    tests = over.pop("tests", [f"./gradlew testDebugUnitTest | PASS | {c1} | https://github.com/Jack4B/ketchup-player/actions/runs/1"])
    f.update(over)
    lines = [f"{k}: {v}" for k, v in f.items() if k not in remove]
    lines += [f"test: {t}" for t in tests]
    return "# Handoff\n\n```handoff\n" + "\n".join(lines) + "\n```\n"


def snapshot(root):
    out = {}
    for p in sorted(Path(root).rglob("*")):
        if p.is_file():
            out[str(p)] = hashlib.sha256(p.read_bytes()).hexdigest()
    return out


class Base(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.repo, self.c1 = make_repo(self.tmp.name)

    def check(self, text, repo=None, now=NOW):
        return vh.validate(text, repo if repo is not None else self.repo, now)

    def has(self, items, code):
        return any(m.startswith(code + ":") for m in items)

    def assertErr(self, res, code):
        self.assertTrue(self.has(res.errors, code), f"brak ERROR {code}: {res.errors} / {res.warnings}")

    def assertWarn(self, res, code):
        self.assertTrue(self.has(res.warnings, code), f"brak WARN {code}: {res.errors} / {res.warnings}")

    def assertNoWarn(self, res, code):
        self.assertFalse(self.has(res.warnings, code), f"nieoczekiwany WARN {code}: {res.warnings}")


class TestValid(Base):
    def test_poprawny_handoff_bez_bledow_i_ostrzezen(self):
        res = self.check(handoff(self.c1))
        self.assertEqual(res.errors, [])
        self.assertEqual(res.warnings, [])

    def test_brak_bloku_handoff(self):
        self.assertErr(self.check("# nic"), "no_block")

    def test_brak_pola(self):
        self.assertErr(self.check(handoff(self.c1, remove=("status",))), "missing_field")

    def test_zdublowane_pole(self):
        text = handoff(self.c1).replace("```handoff\n", "```handoff\nstatus: done\n")
        self.assertErr(self.check(text), "duplicate_field")

    def test_nieznane_pole_to_ostrzezenie(self):
        text = handoff(self.c1).replace("```handoff\n", "```handoff\nfoo: bar\n")
        res = self.check(text)
        self.assertEqual(res.errors, [])
        self.assertWarn(res, "unknown_field")

    def test_zly_status(self):
        self.assertErr(self.check(handoff(self.c1, status="finished")), "bad_status")

    def test_wszystkie_statusy_dozwolone(self):
        for st in ("plan", "active", "blocked", "ready_for_review", "done"):
            res = self.check(handoff(self.c1, status=st))
            self.assertFalse(self.has(res.errors, "bad_status"), st)

    def test_zly_task_state(self):
        self.assertErr(self.check(handoff(self.c1, task_state="finished")), "bad_task_state")

    def test_zly_pr(self):
        self.assertErr(self.check(handoff(self.c1, pr="PR #8")), "bad_pr")
        res = self.check(handoff(self.c1, pr="https://github.com/Jack4B/ketchup-player/pull/8"))
        self.assertFalse(self.has(res.errors, "bad_pr"))


class TestDates(Base):
    def test_zly_format_daty(self):
        for bad in ("7.11.2026", "2026-11-07", "2026-11-07T14:00:00", "2026-11-07T14:00:00Z",
                    "2026-13-40T14:00:00+01:00"):
            self.assertErr(self.check(handoff(self.c1, updated_at=bad)), "bad_date")

    def test_offset_zima_i_lato_wzgledem_warszawy(self):
        self.assertFalse(self.has(self.check(handoff(self.c1, updated_at="2026-07-01T12:00:00+02:00")).errors, "bad_date"))
        self.assertErr(self.check(handoff(self.c1, updated_at="2026-07-01T12:00:00+01:00")), "bad_date")
        self.assertErr(self.check(handoff(self.c1, updated_at="2026-11-07T14:00:00+02:00")), "bad_date")
        self.assertErr(self.check(handoff(self.c1, updated_at="2026-11-07T14:00:00+00:00")), "bad_date")

    def test_granice_czasu_letniego(self):
        h = timedelta(hours=1)
        self.assertEqual(vh.warsaw_offset(datetime(2026, 3, 29, 0, 59, tzinfo=UTC)), h)
        self.assertEqual(vh.warsaw_offset(datetime(2026, 3, 29, 1, 0, tzinfo=UTC)), 2 * h)
        self.assertEqual(vh.warsaw_offset(datetime(2026, 10, 25, 0, 59, tzinfo=UTC)), 2 * h)
        self.assertEqual(vh.warsaw_offset(datetime(2026, 10, 25, 1, 0, tzinfo=UTC)), h)

    def test_stary_handoff_to_ostrzezenie(self):
        res = self.check(handoff(self.c1, updated_at="2026-11-04T10:00:00+01:00"))
        self.assertEqual(res.errors, [])
        self.assertWarn(res, "stale")

    def test_handoff_z_przyszlosci_to_ostrzezenie(self):
        self.assertWarn(self.check(handoff(self.c1, updated_at="2026-11-08T10:00:00+01:00")), "future_date")


class TestShas(Base):
    def test_zly_format_sha_to_blad(self):
        self.assertErr(self.check(handoff(self.c1, base_sha="abc123")), "bad_sha")
        self.assertErr(self.check(handoff(self.c1, base_sha="G" * 40)), "bad_sha")
        self.assertErr(self.check(handoff(self.c1, verified_code_sha="xyz")), "bad_sha")
        self.assertErr(self.check(handoff(self.c1, base_sha="none")), "bad_sha")

    def test_brak_verified_sha_to_ostrzezenie(self):
        res = self.check(handoff(self.c1, verified_code_sha="none"))
        self.assertEqual(res.errors, [])
        self.assertWarn(res, "no_verified_sha")

    def test_poprawny_sha_niedostepny_lokalnie_to_ostrzezenie(self):
        res = self.check(handoff(self.c1, base_sha=FAKE_SHA))
        self.assertEqual(res.errors, [])
        self.assertWarn(res, "sha_unavailable")

    def test_plytki_klon_nie_ma_starego_sha_to_ostrzezenie(self):
        commit_file(self.repo, "docs/NOTE.md", "x\n", "docs")
        clone = Path(self.tmp.name) / "shallow"
        subprocess.run(["git", "clone", "-q", "--depth", "1", f"file://{self.repo}", str(clone)],
                       check=True, capture_output=True)
        res = self.check(handoff(self.c1), repo=clone)
        self.assertEqual(res.errors, [])
        self.assertWarn(res, "sha_unavailable")


class TestGitState(Base):
    def test_inna_galaz_to_ostrzezenie(self):
        self.assertWarn(self.check(handoff(self.c1, branch="feat/inna")), "branch_differs")

    def test_dirty_tree_zmiana_pliku_sledzonego(self):
        (self.repo / "app/Main.kt").write_text("fun main() { println() }\n", encoding="utf-8")
        res = self.check(handoff(self.c1))
        self.assertEqual(res.errors, [])
        self.assertWarn(res, "dirty_tree")

    def test_dirty_tree_nowy_plik_nieskladowany(self):
        (self.repo / "app/New.kt").write_text("x\n", encoding="utf-8")
        self.assertWarn(self.check(handoff(self.c1)), "dirty_tree")

    def test_dluga_lista_zmian_jest_oznaczona_wielokropkiem(self):
        for i in range(7):
            (self.repo / f"app/F{i}.kt").write_text("x\n", encoding="utf-8")
        msg = next(m for m in self.check(handoff(self.c1)).warnings if m.startswith("dirty_tree:"))
        self.assertIn("…", msg)

    def test_pliki_handoff_i_historii_nie_brudza(self):
        (self.repo / "docs").mkdir(exist_ok=True)
        (self.repo / "docs/HANDOFF.md").write_text("x\n", encoding="utf-8")
        (self.repo / "docs/HISTORIA-SESJI.md").write_text("x\n", encoding="utf-8")
        self.assertNoWarn(self.check(handoff(self.c1)), "dirty_tree")

    def test_commit_tylko_dokumentow_nie_uniewaznia_testow(self):
        commit_file(self.repo, "docs/HANDOFF.md", "x\n", "docs: handoff")
        commit_file(self.repo, "docs/HISTORIA-SESJI.md", "x\n", "docs: historia")
        res = self.check(handoff(self.c1))
        self.assertEqual(res.errors, [])
        self.assertNoWarn(res, "code_changed")

    def test_zmieniony_kod_od_verified_sha(self):
        commit_file(self.repo, "app/Main.kt", "fun main() { println() }\n", "feat")
        res = self.check(handoff(self.c1))
        self.assertEqual(res.errors, [])
        self.assertWarn(res, "code_changed")

    def test_skrypty_konfiguracja_i_claude_nie_sa_docs_only(self):
        for rel in ("tools/x.py", ".claude/settings.json", ".claude/commands/a.md", ".github/workflows/ci.yml",
                    "build.gradle.kts", "docs/mockups/v5.1/generate.py"):
            tmp = tempfile.TemporaryDirectory()
            self.addCleanup(tmp.cleanup)
            repo, c1 = make_repo(tmp.name)
            commit_file(repo, rel, "x\n", "change")
            self.assertWarn(vh.validate(handoff(c1), repo, NOW), "code_changed")

    def test_zmiana_niezatwierdzona_tez_liczy_sie_jako_kod(self):
        (self.repo / "app/Main.kt").write_text("fun main() { println() }\n", encoding="utf-8")
        self.assertWarn(self.check(handoff(self.c1)), "code_changed")

    def test_brak_repo_git_to_ostrzezenie(self):
        plain = Path(self.tmp.name) / "plain"
        plain.mkdir()
        res = self.check(handoff(self.c1), repo=plain)
        self.assertEqual(res.errors, [])
        self.assertWarn(res, "no_git")


class TestDirtyFields(Base):
    def test_clean_ale_lista_plikow_to_blad(self):
        self.assertErr(self.check(handoff(self.c1, dirty_files="app/Main.kt")), "dirty_inconsistent")
        self.assertErr(self.check(handoff(self.c1, outside_handoff_changes="app/Main.kt")), "dirty_inconsistent")

    def test_dirty_bez_listy_to_blad(self):
        self.assertErr(self.check(handoff(self.c1, dirty_tree="dirty")), "dirty_inconsistent")

    def test_dirty_z_lista_jest_spojny(self):
        res = self.check(handoff(self.c1, dirty_tree="dirty", dirty_files="app/Main.kt; docs/HANDOFF.md",
                                 outside_handoff_changes="app/Main.kt"))
        self.assertFalse(self.has(res.errors, "dirty_inconsistent"))

    def test_zla_wartosc_dirty_tree(self):
        self.assertErr(self.check(handoff(self.c1, dirty_tree="unknown")), "dirty_inconsistent")


class TestTestRows(Base):
    def row(self, line, **over):
        return self.check(handoff(self.c1, tests=[line], **over))

    def test_pass_na_dokladnym_sha_z_linkiem(self):
        res = self.row(f"unit | PASS | {self.c1} | https://github.com/Jack4B/ketchup-player/actions/runs/1")
        self.assertEqual(res.errors, [])

    def test_pass_working_tree_z_lokalnym_dowodem_bez_linku(self):
        res = self.row("python3 -B -m unittest | PASS | working_tree | 2026-11-07T14:20:00+01:00 exit 0")
        self.assertEqual(res.errors, [])

    def test_pass_working_tree_nie_jest_dowodem_dla_commita(self):
        res = self.row("unit | PASS | working_tree | exit 0", status="ready_for_review")
        self.assertEqual(res.errors, [])
        self.assertWarn(res, "not_proof")

    def test_pass_na_sha_przy_ready_for_review_bez_ostrzezenia_not_proof(self):
        res = self.row(f"unit | PASS | {self.c1} | exit 0", status="ready_for_review")
        self.assertNoWarn(res, "not_proof")

    def test_pass_bez_sha_to_blad(self):
        self.assertErr(self.row("unit | PASS | none | exit 0"), "bad_test_row")

    def test_pass_ze_zlym_sha_to_blad_bad_sha(self):
        self.assertErr(self.row("unit | PASS | abc123 | exit 0"), "bad_sha")

    def test_pass_bez_dowodu_to_blad(self):
        self.assertErr(self.row(f"unit | PASS | {self.c1} |"), "bad_test_row")

    def test_not_run_wymaga_powodu(self):
        self.assertErr(self.row("gradle | NOT_RUN | none |"), "bad_test_row")
        res = self.row("gradle | NOT_RUN | none | brak Android SDK")
        self.assertEqual(res.errors, [])

    def test_zly_wynik_i_zla_liczba_pol(self):
        self.assertErr(self.row(f"unit | OK | {self.c1} | x"), "bad_test_row")
        self.assertErr(self.row("unit | PASS | working_tree"), "bad_test_row")

    def test_fail_przy_statusie_done_to_blad(self):
        self.assertErr(self.row(f"unit | FAIL | {self.c1} | exit 1", status="done"), "test_inconsistent")

    def test_not_run_przy_done_to_ostrzezenie(self):
        res = self.row("gradle | NOT_RUN | none | brak SDK", status="done")
        self.assertEqual(res.errors, [])
        self.assertWarn(res, "test_inconsistent")

    def test_done_z_awaiting_device_to_ostrzezenie(self):
        self.assertWarn(self.check(handoff(self.c1, status="done", task_state="awaiting_device")), "unmet_criterion")

    def test_brak_wierszy_testow_to_ostrzezenie(self):
        self.assertWarn(self.check(handoff(self.c1, tests=[])), "no_tests")

    def test_sha_testu_niedostepny_lokalnie_to_ostrzezenie(self):
        self.assertWarn(self.row(f"unit | PASS | {FAKE_SHA} | exit 0"), "sha_unavailable")


class TestSecrets(Base):
    def test_url_z_haslem_i_password_to_ostrzezenie(self):
        res = self.check(handoff(self.c1, next_action="uzyj http://user:pass123@host/list.m3u"))
        self.assertWarn(res, "secret")
        self.assertWarn(self.check(handoff(self.c1, blockers="password=hunter2xyz")), "secret")

    def test_zwykly_tekst_bez_ostrzezenia(self):
        self.assertNoWarn(self.check(handoff(self.c1, blockers="brak tokenu w CI")), "secret")


class TestReadOnly(Base):
    def test_walidator_nie_zmienia_plikow_ani_git_status(self):
        (self.repo / "app/Main.kt").write_text("fun main() { println() }\n", encoding="utf-8")
        (self.repo / "app/New.kt").write_text("x\n", encoding="utf-8")
        (self.repo / "docs").mkdir(exist_ok=True)
        (self.repo / "docs/HANDOFF.md").write_text(handoff(self.c1), encoding="utf-8")
        before, status_before = snapshot(self.repo), git(self.repo, "status", "--porcelain")
        self.check(handoff(self.c1))
        subprocess.run([sys.executable, "-B", str(SCRIPT), "--repo", str(self.repo)], capture_output=True)
        self.assertEqual(snapshot(self.repo), before)
        self.assertEqual(git(self.repo, "status", "--porcelain"), status_before)


class TestCli(Base):
    def run_cli(self, text, *extra):
        (self.repo / "docs").mkdir(exist_ok=True)
        path = self.repo / "docs/HANDOFF.md"
        path.write_text(text, encoding="utf-8")
        return subprocess.run([sys.executable, "-B", str(SCRIPT), "--repo", str(self.repo),
                               "--now", NOW.isoformat(), *extra], capture_output=True, text=True)

    def test_exit_0_dla_poprawnego(self):
        p = self.run_cli(handoff(self.c1))
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)

    def test_exit_1_dla_bledow(self):
        p = self.run_cli(handoff(self.c1, status="zly"))
        self.assertEqual(p.returncode, 1)
        self.assertIn("ERROR", p.stdout)

    def test_exit_0_z_ostrzezeniami(self):
        p = self.run_cli(handoff(self.c1, branch="feat/inna"))
        self.assertEqual(p.returncode, 0)
        self.assertIn("WARN", p.stdout)

    def test_exit_2_gdy_brak_pliku(self):
        p = subprocess.run([sys.executable, "-B", str(SCRIPT), "--repo", str(self.repo)], capture_output=True, text=True)
        self.assertEqual(p.returncode, 2)


if __name__ == "__main__":
    unittest.main()
