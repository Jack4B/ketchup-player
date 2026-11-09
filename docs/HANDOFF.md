# HANDOFF: migawka ostatniej sesji

Krótka migawka stanu, **nie backlog**. Plany i checkboxy zostają w `docs/PLAN-1.0.md` i `docs/PLAN-FINALNY.md`; historia
w `docs/HISTORIA-SESJI.md`. Zapis: `/domknij-projekt`, odczyt: `/wczytaj-projekt`.
Walidator (`python3 -B tools/handoff/validate_handoff.py`) sprawdza strukturę i wykrywalne rozbieżności; nie dowodzi, że opis jest prawdziwy ani aktualny.

## Format

Jeden blok ` ```handoff ` z liniami `klucz: wartość` (bez YAML). Wiersze `test:` mogą się powtarzać.

```text
updated_at: 2026-11-09T15:40:00+01:00
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
base_sha: e14d5793f24b2ce9745288f69f31e367b35b23fb
verified_code_sha: none
dirty_tree: clean
dirty_files: none
outside_handoff_changes: none
task_state: implemented
plan_point: PR 2: dotyk w odtwarzaczu (telefon, minimalnie; pełny telefon w 2.0, bez wpływu na bramkę 1.0 i decyzję tv-first); PR 1b (wyniki przechwytu IPTV Extreme: import przez player_api.php i UA okhttp) osobną gałęzią
done: PR #15, #16, #17, #18 scalone do dev (CI push na dev zielone po każdym); wydania testowe 0.9.11-rc2 (versionCode 91102, tag rc-0.9.11-rc2 na fc1d30e, bieg 42569216056 przy trzeciej próbie: rc2 jednak wyszedł) i 0.9.11-rc3 (tag rc-0.9.11-rc3 na 2e3e35e, bieg 42619214582, APK sha256 20ed57ab…, odcisk certyfikatu bez zmian, rc3 jest pierwszym testem boxa od rc1); schemat Room 8.json jest w repo; PR #19 (docs/analiza-504-playery.md: porównanie OwnTV/StreamVault, hipotezy 504, plan PR 1b) otwarty bez merge; PR #20 (ta gałąź): tryb dotyku w odtwarzaczu (TouchMode włączany dotknięciem, wyłączany klawiszem; stuknięcie pokazuje/chowa OSD, przyciski wyjścia, odtwórz/pauza, ±10 s, pasek postępu stuknięciem i przeciągnięciem z jednym seekiem po puszczeniu; na żywo bez paska; onPreviewKeyEvent nietknięty), testy PlayerTouchTest (Compose, regresja pilota) i 3 zrzuty Roborazzi z artefaktu CI, nota w docs/adr/tv-first.md i DESIGN-LOG
unfinished: CI PR #20 na końcowym SHA do odczytania; merge PR #19 i #20 tylko po zgodzie właściciela; PR 1b w osobnej gałęzi; wynik diagnostyki z telefonu i test prawdziwej listy (właściciel); testy fizycznego boxa po rc3 (punkt 1.3 NIEODHACZONY, zimny start w trybie klawiszowym, BACK ze szczegółów, LEWO na pustej Mojej liście, POST na własnej liście)
next_action: po zielonym CI na końcowym SHA zgłosić PR #20 właścicielowi (merge po osobnej zgodzie); opcje audio/napisy dotykiem, układ pionowy i gesty zostają w 2.0
blockers: brak TV-boxa (testy fizyczne po RC); push gałęzi rcbuild na commit bota z [skip ci] NIE uruchamia rc.yml (RC wypychamy na commit z kodem albo przez tag rc-*); sesja Claude dostaje 403 przy pushu tagów, więc tag tworzy właściciel; pobieranie zależności w CI bywa zawodne (rc2: dwa biegi padły na HTTP 504 przy kotlin-compiler-embeddable z github.com/JetBrains/kotlin/releases, trzeci przeszedł); RYZYKO (bez kodu teraz): przyszły mikro-PR (dopiero po RC i tylko na polecenie właściciela) może dodać filtr treści repozytoriów w settings.gradle.kts i build-logic/settings.gradle.kts (gradlePluginPortal bez grup org.jetbrains.kotlin, które mają iść z mavenCentral), bo plugins.gradle.org przekierowywał na github.com/JetBrains/kotlin/releases (504)
do_not_touch: main; PR #5 (dev do main); PR #14 (personalizacja po pierwszym RC); lista IPTV użytkownika (adres i dane dostępu tylko w prywatnym pliku poza repo, nigdy w commitach, logach i raportach); wydawanie RC, rcbuild, tag, publikacja i instalacja APK bez decyzji; kryteria wymagające fizycznego boxa (nie odhaczać)
test: CI PR #15 na końcowym SHA | PASS | ef3ddd4814706cbd6fb0443df6db8c62c90ef1e2 | https://github.com/Jack4B/ketchup-player/actions/runs/42523916967 ; build (testy jednostkowe 636, Roborazzi verify 321, bez porażek), gitleaks, emulator
test: CI dev po scaleniu PR #15 | PASS | fc1d30ec5a979cd94cb9ec5c85c28acd60202a02 | https://github.com/Jack4B/ketchup-player/actions/runs/42527006213 ; build, gitleaks, emulator
test: Release candidate rc2 (attempt 3) | PASS | fc1d30ec5a979cd94cb9ec5c85c28acd60202a02 | https://github.com/Jack4B/ketchup-player/actions/runs/42569216056 ; pre-release v0.9.11-rc2; odcisk certyfikatu SHA-256 84:15:AD:8F:75:CD:17:95:A9:9A:A1:38:9E:F9:2C:B2:C2:69:33:BE:25:26:36:80:C9:43:0D:52:45:8B:C2:21 jak w rc1
test: core:network (41) i providery m3u/xtream (M3uRequestTest 8, XtreamPostTest 5), JVM z OkHttp 4.12 i MockWebServer, kompilator z dystrybucji Gradle | PASS | 71dfc60b5f0f92220a544a6a32a03a4961ba98b9 | lokalnie; ImportErrorsTest i StoredErrorsTest 20/20, RedactorTest 29/29
test: CI PR #16 na końcowym SHA | PASS | fede0f7708c25abb232529689d2d4f033724591f | https://github.com/Jack4B/ketchup-player/actions/runs/42584678888
test: CI PR #17 na końcowym SHA | PASS | 1b4b9a10a64c1759e104e21e76ac41cfc293df68 | https://github.com/Jack4B/ketchup-player/actions/runs/42584728704
test: CI PR #20 na końcowym SHA | NOT_RUN | none | uruchamiany po tym wpisie; wynik w raporcie sesji i w PR
test: skrypt diagnostyczny listy na telefonie użytkownika (PR #16) i import prawdziwej listy przez POST | NOT_RUN | none | uruchamia właściciel; wynik wraca jako diag.md
test: skrypt na TV-boxie (S00-S12) | NOT_RUN | none | po instalacji rc3; brak urządzenia
test: TV-box (fokus, BACK, Szukaj, Moja lista, LEWO z akcji pustej listy, BACK ze szczegółów, zimny start) | NOT_RUN | none | brak urządzenia; pozycje w docs/TEST-CHECKLIST.md
```
