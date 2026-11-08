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
updated_at: 2026-11-08T14:11:57+01:00
status: blocked
branch: ccr-a5500ce0-233anr
pr: https://github.com/Jack4B/ketchup-player/pull/11
base_sha: 338800b5f5116f31b4175b8f9f91615520a81898
verified_code_sha: 9e1a5aa739bde4f3e08326c81f925de83b6f48df
dirty_tree: clean
dirty_files: none
outside_handoff_changes: none
task_state: implemented
plan_point: 1.3 (część do zrobienia w chmurze): D-pad i audyt układu dla Szukaj i Moja lista; analiza dużej listy M3U użytkownika
done: przyczyna czerwonych asercji fokusu potwierdzona na poziomie komponentu (FocusItemTest 4/4 PASS na 6844d2f): w Robolectric okno startuje w trybie dotyku, a w nim semantyka Focused elementów FocusItem jest pusta, choć fokus działa; w trybie klawiszowym jest widoczna; kodu produkcyjnego nie zmieniono; testy modułowe przełączają InputMode.Keyboard (jedno żądanie, z kontrolą); scenariusze pilota E2E mają okno w trybie nie-dotykowym (TvBoxRule + StartsInRemoteMode, Instrumentation.setInTouchMode, kontrola requireRemoteMode, przywracanie trybu po teście); scenariusz syntetycznego katalogu PASS na cdf8952 i 6844d2f; ścieżki Szukaj w module PASS na 6844d2f; poprawki z 06961d2 (jawna kolejność Mojej listy wg czasu dodania 500/300/100 i tożsamość elementu reagującego na OK, test trasy LEWO/BACK na pustej liście, znaczniki etapów i strażnik w E2E pilota, usunięte sondy modułu) niewykonane w CI; zgłoszenie Redactor jako osobny problem (issue #12)
unfinished: bieg na kolejnym SHA; przyczyna OOM w dwóch scenariuszach pilota E2E nieustalona (OutOfMemoryError w ShadowTrace.endSection z Recomposer.runRecomposeAndApplyChanges to tylko hipoteza pętli klatek; zawężać znacznikami KROK i wydrukiem STRAŻNIK); przyczyna jednorazowej porażki rozwiązywania ktlint (f3499c8, 60ae6be) nieustalona: na 6844d2f z --info --stacktrace krok przeszedł; sondy E2E (sondaFokusuPoWyborzeSekcji, wydruki SONDA/SZYNA) i tymczasowe flagi --info --stacktrace w ci.yml do usunięcia po ustaleniu przyczyn; zachowanie na TV-boxie niesprawdzone; kryterium 1.3 nie zamknięte
next_action: odczytać JUnit z biegu na SHA tego wpisu: wydruki KROK/STRAŻNIK z mojaListaISzukajDzialajaSamymPilotem i sondaFokusuPoWyborzeSekcji (ostatni zakończony etap, stos wątku), nowe testy Mojej listy i trasy LEWO/BACK; po ustaleniu przyczyny OOM minimalna poprawka w zatwierdzonym zakresie, potem usunąć sondy i flagi CI i zweryfikować końcowy SHA; osobno, po decyzji: mikro-PR Redactor (test najpierw)
blockers: wynik OOM w E2E pilota (przyczyna nieustalona); pobieranie zależności w CI bywa zawodne: HTTP 504 (16f9279, 50b979c), timeout 10000 ms Gradle (bcf8852, 676cf21), brak ktlint-cli 1.5.0 (60ae6be, f3499c8; bez kodu HTTP) i jednocześnie przeszło (d367f24 podejście 2, cdf8952, 6844d2f); odtwarzanie próbki strumieni zablokowane polityką sieci środowiska (serwery mediów to adresy IP); brak TV-boxa
do_not_touch: main; PR #5 (dev do main); PR #13 i #14; lista IPTV (adres i dane dostępu tylko w prywatnym pliku poza repo, nigdy w commitach, logach i raportach); wersja Gradle, adres dystrybucji, sumy i walidacja (networkTimeout 60000 zatwierdzony); CI poza zatwierdzonymi tymczasowymi flagami --info --stacktrace w kroku Spotless + Detekt (do wycofania); produkcyjna obsługa fokusu bez dowodu
test: python3 -B -m unittest discover -s tools/handoff -p 'test_*.py' | PASS | working_tree | 53 testy, exit 0 (klon w katalogu roboczym; CI tych testów nie uruchamia)
test: CI dev po scaleniu PR #9 | PASS | 9e1a5aa739bde4f3e08326c81f925de83b6f48df | https://github.com/Jack4B/ketchup-player/actions/runs/42423901738 ; gitleaks, build, emulator
test: CI PR #11 (cdf8952, wynik historyczny) | FAIL | cdf89525dec8b5f9d422abf11bc7440e73341c9f | https://github.com/Jack4B/ketchup-player/actions/runs/42475534591 ; 517 testów: 507 PASS, 10 FAIL (asercje fokusu w trybie dotyku); scenariusz syntetyczny PASS
test: CI PR #11 (6844d2f, wynik historyczny) | FAIL | 6844d2f263b211b327d964ac77eef4bef17ee116 | https://github.com/Jack4B/ketchup-player/actions/runs/42479212785 ; 511 testów: 505 PASS, 6 FAIL: 4 w VodBrowseModesScreenTest (3 błędne oczekiwanie pierwszego plakatu Mojej listy, 1 trasa LEWO na pustej liście), 2 OutOfMemoryError w E2E pilota (mojaListaISzukajDzialajaSamymPilotem, sondaFokusuPoWyborzeSekcji); FocusItemTest 4/4 PASS; scenariusz syntetyczny PASS; Gradle i Spotless/ktlint przeszły
test: CI PR #11 na SHA tego wpisu | NOT_RUN | none | uruchamiany po tym wpisie; wynik w raporcie sesji i w PR, nie jest tu przewidywany
test: parser M3U aplikacji na prawdziwej liście (JVM, bez Gradle) | PASS | working_tree | 4578 kanałów w 109 grupach, 19159 filmów, 3475 seriali, 76386 odcinków, 0 pominiętych, parsowanie 774 ms, wyszukiwanie 25-50 ms; to nie jest import do Room ani pomiar na boxie
test: odtwarzanie próbki strumieni z prawdziwej listy | NOT_RUN | none | serwery mediów to adresy IP, polityka sieci środowiska ich nie przepuszcza (403)
test: D-pad, ulubione i import do Room na prawdziwej liście | NOT_RUN | none | wymaga środowiska Android; lista nie może trafić do CI
test: ./gradlew lokalnie | NOT_RUN | none | brak Android SDK w środowisku Cloud
test: TV-box (fokus, BACK, Szukaj, Moja lista, LEWO z akcji pustej Mojej listy) | NOT_RUN | none | brak urządzenia; pozycje dopisane do docs/TEST-CHECKLIST.md
```
