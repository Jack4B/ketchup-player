# HANDOFF: migawka ostatniej sesji

Krótka migawka stanu, **nie backlog**. Plany i checkboxy zostają w `docs/PLAN-1.0.md` i `docs/PLAN-FINALNY.md`; historia
w `docs/HISTORIA-SESJI.md`. Zapis: `/domknij-projekt`, odczyt: `/wczytaj-projekt`.
Walidator (`python3 -B tools/handoff/validate_handoff.py`) sprawdza strukturę i wykrywalne rozbieżności; nie dowodzi, że opis jest prawdziwy ani aktualny.

## Format

Jeden blok ` ```handoff ` z liniami `klucz: wartość` (bez YAML). Wiersze `test:` mogą się powtarzać.

```text
updated_at: 2026-11-08T22:11:02+01:00
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
updated_at: 2026-11-09T09:06:07+01:00
status: active
branch: feat/list-diag-script
pr: https://github.com/Jack4B/ketchup-player/pull/16
base_sha: 156280f7bffefa6581258dcaa1a9611d24787df2
verified_code_sha: f3e577ecb612c6126a29f5ba446d165400a84c7c
dirty_tree: clean
dirty_files: none
outside_handoff_changes: none
task_state: implemented
plan_point: Zgłoszenie z telefonu (3 punkty): PR 1a diagnostyka listy HTTP 504 (ta gałąź), PR 3 czyszczenie tytułów i oceny IMDb (równolegle, osobna gałąź), PR 1b poprawka 504 dopiero po wyniku diagnostyki, PR 2 dotyk w odtwarzaczu po PR 1a i 3
done: PR #15 (issue #12: maskowanie danych dostępu w URL, ErrorText, allow-list lastError, brak treści i nagłówków serwera w komunikatach, testy JVM core:common/network/xmltv/m3u/xtream w CI) scalony do dev (fc1d30ec5a979cd94cb9ec5c85c28acd60202a02), CI push na dev zielone; wydanie testowe 0.9.11-rc2 (versionCode 91102, pakiet pl.ketchupplayer.tv.debug, podpis kluczem debug z repo jak rc1) opublikowane z tagu rc-0.9.11-rc2 na fc1d30e (APK sha256 2321a4d795a50b25b02eabf669d127c9566b36616d9e7af53bcab5b969997084); PR #16 (ta gałąź): tools/tvbox/ketchup_list_diag.sh (curl, bez ADB; bazowe GET get.php + maks. 4 warianty: GET player_api, POST get.php, POST player_api, GET z UA VLC; przerwa >= 30 s i Retry-After; raport bez adresu/hosta/loginu/hasła/Server/Set-Cookie), atrapa curl i testy offline, jedna linia w rc.yml (skrypt w następnym RC; do rc2 pobrać z repo)
unfinished: wynik diagnostyki z telefonu użytkownika (potrzebny do PR 1b); PR 3 w innej gałęzi; PR 1b i PR 2 nierozpoczęte; testy fizycznego boxa po rc2 (zimny start w trybie klawiszowym, BACK ze szczegółów, LEWO na pustej Mojej liście, skrypt boxa, punkt 1.3 NIEODHACZONY); CI PR #16 na końcowym SHA do odczytania
next_action: odczytać CI PR #16 i zgłosić gotowość (merge tylko za osobną zgodą); przeczytać diag.md od użytkownika i dopiero wtedy zaplanować PR 1b (UA / POST jako domyślny lub zapasowy / podpowiedź Xtream / strumieniowanie listy kanałów Xtream); PR 2 (dotyk w odtwarzaczu) po PR 1a i 3 z notą w ADR tv-first
blockers: brak TV-boxa (testy fizyczne po RC); push gałęzi rcbuild na commit bota z [skip ci] NIE uruchamia rc.yml (RC wypychamy na commit z kodem albo przez tag rc-*); sesja Claude dostaje 403 przy pushu tagów, więc tag tworzy właściciel; pobieranie zależności w CI bywa zawodne (rc2: dwa biegi padły na HTTP 504 przy kotlin-compiler-embeddable z github.com/JetBrains/kotlin/releases, trzeci przeszedł); RYZYKO (bez kodu teraz): przyszły mikro-PR (dopiero po RC i tylko na polecenie właściciela) może dodać filtr treści repozytoriów w settings.gradle.kts i build-logic/settings.gradle.kts (gradlePluginPortal bez grup org.jetbrains.kotlin, które mają iść z mavenCentral), bo plugins.gradle.org przekierowywał na github.com/JetBrains/kotlin/releases (504)
do_not_touch: main; PR #5 (dev do main); PR #14 (personalizacja po pierwszym RC); lista IPTV użytkownika (adres i dane dostępu tylko w prywatnym pliku poza repo, nigdy w commitach, logach i raportach); wydawanie RC, rcbuild, tag, publikacja i instalacja APK bez decyzji; kryteria wymagające fizycznego boxa (nie odhaczać)
test: bash tools/tvbox/tests/run_diag_tests.sh (atrapa curl; ShellCheck 0.11.0 na skrypcie, testach i atrapie) | PASS | f3e577ecb612c6126a29f5ba446d165400a84c7c | 46 PASS, 0 FAIL, 0 NOT_RUN, lokalnie
test: CI PR #15 na końcowym SHA | PASS | ef3ddd4814706cbd6fb0443df6db8c62c90ef1e2 | https://github.com/Jack4B/ketchup-player/actions/runs/42523916967 ; build (testy jednostkowe 636, Roborazzi verify 321, bez porażek), gitleaks, emulator
test: CI dev po scaleniu PR #15 | PASS | fc1d30ec5a979cd94cb9ec5c85c28acd60202a02 | https://github.com/Jack4B/ketchup-player/actions/runs/42527006213 ; build, gitleaks, emulator
test: Release candidate rc2 (attempt 3) | PASS | fc1d30ec5a979cd94cb9ec5c85c28acd60202a02 | https://github.com/Jack4B/ketchup-player/actions/runs/42569216056 ; pre-release v0.9.11-rc2; odcisk certyfikatu SHA-256 84:15:AD:8F:75:CD:17:95:A9:9A:A1:38:9E:F9:2C:B2:C2:69:33:BE:25:26:36:80:C9:43:0D:52:45:8B:C2:21 jak w rc1
test: CI PR #16 na końcowym SHA | NOT_RUN | none | uruchamiany po tym wpisie; wynik w raporcie sesji i w PR, nie jest tu przewidywany
test: skrypt na TV-boxie (S00-S12) | NOT_RUN | none | po instalacji rc2; brak urządzenia
test: skrypt diagnostyczny listy na telefonie użytkownika | NOT_RUN | none | uruchamia właściciel na prawdziwej liście; wynik wraca jako diag.md
test: TV-box (fokus, BACK, Szukaj, Moja lista, LEWO z akcji pustej listy, BACK ze szczegółów, zimny start) | NOT_RUN | none | brak urządzenia; pozycje w docs/TEST-CHECKLIST.md
```
