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
updated_at: 2026-11-08T12:52:00+01:00
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
done: ustalona przyczyna czerwonych asercji fokusu w Robolectric (nie błąd aplikacji): performKeyInput zostawia tryb dotyku, a w nim semantyka Focused elementów FocusItem jest pusta (scalona i niescalona), choć fokus jest prawdziwy (OK klika element z fokusem, DÓŁ przenosi go na następny); w trybie klawiszowym semantyka pokazuje fokus (dane z JUnit biegu 42475534591 na cdf8952); FocusItemTest zastąpił sondę FocusItemReproTest; testy modułowe przełączają InputMode.Keyboard w SideEffect z kontrolą wyniku; scenariusze pilota E2E mają okno w trybie nie-dotykowym przez TvBoxRule i @StartsInRemoteMode (Instrumentation.setInTouchMode, bez refleksji) z kontrolą requireRemoteMode; zgłoszenie Redactor jako osobny problem (issue #12); syntetyczny katalog SyntheticCatalog i scenariusz syntetycznyKatalogImportujeSzukaIPamietaUlubione (wykonany na cdf8952: PASS); analiza listy M3U parserem aplikacji na JVM
unfinished: poprawki testów (bcf8852 i następny commit) niezweryfikowane w CI; nieznane, czy po przełączeniu trybu przechodzą wszystkie ścieżki D-pad, BACK i powrót na szynę (moduł i E2E) oraz czy Instrumentation.setInTouchMode(false) przełącza okno w pełnej aplikacji; sondy (println, sondaTrybuWejscia, sondaPoczatkowegoFokusuEkranu, sondaFokusuPoWyborzeSekcji) do usunięcia po potwierdzeniu diagnozy; zachowanie na TV-boxie niesprawdzone; kryterium 1.3 nie zamknięte
next_action: odczytać JUnit z biegu na końcowym SHA (FocusItemTest, VodBrowseModesScreenTest, ScenariosTest, scenariusz syntetyczny); jeśli ścieżki przechodzą, usunąć sondy osobnym commitem i zweryfikować jego SHA; jeśli nie, poprawić testy w zatwierdzonym zakresie (asercje zachowania: kto dostał OK, kierunek, BACK, powrót na szynę), bez zmian produkcyjnych bez dowodu; osobno, po decyzji: mikro-PR Redactor dla ścieżek host/użytkownik/hasło/id (test najpierw)
blockers: pobieranie Gradle 8.14.3 w CI jest zawodne: HTTP 504 (16f9279, pięć prób; 50b979c), timeout 10000 ms z services.gradle.org na bcf8852 (bieg 42476586646, krok build-logic, żaden test się nie wykonał) i brak ktlint-cli 1.5.0 na 60ae6be (kod odpowiedzi nieznany); przyczyna nieustalona (konfiguracja wrappera, CI i obraz runnera bez zmian; na d367f24 podejście 2 i na cdf8952 pobranie przeszło); odtwarzanie próbki strumieni zablokowane polityką sieci środowiska (serwery mediów to adresy IP); brak TV-boxa
do_not_touch: main; PR #5 (dev do main); PR #13 i #14; lista IPTV (adres i dane dostępu tylko w prywatnym pliku poza repo, nigdy w commitach, logach i raportach); CI i wersja Gradle; produkcyjna obsługa fokusu bez dowodu
test: python3 -B -m unittest discover -s tools/handoff -p 'test_*.py' | PASS | working_tree | 53 testy, exit 0 (klon w katalogu roboczym; CI tych testów nie uruchamia)
test: CI dev po scaleniu PR #9 | PASS | 9e1a5aa739bde4f3e08326c81f925de83b6f48df | https://github.com/Jack4B/ketchup-player/actions/runs/42423901738 ; gitleaks, build, emulator
test: CI PR #11 (cdf8952, wynik historyczny) | FAIL | cdf89525dec8b5f9d422abf11bc7440e73341c9f | https://github.com/Jack4B/ketchup-player/actions/runs/42475534591 ; 517 testów wykonanych: 507 PASS, 10 FAIL (9 w VodBrowseModesScreenTest i mojaListaISzukajDzialajaSamymPilotem, asercje fokusu w trybie dotyku); 66 pozostałych scenariuszy ScenariosTest PASS, w tym syntetycznyKatalogImportujeSzukaIPamietaUlubione; FocusItemReproTest 10 wariantów wykonanych (dane do diagnozy); Roborazzi, Lint i emulator pominięte po porażce kroku testów
test: CI PR #11 (bcf8852) | NOT_RUN | bcf8852f69aa71de7e667f27d6559fe957495541 | https://github.com/Jack4B/ketchup-player/actions/runs/42476586646 ; build przerwany na pobieraniu Gradle (timeout 10000 ms, services.gradle.org), żaden test się nie wykonał; poprawki testów z tego SHA niezweryfikowane
test: CI PR #11 na końcowym SHA | NOT_RUN | none | uruchamiany po tym wpisie; wynik w raporcie sesji i w PR, nie jest tu przewidywany
test: scenariusz syntetycznego katalogu (Room, Szukaj, ulubione) | PASS | cdf89525dec8b5f9d422abf11bc7440e73341c9f | wykonany w biegu 42475534591 (JUnit ScenariosTest); nie powtórzony na późniejszych SHA
test: parser M3U aplikacji na prawdziwej liście (JVM, bez Gradle) | PASS | working_tree | 4578 kanałów w 109 grupach, 19159 filmów, 3475 seriali, 76386 odcinków, 0 pominiętych, parsowanie 774 ms, wyszukiwanie 25-50 ms; to nie jest import do Room ani pomiar na boxie
test: odtwarzanie próbki strumieni z prawdziwej listy | NOT_RUN | none | serwery mediów to adresy IP, polityka sieci środowiska ich nie przepuszcza (403)
test: D-pad, ulubione i import do Room na prawdziwej liście | NOT_RUN | none | wymaga środowiska Android; lista nie może trafić do CI
test: ./gradlew lokalnie | NOT_RUN | none | brak Android SDK w środowisku Cloud
test: TV-box (fokus, BACK, Szukaj, Moja lista) | NOT_RUN | none | brak urządzenia
```
