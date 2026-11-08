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
updated_at: 2026-11-08T10:30:27+01:00
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
done: zgłoszenie Redactor jako osobny problem (issue #12, bez prawdziwych danych); syntetyczny katalog SyntheticCatalog (4 kanały bez prefiksu /live/, 6 filmów, 2 seriale po 3 odcinki) i scenariusz syntetycznyKatalogImportujeSzukaIPamietaUlubione (Room, Szukaj, Moja lista; niewykonany); dopisane testy (VodBrowseModesScreenTest, scenariusz E2E mojaListaISzukajDzialajaSamymPilotem, audyt układu Moja lista i Szukaj) wraz z tymczasowymi sondami fokusu; analiza listy M3U parserem aplikacji na JVM; ustalone: strzałki w polu wyszukiwania zostają w polu (Robolectric), Redactor nie maskuje adresów host/użytkownik/hasło/id
unfinished: przyczyna braku fokusu FocusItem w testach nieustalona (hipoteza: struktura clickable + focusable albo semantyka Focused, do rozstrzygnięcia sondą FocusItemReproTest); testy z asercjami fokusu nadal czerwone w ostatnim biegu z wynikami; sondy (println, FocusItemReproTest) do usunięcia przed scaleniem; kryterium 1.3 nie zamknięte
next_action: gdy CI znowu pobiera zależności: odczytać raport JUnit z FocusItemReproTest na 60ae6be, przepisać asercje fokusu według wyniku (bez requestFocus w teście i bez zmian FocusItem bez dowodu), usunąć sondy, zaktualizować ten wpis przed końcowym CI; osobno, po decyzji: mikro-PR Redactor dla ścieżek host/użytkownik/hasło/id (test najpierw)
blockers: CI build na 16f9279 (5 prób) i na 50b979c: krok "Weryfikacja reguł modułów (build-logic)", pobranie Gradle 8.14.3, HTTP 504 (timeout bramki GitHub, nie brak pliku); na 60ae6be: spotlessKotlin nie rozwiązał ktlint-cli 1.5.0 (kod odpowiedzi nieznany, konfiguracja repozytoriów bez zmian); to blokada pobierania zależności, nie błąd kodu; odtwarzanie próbki strumieni zablokowane polityką sieci środowiska (serwery mediów to adresy IP); brak TV-boxa
do_not_touch: main; PR #5 (dev do main); lista IPTV (adres i dane dostępu tylko w prywatnym pliku poza repo, nigdy w commitach, logach i raportach); CI i wersja Gradle; produkcyjna obsługa fokusu bez dowodu
test: python3 -B -m unittest discover -s tools/handoff -p 'test_*.py' | PASS | 338800b5f5116f31b4175b8f9f91615520a81898 | 53 testy, exit 0 (klon w katalogu roboczym; CI tych testów nie uruchamia)
test: CI dev po scaleniu PR #9 | PASS | 9e1a5aa739bde4f3e08326c81f925de83b6f48df | https://github.com/Jack4B/ketchup-player/actions/runs/42423901738 ; gitleaks, build, emulator
test: CI PR #11 po poprawce nazwy testu | FAIL | d2787cd313a20d8b703695e112c655214f6fe1f9 | https://github.com/Jack4B/ketchup-player/actions/runs/42463375114 ; nowe testy z asercjami fokusu czerwone, sondy wykonane; E2E mojaListaISzukajDzialajaSamymPilotem czerwony
test: CI PR #11 na ostatnich SHA | NOT_RUN | none | build zablokowany pobieraniem zależności (Gradle 504 na 16f9279 i 50b979c; ktlint-cli 1.5.0 na 60ae6be), uruchomienia testów nie było; wynik sond FocusItemReproTest nieznany
test: scenariusz syntetycznego katalogu (Room, Szukaj, ulubione) | NOT_RUN | none | dopisany, niewykonany (CI zablokowane); kształt fixture sprawdzony lokalnie parserem aplikacji: 4 kanały, 6 filmów, 2 seriale, 6 odcinków
test: parser M3U aplikacji na prawdziwej liście (JVM, bez Gradle) | PASS | working_tree | 4578 kanałów w 109 grupach, 19159 filmów, 3475 seriali, 76386 odcinków, 0 pominiętych, parsowanie 774 ms, wyszukiwanie 25-50 ms; to nie jest import do Room ani pomiar na boxie
test: odtwarzanie próbki strumieni z prawdziwej listy | NOT_RUN | none | serwery mediów to adresy IP, polityka sieci środowiska ich nie przepuszcza (403)
test: D-pad, ulubione i import do Room na prawdziwej liście | NOT_RUN | none | wymaga środowiska Android; lista nie może trafić do CI
test: ./gradlew lokalnie | NOT_RUN | none | brak Android SDK w środowisku Cloud
test: TV-box (fokus, BACK, Szukaj, Moja lista) | NOT_RUN | none | brak urządzenia
```
