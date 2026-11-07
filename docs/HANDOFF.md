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
updated_at: 2026-11-07T23:28:44+01:00
status: ready_for_review
branch: ccr-a5500ce0-233anr
pr: https://github.com/Jack4B/ketchup-player/pull/9
base_sha: b9ca3a336ed3a73968df7244e9c194c61cf94f89
verified_code_sha: f4416505f90e77059fd335f3c8bd8d5fc80797d7
dirty_tree: clean
dirty_files: none
outside_handoff_changes: none
task_state: awaiting_merge
plan_point: poza punktami planu: przekazywanie stanu sesji (PR #9) i deterministyczne zrzuty Roborazzi (PR #10), makiety v5.1 i workflow (PR #8); plany merytoryczne bez zmian
done: PR #10 scalony do dev (zegar UI, Home w dwóch rozmiarach, "koniec ok." i wiek odświeżenia ze źródła czasu, 22 wzorce, --continue i raporty JUnit w CI); PR #8 scalony do dev po aktualizacji gałęzi (CI w trybie verify); gałąź PR #9 zaktualizowana o dev bez konfliktów, własny diff = 11 plików handoff; dev po #10 i #8: CI zielone, 0 zmian w PNG Roborazzi, bot dopisał tylko docs/qa/emulator
unfinished: PR #9 czeka na scalenie po końcowym CI (wpis zapisany przed scaleniem: na dev PR #9 jest już scalony); żadna kolejna funkcja aplikacji nie została rozpoczęta
next_action: nowa sesja: repo Jack4B/ketchup-player, gałąź dev, komenda /wczytaj-projekt; następny punkt planu wg docs/PLAN-FINALNY.md (krok 1: 1.1, 1.2, 1.3, 1.5), po akceptacji użytkownika
blockers: none
do_not_touch: main; PR #5 (dev do main); kod aplikacji bez zlecenia; lista IPTV z danymi logowania nie jest częścią repo ani zadania
test: python3 -B -m unittest discover -s tools/handoff -p 'test_*.py' | PASS | f48d447284136850d7d8ecd0808dda559af7f3fd | 2026-11-07T23:28:44+01:00, exit 0, 53 testy (lokalnie na czystym drzewie; CI tych testów nie uruchamia)
test: git diff --check origin/dev..HEAD | PASS | f48d447284136850d7d8ecd0808dda559af7f3fd | exit 0, bez uwag
test: CI PR #10 verify (junit-roborazzi-verify), dwa wykonania | PASS | f4416505f90e77059fd335f3c8bd8d5fc80797d7 | https://github.com/Jack4B/ketchup-player/actions/runs/42398919081 ; 298 testów 0 FAIL, wykonania 19:39 i 20:21 Europe/Warsaw, 24 dotknięte testy zrzutowe PASS
test: CI PR #8 verify po aktualizacji o dev | PASS | 9180a20e1edb6786c7fac7778ec42287841b7462 | https://github.com/Jack4B/ketchup-player/actions/runs/42414573838 ; 298 testów 0 FAIL
test: CI dev (tryb record, nie verify) po scaleniu PR #10 | PASS | 497bc93235fc2a4d11152f9bf06d06876d34d09d | https://github.com/Jack4B/ketchup-player/actions/runs/42412060376 ; 0 zmian PNG Roborazzi
test: CI dev (tryb record, nie verify) po scaleniu PR #8 | PASS | a502d4cce1f25f9a8b84bf70cc2ebeaac8c9510d | https://github.com/Jack4B/ketchup-player/actions/runs/42418275718 ; 0 zmian PNG Roborazzi
test: ./gradlew spotlessCheck i testy aplikacji lokalnie | NOT_RUN | none | brak Android SDK w środowisku Cloud; wykonywane w CI
test: CI PR #9 po aktualizacji o dev (pełne, z emulatorem) | NOT_RUN | none | uruchamia się po tym zapisie; wynik w raporcie sesji i w PR (dokument nie zawiera własnego SHA)
```
