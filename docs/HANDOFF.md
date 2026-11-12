# HANDOFF: migawka ostatniej sesji

Krótka migawka stanu, **nie backlog**. Plany i checkboxy zostają w `docs/PLAN-1.0.md` i `docs/PLAN-FINALNY.md`; historia
w `docs/HISTORIA-SESJI.md`. Zapis: `/domknij-projekt`, odczyt: `/wczytaj-projekt`.
Walidator (`python3 -B tools/handoff/validate_handoff.py`) sprawdza strukturę i wykrywalne rozbieżności; nie dowodzi, że opis jest prawdziwy ani aktualny.

## Format

Jeden blok ` ```handoff
updated_at: 2026-11-12T12:05:00+01:00
status: ready_for_review
branch: docs/personalizacja-konspekt
pr: https://github.com/Jack4B/ketchup-player/pull/14
base_sha: 2a7b74fcabd286f866fb3f3608a5570290a9aea1
verified_code_sha: none
dirty_tree: dirty
dirty_files: merge dev do gałęzi w toku (FakePlayerModule.kt, PlayerViewModel.kt, feature/settings strings.xml, DESIGN-LOG, HISTORIA-SESJI, HANDOFF rozwiązane; reszta z dev)
outside_handoff_changes: merge dev (PR #22 i #23) do docs/personalizacja-konspekt
task_state: implemented
plan_point: PLAN-FINALNY Krok 9: personalizacja MVP w PR #14; scalanie za zgodą właściciela w kolejności #22, #23, #14
done: PR #22 (atrapa odtwarzacza, merge d740d43) i PR #23 (poprawki rc4: insets OSD, edycja listy, grupy per lista, wybór listy; merge 2a7b74f) scalone do dev za zgodą właściciela; dev scalony do PR #14 bez przepisywania historii; konflikty: FakePlayerModule (wersja z #22, wynik play() w osobnym obiegu, plus playingWhenReady, durationMs i advance z #14), PlayerViewModel (toSnapshot obserwatora personalizacji obok zapList ograniczonego do jednej listy), strings.xml Ustawień (personal_* i groups_from_list), DESIGN-LOG i HISTORIA-SESJI (oba wpisy chronologicznie); DataModule.kt i emulator-smoke.sh scalone automatycznie; personalizacja z #14 bez zmian funkcji
unfinished: CI PR #14 na nowym HEAD i scalenie po zielonym CI; działanie na TV-boxie i telefonie (pilot, płynność, pamięć, prawdziwe EPG, przycisk Wstecz pod paskiem stanu na Androidzie 15) po stronie właściciela; przycisk „nie lubię” bez UI; obserwowanie rozgrywek
next_action: po zielonym build, gitleaks i emulator na HEAD PR #14 scalić PR #14 zwykłym merge i sprawdzić CI na dev
blockers: brak urządzeń (telefon z Androidem 15, TV-box) w sesji Cloud
do_not_touch: main; PR #5; workflow wydania, tagi i wydania rc; rcbuild, publikacja i instalacja APK; lista IPTV użytkownika i pliki PCAP; logika failoveru; kryteria wymagające fizycznego boxa (nie odhaczać)
test: ./gradlew testDebugUnitTest :core:model:test :core:policy:test :epg:radar:test :provider:api:test | PASS | working_tree | lokalnie 2026-11-12 na próbnym scaleniu o identycznym kodzie (różnią się tylko zrzuty bota w docs/qa/emulator), exit 0
test: ./gradlew verifyRoborazziDebug :app:assembleDebug | PASS | working_tree | lokalnie 2026-11-12 na próbnym scaleniu, exit 0
test: ./gradlew verifyModuleDependencies detekt :app:lintDebug | PASS | working_tree | lokalnie 2026-11-12 na próbnym scaleniu, exit 0
test: CI PR #14 na nowym HEAD (build, gitleaks, emulator) | NOT_RUN | none | uruchamiany po wypchnięciu scalenia
test: telefon Android 15 i TV-box | NOT_RUN | none | brak urządzenia; testy sprzętowe po stronie właściciela
```text
updated_at: 2026-11-09T20:48:15+00:00
status: plan | active | blocked | ready_for_review | done
branch / pr: nazwa gałęzi; URL PR albo none
base_sha / verified_code_sha: pełne SHA (40 hex); verified_code_sha może być none
dirty_tree: clean | dirty        (stan na moment pomiaru, przed zapisem handoffu)
dirty_files: none | pełna lista zmian drzewa
outside_handoff_changes: none | zmiany poza docs/HANDOFF.md i docs/HISTORIA-SESJI.md
task_state: implemented
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
updated_at: 2026-11-12T11:10:45+01:00
status: ready_for_review
branch: fix/rc4-playlisty-osd
pr: none
base_sha: 640391b47e612eb3d1d857768f7b55f22b2a99dd
verified_code_sha: none
dirty_tree: clean
dirty_files: none
outside_handoff_changes: none
task_state: implemented
plan_point: poprawki błędów z testów rc4 (OSD Wstecz, edycja listy, grupy przy dwóch listach, wybór listy); osobny PR do dev, merge tylko po zgodzie właściciela
done: OSD dotykowe: pasek z przyciskiem wyjścia i dolny OSD odsunięte od pasków systemu i wycięcia aparatu (SDK 35 rysuje pod paskami na Androidzie 15); edycja zapisanej listy (CatalogRepository.update, UPDATE zamiast upsert, puste adres/login/hasło/własny UA = bez zmian, sam host na ekranie, ta sama lista bez duplikatu, przy błędzie pobrania zostają kanały); grupy i kategorie związane z listą (ProviderNameCount, HiddenGroups lista+nazwa ze zgodnością ze starymi wpisami, PlaylistScope: dopisek nazwy listy przy powtórzonej nazwie, „Wszystkie” listami bez przeplatania numerów, CH+/CH− w obrębie listy); wybór listy w Na żywo, Filmach i Serialach (PlaylistFilterRepository w DataStore per ekran, usunięta lista = wszystkie, pusta lista ze stanem i powrotem); testy jednostkowe, Room, UI z D-pad i zrzutami, scenariusz E2E na dwóch listach, scenariusz w emulator-smoke.sh; PR #22 (atrapa odtwarzacza) i PR #14 (personalizacja) mają zielone CI, nie scalone
unfinished: CI tego PR; decyzja właściciela o scaleniu PR #22, #14 i tego PR; test na telefonie z Androidem 15 (przycisk Wstecz pod paskiem stanu) i na TV-boxie (wybór listy pilotem, dwie prawdziwe listy) po stronie właściciela; przyczyna punktu „Wstecz w OSD” ustalona z kodu (brak odsunięcia od pasków systemu przy SDK 35), bez zgłoszenia z urządzenia do porównania
next_action: odczytać CI tego PR i zgłosić właścicielowi; nie scalać bez zgody
blockers: brak urządzeń (telefon z Androidem 15, TV-box) w sesji Cloud
do_not_touch: main; PR #5; workflow wydania, tagi i wydania rc; lista IPTV użytkownika i pliki PCAP; logika failoveru
test: ./gradlew testDebugUnitTest :core:model:test :core:policy:test :epg:radar:test :provider:api:test | PASS | working_tree | lokalnie 2026-11-12, exit 0, 653 testy bez porażek (przed ostatnimi poprawkami stylu; moduły po poprawkach powtórzone niżej)
test: ./gradlew verifyRoborazziDebug i :app:assembleDebug | PASS | working_tree | lokalnie 2026-11-12, exit 0; nowe wzorce guide_playlist i providers_edit nagrane lokalnie i obejrzane
test: ./gradlew verifyModuleDependencies detekt lintDebug + testy core:data Playlist*, feature:providers, feature:guide, feature:player-ui | PASS | working_tree | lokalnie 2026-11-12, exit 0, po ostatnich poprawkach stylu
test: CI tego PR (build, gitleaks, emulator) | NOT_RUN | none | uruchamiany po otwarciu PR
test: telefon Android 15 i TV-box | NOT_RUN | none | brak urządzenia; testy sprzętowe po stronie właściciela
```
