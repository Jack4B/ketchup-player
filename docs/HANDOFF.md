# HANDOFF: migawka ostatniej sesji

Krótka migawka stanu, **nie backlog**. Plany i checkboxy zostają w `docs/PLAN-1.0.md` i `docs/PLAN-FINALNY.md`; historia
w `docs/HISTORIA-SESJI.md`. Zapis: `/domknij-projekt`, odczyt: `/wczytaj-projekt`.
Walidator (`python3 -B tools/handoff/validate_handoff.py`) sprawdza strukturę i wykrywalne rozbieżności; nie dowodzi, że opis jest prawdziwy ani aktualny.

## Format

Jeden blok ` ```handoff ` z liniami `klucz: wartość` (bez YAML). Wiersze `test:` mogą się powtarzać.

```text
updated_at: 2026-11-09T19:20:00+01:00
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
branch: feat/xtream-player-api
pr: https://github.com/Jack4B/ketchup-player/pull/21
base_sha: e14d5793f24b2ce9745288f69f31e367b35b23fb
verified_code_sha: none
dirty_tree: clean
dirty_files: none
outside_handoff_changes: none
task_state: implemented
plan_point: PR 1b po przechwycie IPTV Extreme: import konta Xtream przez player_api.php (get.php tylko zapasowo), domyślny UA okhttp/4.10.0, UA odtwarzacza VLC, UA listy w Room (v9), warianty E1-E3 w ketchup_list_diag.sh; PR 2 (dotyk, PR #20) osobno
done: PR #15-#18 scalone do dev; rc2 (tag rc-0.9.11-rc2, bieg 42569216056, trzecia próba) i rc3 (tag rc-0.9.11-rc3 na 2e3e35e, bieg 42619214582) wyszły; rc3 pierwszym testem boxa od rc1; PR #19 (analiza 504) i #20 (dotyk w odtwarzaczu) otwarte bez merge; PR #21 (ta gałąź): z przechwytu IPTV Extreme (lista działa, nasz UA OTT Navigator dostaje 520 od Cloudflare): klient list wysyła tylko User-Agent, Host, Connection, Accept-Encoding (dokładnie ta kolejność, test), domyślny UA okhttp/4.10.0, OTT Navigator jako profil, UA odtwarzacza VLC/3.4.3 LibVLC/3.4.31 z Accept, 302 na inny host zachowuje UA i Range (test); link get.php/player_api.php z loginem importowany jako konto Xtream przez player_api.php GET w kolejności konto, live kategorie, live strumienie (strumieniowo), vod kategorie, vod, seriale kategorie, seriale, get.php jako tryb zapasowy (XtreamFirstProvider); Room providers.userAgent (migracja 8 do 9, 9.json z KSP w CI), własny UA jako dane dostępu w SecretStore; ketchup_list_diag.sh diag-2 z E1-E3; scenariusz emulatora panelu za Cloudflare, który znalazł błąd: OkHttpTextFetcher.fetchText czytał treść odpowiedzi na wątku głównym (NetworkOnMainThreadException przy imporcie konta Xtream, także przy ręcznie dodanych kontach), naprawione na IO z testem odtwarzającym błąd
unfinished: CI PR #21 na końcowym SHA (w tym job emulator z nowym scenariuszem) do odczytania; merge PR #19, #20, #21 tylko po zgodzie właściciela (konflikt HANDOFF/HISTORIA między #20 i #21 do scalenia); test na prawdziwej liście (właściciel, rc po merge); Media3 OkHttpDataSource: nagłówki z kodu biblioteki, bez przechwytu; punkt 1.3 boxa NIEODHACZONY
next_action: po zielonym CI na końcowym SHA zgłosić PR #21 właścicielowi; po merge i RC właściciel testuje prawdziwą listę (import powinien iść player_api.php z UA okhttp/4.10.0), a wynik diag-2 (E1-E3) rozstrzyga, czy dodatkowo potrzebna jest zmiana HTTP/1.1 lub Accept
blockers: brak TV-boxa (testy fizyczne po RC); push gałęzi rcbuild na commit bota z [skip ci] NIE uruchamia rc.yml (RC wypychamy na commit z kodem albo przez tag rc-*); sesja Claude dostaje 403 przy pushu tagów, więc tag tworzy właściciel; pobieranie zależności w CI bywa zawodne (rc2: dwa biegi padły na HTTP 504 przy kotlin-compiler-embeddable z github.com/JetBrains/kotlin/releases, trzeci przeszedł); RYZYKO (bez kodu teraz): przyszły mikro-PR (dopiero po RC i tylko na polecenie właściciela) może dodać filtr treści repozytoriów w settings.gradle.kts i build-logic/settings.gradle.kts (gradlePluginPortal bez grup org.jetbrains.kotlin, które mają iść z mavenCentral), bo plugins.gradle.org przekierowywał na github.com/JetBrains/kotlin/releases (504)
do_not_touch: main; PR #5 (dev do main); PR #14 (personalizacja po pierwszym RC); lista IPTV użytkownika (adres i dane dostępu tylko w prywatnym pliku poza repo, nigdy w commitach, logach i raportach); wydawanie RC, rcbuild, tag, publikacja i instalacja APK bez decyzji; kryteria wymagające fizycznego boxa (nie odhaczać)
test: CI PR #15 na końcowym SHA | PASS | ef3ddd4814706cbd6fb0443df6db8c62c90ef1e2 | https://github.com/Jack4B/ketchup-player/actions/runs/42523916967 ; build (testy jednostkowe 636, Roborazzi verify 321, bez porażek), gitleaks, emulator
test: CI dev po scaleniu PR #15 | PASS | fc1d30ec5a979cd94cb9ec5c85c28acd60202a02 | https://github.com/Jack4B/ketchup-player/actions/runs/42527006213 ; build, gitleaks, emulator
test: Release candidate rc2 (attempt 3) | PASS | fc1d30ec5a979cd94cb9ec5c85c28acd60202a02 | https://github.com/Jack4B/ketchup-player/actions/runs/42569216056 ; pre-release v0.9.11-rc2; odcisk certyfikatu SHA-256 84:15:AD:8F:75:CD:17:95:A9:9A:A1:38:9E:F9:2C:B2:C2:69:33:BE:25:26:36:80:C9:43:0D:52:45:8B:C2:21 jak w rc1
test: core:network (41) i providery m3u/xtream (M3uRequestTest 8, XtreamPostTest 5), JVM z OkHttp 4.12 i MockWebServer, kompilator z dystrybucji Gradle | PASS | 71dfc60b5f0f92220a544a6a32a03a4961ba98b9 | lokalnie; ImportErrorsTest i StoredErrorsTest 20/20, RedactorTest 29/29
test: CI PR #16 na końcowym SHA | PASS | fede0f7708c25abb232529689d2d4f033724591f | https://github.com/Jack4B/ketchup-player/actions/runs/42584678888
test: CI PR #17 na końcowym SHA | PASS | 1b4b9a10a64c1759e104e21e76ac41cfc293df68 | https://github.com/Jack4B/ketchup-player/actions/runs/42584728704
test: CI PR #21 na końcowym SHA | NOT_RUN | none | uruchamiany po tym wpisie; wynik w raporcie sesji i w PR
test: skrypt diagnostyczny listy na telefonie użytkownika (PR #16) i import prawdziwej listy przez POST | NOT_RUN | none | uruchamia właściciel; wynik wraca jako diag.md
test: skrypt na TV-boxie (S00-S12) | NOT_RUN | none | po instalacji rc3; brak urządzenia
test: TV-box (fokus, BACK, Szukaj, Moja lista, LEWO z akcji pustej listy, BACK ze szczegółów, zimny start) | NOT_RUN | none | brak urządzenia; pozycje w docs/TEST-CHECKLIST.md
```
