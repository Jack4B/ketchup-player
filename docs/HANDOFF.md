# HANDOFF: migawka ostatniej sesji

Krótka migawka stanu, **nie backlog**. Plany i checkboxy zostają w `docs/PLAN-1.0.md` i `docs/PLAN-FINALNY.md`; historia
w `docs/HISTORIA-SESJI.md`. Zapis: `/domknij-projekt`, odczyt: `/wczytaj-projekt`.
Walidator (`python3 -B tools/handoff/validate_handoff.py`) sprawdza strukturę i wykrywalne rozbieżności; nie dowodzi, że opis jest prawdziwy ani aktualny.

## Format

Jeden blok ` ```handoff ` z liniami `klucz: wartość` (bez YAML). Wiersze `test:` mogą się powtarzać.

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
updated_at: 2026-11-11T14:30:34+01:00
status: ready_for_review
branch: docs/personalizacja-konspekt
pr: https://github.com/Jack4B/ketchup-player/pull/14
base_sha: 640391b47e612eb3d1d857768f7b55f22b2a99dd
verified_code_sha: none
dirty_tree: clean
dirty_files: none
outside_handoff_changes: none
task_state: implemented
plan_point: PLAN-FINALNY Krok 9: personalizacja MVP (etapy A+B+C z docs/design/PERSONALIZACJA-KONSPEKT.md) w PR #14; merge tylko po osobnej zgodzie właściciela
done: PR #19 (analiza 504), #20 (dotyk) i #21 (import przez player_api.php) scalone do dev; PR #14 zawiera implementację personalizacji: typy (core:model) i formuły w czystej JVM (core:policy: sesje liczone z czasu grania, siła sesji z sufitami odcinka i serialu, zanik i kurczenie wag, cechy tylko z metadanych, ranking z MMR i uzasadnieniami, parser drużyn z EPG, sugestie po transmisjach z trzech różnych dni, wydarzenia z „kanał niepotwierdzony”, czas warszawski); Room v10 (11 tabel, automatyczna migracja 9 do 10, 10.json z KSP, reżyser i sposób dopasowania TMDB w vod_meta); core:data/personalization (zgoda domyślnie wyłączona, tryb „nie ucz się”, retencja, rejestrator sesji z zapisem co 15 s i wznowieniami, silnik profilu i pasów, indeks cech, sport, eksport JSON, czyszczenie bez ruszania katalogu, Mojej listy i postępu); obserwator w PlayerViewModel (tylko słucha stanu); Home: pasy „Bo oglądasz”, „Twoje drużyny” i pytanie o obserwowanie; Ustawienia: kategoria Personalizacja; szczegóły: „Lubię to”; scenariusz personalizacji w emulator-smoke.sh; LayoutAudit mierzy pełną wysokość celu; lista kategorii Ustawień zagęszczona (9 kategorii na 540 dp); stan i 17 różnic względem projektu w sekcji 26 konspektu; środowisko Cloud ma teraz dl.google.com, więc Gradle i testy Robolectric działają lokalnie w sesji
unfinished: decyzja właściciela o scaleniu PR #14; działanie na TV-boxie i telefonie (pilot, płynność, pamięć przy dużym katalogu); jakość rekomendacji i sugestii drużyn na prawdziwej historii i prawdziwym EPG; indeks cech przy katalogu rzędu 30 tys. pozycji na urządzeniu; przycisk „nie lubię” (DISLIKE jest w modelu, bez UI); obserwowanie rozgrywek; niestabilny test failoverPrzelaczaNaDrugaListeGdyPierwszaNieDziala (hipoteza: atrapa odtwarzacza zgłasza BUFFERING i ERROR w jednym wywołaniu, więc StateFlow może zgubić drugi identyczny ERROR; niepotwierdzone, bez zmian w kodzie); bieg push CI na dev dla 310ea95 jest czerwony przez krok commitu zrzutów (konflikt rebase po ponowieniu), ten sam SHA ma zielony bieg pull_request 42764240076
next_action: odczytać końcowe CI PR #14 na HEAD gałęzi i zgłosić właścicielowi do oceny; nie scalać bez zgody
blockers: brak TV-boxa (testy fizyczne po RC); parametry algorytmu przyjęte bez strojenia na prawdziwych danych; sesja Claude dostaje 403 przy pushu tagów
do_not_touch: main; PR #5 (dev do main); workflow wydania, tagi i wydania rc4; lista IPTV użytkownika i pliki PCAP (nigdy w repo, logach i raportach); logika failoveru; wydawanie RC, rcbuild, tag, publikacja i instalacja APK bez decyzji; kryteria wymagające fizycznego boxa (nie odhaczać)
test: ./gradlew detekt | PASS | working_tree | lokalnie w sesji Cloud 2026-11-11, exit 0 (Spotless lokalnie nie pobiera ktlint; formatowanie sprawdzone ktlint z jar)
test: ./gradlew testDebugUnitTest :core:model:test :core:policy:test :epg:radar:test :provider:api:test | PASS | working_tree | lokalnie 2026-11-11, exit 0, 768 testów bez porażek (core:policy 104 w tym 73 personalizacji, core:data 209 w tym 35 przepływów na Room, core:database 9 w tym migracja 9 do 10, app 93 w tym 5 scenariuszy E2E personalizacji i audyt układu)
test: ./gradlew verifyRoborazziDebug | PASS | working_tree | lokalnie 2026-11-11, exit 0; nowe wzorce home_personal, personalization_settings, personalization_settings_off i zmieniony settings_panel_nad_na_zywo nagrane lokalnie (istniejące wzorce z CI weryfikują się lokalnie bez różnic)
test: ./gradlew :app:assembleDebug :app:lintDebug | PASS | working_tree | lokalnie 2026-11-11, exit 0
test: CI PR #14 na końcowym HEAD (build, gitleaks, emulator) | NOT_RUN | none | uruchamiany po tym wpisie; wynik w raporcie sesji i w PR
test: TV-box i telefon (pasy Home pilotem, ekran Personalizacja, zużycie pamięci, prawdziwe EPG) | NOT_RUN | none | brak urządzenia; testy sprzętowe po stronie właściciela
test: indeks cech dla 30 tys. pozycji na urządzeniu | NOT_RUN | none | brak urządzenia; na JVM jest test 5 tys. pozycji (FeatureIndexScaleTest)
```
