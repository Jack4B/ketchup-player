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
updated_at: 2026-11-08T14:58:35+01:00
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
done: przyczyna czerwonych asercji fokusu potwierdzona (FocusItemTest 4/4 PASS): w Robolectric okno startuje w trybie dotyku, w którym semantyka Focused elementów FocusItem jest pusta, choć fokus działa; kodu produkcyjnego nie zmieniono; VodBrowseModesScreenTest 12/12 PASS na 74de962 i 5cc19cc (kolejność Mojej listy wg jawnych czasów dodania, tożsamość elementu reagującego na OK, trasa LEWO do pola i BACK na szynę na pustej liście); scenariusz pilota E2E przerobiony: dane w trybie dotyku (helpery UI), potem okno w trybie klawiszowym przez View.requestFocusFromTouch() z kontrolą okna i Compose (kanarek ComposeView, usuwany po odczycie), przywrócenie trybu dotyku w finally; na 5cc19cc przełączenie potwierdzone (okno dotyk=false, Compose=Keyboard), ścieżki Moja lista, Filmy i Szukaj pilotem osiągają szynę, kategorię, plakat i szczegóły (sonda, 3 sekcje); scenariusz syntetycznego katalogu PASS; sondy E2E i wydruki usunięte, zostaje strażnik 60 s i znaczniki etapów bez wydruków; zgłoszenie Redactor jako osobny problem (issue #12)
unfinished: (1) NIEROZSTRZYGNIĘTE, osobne: zimny start w oknie nie-dotykowym zawiesza helper click w przygotowaniu danych (bieg 42484276208: stos click -> performScrollTo/performClick -> ComposeIdlingResource.isIdleNow w pętli advanceTimeByFrame, ostatni etap "dodanie listy przez UI", kategoria Listy i dostawcy w Ustawieniach); nie ustalono, czy winna jest akcja scroll, klik czy oczekiwanie na bezczynność, ani czemu aplikacja stale zgłasza pracę klatkową; hipoteza o wstrzykiwaniu dotyku niepotwierdzona, implementacja performClick niesprawdzona (context7 niedostępny), nowy scenariusz tego nie rozstrzyga, tylko omija; (2) BACK ze szczegółów filmu: obecnie pierwszy BACK przenosi fokus na szynę, szczegóły zostają, drugi BACK prowadzi na Home (Shell.BackHandler; DetailsRoute bez onBack); zasada "BACK cofa o jeden poziom" i KONSPEKT l.399 sugerują powrót do listy: decyzja właściciela i ocena na boxie; (3) przyczyna jednorazowej porażki rozwiązywania ktlint (60ae6be, f3499c8) nieustalona, flagi --info --stacktrace w ci.yml do wycofania; (4) zachowanie na TV-boxie niesprawdzone; kryterium 1.3 nie zamknięte
next_action: odczytać JUnit z biegu na końcowym SHA (ScenariosTest: mojaListaISzukajDzialajaSamymPilotem, syntetyczny katalog, FocusItemTest, VodBrowseModesScreenTest); jeśli zielone, wycofać flagi --info --stacktrace osobnym commitem i zweryfikować jego SHA, zaktualizować opis PR; decyzje właściciela: BACK ze szczegółów, ocena LEWO z akcji pustej Mojej listy na boxie; osobno: nierozstrzygnięty zimny start w trybie klawiszowym, mikro-PR Redactor (test najpierw)
blockers: nierozstrzygnięty zimny start w oknie nie-dotykowym (osobne ustalenie); pobieranie zależności w CI bywa zawodne (HTTP 504, timeout Gradle, brak ktlint-cli 1.5.0 bez kodu HTTP); odtwarzanie próbki strumieni zablokowane polityką sieci środowiska (serwery mediów to adresy IP); brak TV-boxa
do_not_touch: main; PR #5 (dev do main); PR #13 i #14; lista IPTV (adres i dane dostępu tylko w prywatnym pliku poza repo, nigdy w commitach, logach i raportach); wersja Gradle, adres dystrybucji, sumy i walidacja (networkTimeout 60000 zatwierdzony); CI poza zatwierdzonymi tymczasowymi flagami --info --stacktrace w kroku Spotless + Detekt (do wycofania); produkcyjna obsługa fokusu i BACK bez decyzji właściciela
test: python3 -B -m unittest discover -s tools/handoff -p 'test_*.py' | PASS | working_tree | 53 testy, exit 0 (klon w katalogu roboczym; CI tych testów nie uruchamia)
test: CI dev po scaleniu PR #9 | PASS | 9e1a5aa739bde4f3e08326c81f925de83b6f48df | https://github.com/Jack4B/ketchup-player/actions/runs/42423901738 ; gitleaks, build, emulator
test: CI PR #11 (cdf8952, wynik historyczny) | FAIL | cdf89525dec8b5f9d422abf11bc7440e73341c9f | https://github.com/Jack4B/ketchup-player/actions/runs/42475534591 ; 507 PASS, 10 FAIL (asercje fokusu w trybie dotyku)
test: CI PR #11 (74de962, wynik historyczny) | FAIL | 74de9620f99056884fe528b88fc47f456bcd9805 | https://github.com/Jack4B/ketchup-player/actions/runs/42484276208 ; 510 testów: 508 PASS, 2 FAIL (scenariusze pilota E2E przerwane strażnikiem po 60 s w przygotowaniu danych w oknie nie-dotykowym); VodBrowseModesScreenTest 12/12, FocusItemTest 4/4, scenariusz syntetyczny PASS
test: CI PR #11 (f1c8569, wynik historyczny) | FAIL | f1c8569a17a4b37fff205d4d5da1ef1189748eb5 | https://github.com/Jack4B/ketchup-player/actions/runs/42485706688 ; setup w trybie dotyku bez zawieszeń, przełączenie okna i Compose potwierdzone; 2 FAIL: kanarek jako drugi korzeń semantyki psuł onRoot (poprawione)
test: CI PR #11 (5cc19cc, wynik historyczny) | FAIL | 5cc19cc25c6efce307bff19dd17470e0246a9d14 | https://github.com/Jack4B/ketchup-player/actions/runs/42486480771 ; 510 testów: 509 PASS, 1 FAIL: mojaListaISzukajDzialajaSamymPilotem czekał na zamknięcie szczegółów po BACK (oczekiwanie powrotu do listy było założeniem; obecne zachowanie: BACK -> szyna, drugi BACK -> Home); sondaFokusuPoWyborzeSekcji PASS (Moja lista, Filmy, Szukaj pilotem: szyna -> kategoria -> plakat -> OK otwiera szczegóły); synt. katalog PASS
test: CI PR #11 na SHA tego wpisu | NOT_RUN | none | uruchamiany po tym wpisie; wynik w raporcie sesji i w PR, nie jest tu przewidywany
test: parser M3U aplikacji na prawdziwej liście (JVM, bez Gradle) | PASS | working_tree | 4578 kanałów w 109 grupach, 19159 filmów, 3475 seriali, 76386 odcinków, 0 pominiętych, parsowanie 774 ms, wyszukiwanie 25-50 ms; to nie jest import do Room ani pomiar na boxie
test: odtwarzanie próbki strumieni z prawdziwej listy | NOT_RUN | none | serwery mediów to adresy IP, polityka sieci środowiska ich nie przepuszcza (403)
test: D-pad, ulubione i import do Room na prawdziwej liście | NOT_RUN | none | wymaga środowiska Android; lista nie może trafić do CI
test: ./gradlew lokalnie | NOT_RUN | none | brak Android SDK w środowisku Cloud
test: detekt-cli 1.23.8 i ktlint-cli 1.5.0 lokalnie (konfiguracja projektu) | PASS | working_tree | moduły app, feature:vod, core:designsystem bez uwag; to nie zastępuje kroku Spotless + Detekt w CI
test: TV-box (fokus, BACK, Szukaj, Moja lista, LEWO z akcji pustej Mojej listy, BACK ze szczegółów) | NOT_RUN | none | brak urządzenia; pozycje w docs/TEST-CHECKLIST.md
```
