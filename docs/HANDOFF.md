# HANDOFF: migawka ostatniej sesji

Krótka migawka stanu, **nie backlog**. Plany i checkboxy zostają w `docs/PLAN-1.0.md` i `docs/PLAN-FINALNY.md`; historia
w `docs/HISTORIA-SESJI.md`. Zapis: `/domknij-projekt`, odczyt: `/wczytaj-projekt`.
Walidator (`python3 -B tools/handoff/validate_handoff.py`) sprawdza strukturę i wykrywalne rozbieżności; nie dowodzi, że opis jest prawdziwy ani aktualny.

## Format

Jeden blok ` ```handoff
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
updated_at: 2026-11-09T11:51:38+01:00
status: active
branch: feat/player-dotyk
pr: https://github.com/Jack4B/ketchup-player/pull/20
base_sha: 4acd3fb33c31190a166bfb4c0f8d3fd1318c5b45
verified_code_sha: none
dirty_tree: clean
dirty_files: none
outside_handoff_changes: none
task_state: implemented
plan_point: PR 2: dotyk w odtwarzaczu (telefon, minimalnie; pełny telefon w 2.0, bez wpływu na bramkę 1.0 i decyzję tv-first), ta gałąź po włączeniu dev z poprawką importu (PR #21 scalony); merge PR #20 tylko po osobnej zgodzie właściciela
done: PR #15-#18 scalone do dev; rc2 (tag rc-0.9.11-rc2, bieg 42569216056, trzecia próba) i rc3 (tag rc-0.9.11-rc3 na 2e3e35e, bieg 42619214582) wyszły; rc3 pierwszym testem boxa od rc1; PR #19 (analiza 504) i #20 (dotyk w odtwarzaczu) otwarte bez merge; PR #21 scalony do dev (merge d8091a1, 2026-11-09 20:06 UTC; CI push na dev bieg 42689732409 zielony: build, gitleaks, emulator; później commit bota 4acd3fb tylko docs/qa/emulator): z przechwytu IPTV Extreme (lista działa, nasz UA OTT Navigator dostaje 520 od Cloudflare): klient list wysyła tylko User-Agent, Host, Connection, Accept-Encoding (dokładnie ta kolejność, test), domyślny UA okhttp/4.10.0, OTT Navigator jako profil, UA odtwarzacza VLC/3.4.3 LibVLC/3.4.31 z Accept, 302 na inny host zachowuje UA i Range (test); link get.php/player_api.php z loginem importowany jako konto Xtream przez player_api.php GET w kolejności konto, live kategorie, live strumienie (strumieniowo), vod kategorie, vod, seriale kategorie, seriale, get.php jako tryb zapasowy (XtreamFirstProvider); Room providers.userAgent (migracja 8 do 9, 9.json z KSP w CI), własny UA jako dane dostępu w SecretStore; ketchup_list_diag.sh diag-2 z E1-E3; scenariusz emulatora panelu za Cloudflare, który znalazł błąd: OkHttpTextFetcher.fetchText czytał treść odpowiedzi na wątku głównym (NetworkOnMainThreadException przy imporcie konta Xtream, także przy ręcznie dodanych kontach), naprawione na IO z testem odtwarzającym błąd; poprawki po przeglądzie właściciela (ten sam PR #21): własny UA wpisywany w kreatorze i w Ustawieniach → Sieć per lista (SecretStore, walidacja znaków nagłówka), UA listy w EPG (EpgTarget, StreamFetcher.withUserAgent) i CrossHostGuard (przy przekierowaniu na inny host tylko nagłówki tożsamości), migracja DataStore → Room/SecretStore (LegacyUserAgentMigration, powtarzalna), przenoszenie ulubionych/ukrytych/ostatnio oglądanych i filmów po numerze strumienia przy zmianie trybu M3U ↔ API (StreamIdentity; seriale zostają pod starymi id: ograniczenie), kontrolowany fallback do get.php (tylko brak API; bez fallbacku po 401/403, 429 i Retry-After, cooldown w repozytorium), odtwarzacz przez PlayerDataSources: Accept-Language en_US i Range bytes=0- tylko dla pierwszego żądania bezpośredniego .ts, test na prawdziwej fabryce źródeł danych Media3 (Robolectric); drugi błąd z tego scenariusza: źródło EPG zwracające nie-XML (JSON z xmltv.php) wywalało aplikację XmlPullParserException, teraz porażka źródła (test); seriale po zmianie trybu M3U na API: dane zostają zapisane, ale mogą być niewidoczne do powrotu na M3U (ograniczenie zaakceptowane przez właściciela); PR #20 (ta gałąź): tryb dotyku w odtwarzaczu (TouchMode włączany dotknięciem, wyłączany klawiszem; stuknięcie pokazuje/chowa OSD, przyciski wyjścia, odtwórz/pauza, ±10 s, pasek postępu stuknięciem i przeciągnięciem z jednym seekiem po puszczeniu; na żywo bez paska; onPreviewKeyEvent nietknięty), testy PlayerTouchTest (Compose, regresja pilota) i 3 zrzuty Roborazzi z artefaktu CI, nota w docs/adr/tv-first.md i DESIGN-LOG; ta gałąź zawiera merge origin/dev po PR #21 (konflikty tylko HANDOFF i HISTORIA-SESJI), dotyk NIE jest scalony do dev
unfinished: CI PR #20 na nowym HEAD (po merge dev) do odczytania; merge PR #19 i #20 tylko po osobnej zgodzie właściciela; działanie na prawdziwym panelu i testy telefonu/TV-boxa po stronie właściciela (rc po merge); wynik diagnostyki E1-E3 (właściciel); seriale po zmianie trybu M3U na API mogą być niewidoczne do powrotu na M3U; testy fizycznego boxa po rc3 (punkt 1.3 NIEODHACZONY, zimny start w trybie klawiszowym, BACK ze szczegółów, LEWO na pustej Mojej liście, POST na własnej liście)
next_action: po zielonym CI na nowym HEAD zgłosić PR #20 właścicielowi (merge po osobnej zgodzie); opcje audio/napisy dotykiem, układ pionowy i gesty zostają w 2.0
blockers: brak TV-boxa (testy fizyczne po RC); push gałęzi rcbuild na commit bota z [skip ci] NIE uruchamia rc.yml (RC wypychamy na commit z kodem albo przez tag rc-*); sesja Claude dostaje 403 przy pushu tagów, więc tag tworzy właściciel; pobieranie zależności w CI bywa zawodne (rc2: dwa biegi padły na HTTP 504 przy kotlin-compiler-embeddable z github.com/JetBrains/kotlin/releases, trzeci przeszedł); RYZYKO (bez kodu teraz): przyszły mikro-PR (dopiero po RC i tylko na polecenie właściciela) może dodać filtr treści repozytoriów w settings.gradle.kts i build-logic/settings.gradle.kts (gradlePluginPortal bez grup org.jetbrains.kotlin, które mają iść z mavenCentral), bo plugins.gradle.org przekierowywał na github.com/JetBrains/kotlin/releases (504)
do_not_touch: main; PR #5 (dev do main); PR #14 (personalizacja po pierwszym RC); lista IPTV użytkownika (adres i dane dostępu tylko w prywatnym pliku poza repo, nigdy w commitach, logach i raportach); wydawanie RC, rcbuild, tag, publikacja i instalacja APK bez decyzji; kryteria wymagające fizycznego boxa (nie odhaczać)
test: CI PR #15 na końcowym SHA | PASS | ef3ddd4814706cbd6fb0443df6db8c62c90ef1e2 | https://github.com/Jack4B/ketchup-player/actions/runs/42523916967 ; build (testy jednostkowe 636, Roborazzi verify 321, bez porażek), gitleaks, emulator
test: CI dev po scaleniu PR #15 | PASS | fc1d30ec5a979cd94cb9ec5c85c28acd60202a02 | https://github.com/Jack4B/ketchup-player/actions/runs/42527006213 ; build, gitleaks, emulator
test: Release candidate rc2 (attempt 3) | PASS | fc1d30ec5a979cd94cb9ec5c85c28acd60202a02 | https://github.com/Jack4B/ketchup-player/actions/runs/42569216056 ; pre-release v0.9.11-rc2; odcisk certyfikatu SHA-256 84:15:AD:8F:75:CD:17:95:A9:9A:A1:38:9E:F9:2C:B2:C2:69:33:BE:25:26:36:80:C9:43:0D:52:45:8B:C2:21 jak w rc1
test: core:network (41) i providery m3u/xtream (M3uRequestTest 8, XtreamPostTest 5), JVM z OkHttp 4.12 i MockWebServer, kompilator z dystrybucji Gradle | PASS | 71dfc60b5f0f92220a544a6a32a03a4961ba98b9 | lokalnie; ImportErrorsTest i StoredErrorsTest 20/20, RedactorTest 29/29
test: CI PR #16 na końcowym SHA | PASS | fede0f7708c25abb232529689d2d4f033724591f | https://github.com/Jack4B/ketchup-player/actions/runs/42584678888
test: CI PR #17 na końcowym SHA | PASS | 1b4b9a10a64c1759e104e21e76ac41cfc293df68 | https://github.com/Jack4B/ketchup-player/actions/runs/42584728704
test: CI PR #21 na końcowym SHA | PASS | 960bd6b01156dd5083162dc8a40e205dfe57e82c | https://github.com/Jack4B/ketchup-player/actions/runs/42683546892 ; build, gitleaks, emulator
test: CI dev po scaleniu PR #21 | PASS | d8091a1fb7a3f82d874ae51ea5d4f7a226b92a10 | https://github.com/Jack4B/ketchup-player/actions/runs/42689732409 ; build, gitleaks, emulator (commit bota 4acd3fb dopisał tylko docs/qa/emulator)
test: CI PR #20 na nowym HEAD (po merge dev) | NOT_RUN | none | uruchamiany po tym wpisie; wynik w raporcie sesji i w PR
test: skrypt diagnostyczny listy na telefonie użytkownika (PR #16) i import prawdziwej listy przez POST | NOT_RUN | none | uruchamia właściciel; wynik wraca jako diag.md
test: skrypt na TV-boxie (S00-S12) | NOT_RUN | none | po instalacji rc3; brak urządzenia
test: TV-box (fokus, BACK, Szukaj, Moja lista, LEWO z akcji pustej listy, BACK ze szczegółów, zimny start) | NOT_RUN | none | brak urządzenia; pozycje w docs/TEST-CHECKLIST.md
```
