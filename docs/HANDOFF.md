# HANDOFF: migawka ostatniej sesji

Krótka migawka stanu, **nie backlog**. Plany i checkboxy zostają w `docs/PLAN-1.0.md` i `docs/PLAN-FINALNY.md`; historia
w `docs/HISTORIA-SESJI.md`. Zapis: `/domknij-projekt`, odczyt: `/wczytaj-projekt`.
Walidator (`python3 -B tools/handoff/validate_handoff.py`) sprawdza strukturę i wykrywalne rozbieżności; nie dowodzi, że opis jest prawdziwy ani aktualny.

## Format

Jeden blok ` ```handoff ` z liniami `klucz: wartość` (bez YAML). Wiersze `test:` mogą się powtarzać.

```text
updated_at: ISO 8601 z sekundami i offsetem Europe/Warsaw dla tej daty (np. 2026-11-07T14:34:18+01:00)
status: plan | active | blocked | ready_for_review | done
branch / pr: nazwa gałęzi; URL PR albo none
base_sha / verified_code_sha: pełne SHA (40 hex); verified_code_sha może być none
dirty_tree: clean | dirty        (stan na moment pomiaru, przed zapisem handoffu)
dirty_files: none | pełna lista zmian drzewa
outside_handoff_changes: none | zmiany poza docs/HANDOFF.md i docs/HISTORIA-SESJI.md
task_state: none | implemented | tested | awaiting_device | awaiting_merge
plan_point, done, unfinished, next_action, blockers, do_not_touch
test: komenda lub job | PASS/FAIL/NOT_RUN | pełny SHA, working_tree lub none | dowód lub powód
```

Zasady:

- `base_sha` i `verified_code_sha` opisują punkt bazowy i zbadany kod. To nie jest „SHA commita zawierającego ten dokument”.
- `dirty_tree: clean` tylko gdy `git status` jest pusty. Zmiany pokaż w `dirty_files`, a osobno w `outside_handoff_changes` te poza plikami handoff/historii.
- `task_state`: `implemented` = jest w gałęzi; `tested` = wymagane testy PASS na wskazanym SHA; `awaiting_device` = czeka na test na urządzeniu; `awaiting_merge` = czeka na merge. Nie oznaczaj kryterium jako spełnionego (ani nie odhaczaj w planie), jeśli nie jest.
- PASS dotyczy tylko wskazanego SHA. `working_tree` to wynik lokalny na drzewie z (ewentualnymi) zmianami: dowodem jest komenda, czas i exit code, link CI niepotrzebny. Nie jest to dowód dla commita; po commicie powtórz testy na jego SHA.
- FAIL = błąd kodu lub testu. NOT_RUN wymaga powodu i dotyczy tylko kontroli, których naprawdę nie dało się wykonać.
- Zero sekretów (adresy list z loginem i hasłem, klucze, tokeny).
- Zmiana kodu od `verified_code_sha` daje ostrzeżenie `code_changed`. Poza ostrzeżeniem są tylko pliki z jawnej listy `DOCS_ONLY_FILES` w walidatorze; skrypty, konfiguracja, workflow CI i całe `.claude/` wymagają ponownej oceny.

## Stan

```handoff
updated_at: 2026-11-07T14:41:30+01:00
status: ready_for_review
branch: ccr-a5500ce0-233anr
pr: none
base_sha: 677cddebc997ab3ee93afd3bfee23b90420d3124
verified_code_sha: 677cddebc997ab3ee93afd3bfee23b90420d3124
dirty_tree: dirty
dirty_files: .claude/commands/next-step.md; .claude/commands/verify.md; .claude/skills/ketchup-pamiec/SKILL.md; .claude/skills/ketchup-workflow/SKILL.md; CLAUDE.md; .claude/commands/domknij-projekt.md; .claude/commands/wczytaj-projekt.md; tools/handoff/test_validate_handoff.py; tools/handoff/validate_handoff.py; docs/HANDOFF.md; docs/HISTORIA-SESJI.md
outside_handoff_changes: .claude/commands/next-step.md; .claude/commands/verify.md; .claude/skills/ketchup-pamiec/SKILL.md; .claude/skills/ketchup-workflow/SKILL.md; CLAUDE.md; .claude/commands/domknij-projekt.md; .claude/commands/wczytaj-projekt.md; tools/handoff/test_validate_handoff.py; tools/handoff/validate_handoff.py
task_state: implemented
plan_point: poza punktami planu: nadbudowa przekazywania stanu sesji (HANDOFF, historia, walidator, komendy); plany merytoryczne bez zmian
done: HANDOFF i historia; walidator tools/handoff z 53 testami (RED potem GREEN); komendy /wczytaj-projekt i /domknij-projekt; poprawki /next-step, /verify, ketchup-workflow, ketchup-pamiec, CLAUDE.md; gałąź sesji fast-forward do PR #8 (677cdde)
unfinished: na moment pomiaru nic nie zacommitowane (zgoda na commit, push i PR do dev udzielona); CI na SHA tej paczki do sprawdzenia po push; testy walidatora nie są uruchamiane przez obecne CI
next_action: po commicie powtórzyć testy walidatora i git diff --check na jego SHA, push, PR do dev (zależy od PR #8), sprawdzić CI na końcowym SHA; potem użytkownik decyduje o kolejności merge (bez merge przed jego zgodą)
blockers: decyzja użytkownika o kolejności merge (najpierw PR #8); nowy PR do dev zawiera 4 commity #8 do czasu jego merge
do_not_touch: kod aplikacji; .github/workflows; .claude/settings.json; wzorce Roborazzi; PR #8 (bez merge, rebase i force-push)
test: python3 -B -m unittest discover -s tools/handoff -p 'test_*.py' | PASS | working_tree | 2026-11-07T14:40:46+01:00, exit 0, 53 testy; RED przed implementacją: ModuleNotFoundError; CI tych testów nie uruchamia
test: git diff --check (śledzone) i kontrola białych znaków plików nieśledzonych | PASS | working_tree | 2026-11-07T14:40:46+01:00, exit 0, bez uwag
test: ./gradlew spotlessCheck | NOT_RUN | none | próba exit 1: nie rozwiązano wtyczki com.android.application:8.10.1 (brak dostępu do repozytoriów i Android SDK w Cloud)
test: CI build + emulator + gitleaks na head PR #8 | PASS | 677cddebc997ab3ee93afd3bfee23b90420d3124 | https://github.com/Jack4B/ketchup-player/actions/runs/42352920981
test: CI na commicie z tą paczką | NOT_RUN | none | w chwili zapisu commit jeszcze nie istnieje; wynik na dokładnym SHA jest w raporcie sesji i w PR (dokument nie zawiera własnego SHA)
```
