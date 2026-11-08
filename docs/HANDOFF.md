# HANDOFF: migawka ostatniej sesji

Krótka migawka stanu, **nie backlog**. Plany i checkboxy zostają w `docs/PLAN-1.0.md` i `docs/PLAN-FINALNY.md`; historia
w `docs/HISTORIA-SESJI.md`. Zapis: `/domknij-projekt`, odczyt: `/wczytaj-projekt`.
Walidator (`python3 -B tools/handoff/validate_handoff.py`) sprawdza strukturę i wykrywalne rozbieżności; nie dowodzi, że opis jest prawdziwy ani aktualny.

## Format

Jeden blok ` ```handoff ` z liniami `klucz: wartość` (bez YAML). Wiersze `test:` mogą się powtarzać.

```text
updated_at: 2026-11-08T16:18:03+01:00
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
updated_at: 2026-11-08T19:19:17+01:00
status: active
branch: fix/redactor-url-credentials
pr: https://github.com/Jack4B/ketchup-player/pull/15
base_sha: 12e41f8b62d618662dc715d34a98206841cea953
verified_code_sha: 4bceadda7b17d69efb3855b8a3f23e26ca5ae617
dirty_tree: clean
dirty_files: none
outside_handoff_changes: none
task_state: implemented
plan_point: Bezpieczeństwo P1 (CLAUDE.md) i issue #12: maskowanie danych dostępu w adresach URL i bezpieczne komunikaty błędów; warunek następnego RC
done: PR #11 (testy D-pad Szukaj i Moja lista) scalony do dev (1989e91); PR #13 (skrypt testów RC na boxie) scalony do dev (merge 12e41f8b62d618662dc715d34a98206841cea953), skrypt na dev ma 1155 linii; PR #15 (ta gałąź, draft): Redactor z SZEROKĄ regułą w kontekście URL (userinfo, rozszerzone parametry tylko w URL, ścieżki z prefiksem live/movie/series/timeshift i bez prefiksu także dla id bez rozszerzenia, zagnieżdżone adresy: cała wartość zastępowana maską bez dekodowania), ErrorText i SafeErrorMessage (nieznany wyjątek daje komunikat ogólny z kodem klasy), zastosowane w friendlyImportError, ProviderFactory, RoomCatalogRepository (zapis i odczyt lastError), KeyProbe i EpgSourcesViewModel; sanitizeDetail przez Redactor; testy napisane najpierw (czerwone na starym kodzie), lokalnie 27 z 27 w core:common i ImportErrorsTest 9 z 9, lint detekt i ktlint bez uwag; decyzje właściciela: szeroka reguła (fałszywe trafienia dopuszczone), bez zależności od SecretStore i rejestru sekretów, RC 0.9.11-rc2 (versionCode 91102) jeszcze nie publikowany
unfinished: pełne CI PR #15 na końcowym SHA do odczytania (testy Robolectric Room/Timber wykonuje dopiero CI); GRANICE: logi bibliotek (Media3, OkHttp, Coil) i systemu omijają Redactor; adres bez schematu, JSON, własne nagłówki i ścieżki poza URL nie są maskowane; wpisy providers.lastError zapisane przed poprawką mogą nadal zawierać adresy w bazie do następnego odświeżenia listy (bez masowego czyszczenia; wyświetlanie maskowane przy odczycie); OTWARTE z poprzednich paczek: zimny start w trybie klawiszowym (przyczyna nierozstrzygnięta), BACK ze szczegółów filmu (obecne zachowanie bez akceptacji UX), LEWO z akcji pustej Mojej listy; OCZEKUJE NA URZĄDZENIE (po wydaniu RC): skrypt S00-S12, ręczny test pilota (1.1, 1.2), D-pad w Szukaj i Mojej liście, wydajność (1.8)
next_action: odczytać pełne CI PR #15, zdjąć draft i zgłosić gotowość; merge tylko za osobną zgodą właściciela; RC 0.9.11-rc2 dopiero po zatwierdzonych scaleniach i zielonym CI push na dev na tym samym SHA (push rcbuild/0.9.11-rc2 z dev); osobna decyzja architektoniczna: maskowanie literalne znanych sekretów dostawców i przechwytywanie logów systemu i bibliotek
blockers: brak TV-boxa (testy fizyczne po RC); pobieranie zależności w CI bywa zawodne (HTTP 504, timeout Gradle, brak ktlint-cli bez kodu HTTP)
do_not_touch: main; PR #5 (dev do main); PR #14 (personalizacja po pierwszym RC); lista IPTV (adres i dane dostępu tylko w prywatnym pliku poza repo, nigdy w commitach, logach i raportach); wydawanie RC, rcbuild, tag, publikacja i instalacja APK bez decyzji; globalny rejestr rzeczywistych sekretów i zależność Redactora od SecretStore bez osobnej decyzji
test: python3 -B -m unittest discover -s tools/handoff -p 'test_*.py' | PASS | working_tree | 53 testy, exit 0 (CI tych testów nie uruchamia)
test: RedactorTest i ErrorTextTest (core:common, JUnit na kodzie źródłowym kompilatorem z dystrybucji Gradle) | PASS | 4bceadda7b17d69efb3855b8a3f23e26ca5ae617 | 27 z 27 lokalnie; przed implementacją 11 z 22 testów Redactora było czerwonych
test: ImportErrorsTest | PASS | 4bceadda7b17d69efb3855b8a3f23e26ca5ae617 | 9 z 9 lokalnie (kompilacja z Redactor i ErrorText)
test: detekt-cli 1.23.8 i ktlint-cli 1.5.0 (konfiguracja projektu) na core:common, core:network, core:data, feature:settings, app | PASS | 4bceadda7b17d69efb3855b8a3f23e26ca5ae617 | bez uwag; nie zastępuje kroku Spotless + Detekt w CI
test: CI dev po scaleniu PR #11 | PASS | 1989e9160d8176618dc02c5e4bc1b7f49db8cfc3 | https://github.com/Jack4B/ketchup-player/actions/runs/42498355469 ; build, gitleaks, emulator
test: CI dev po scaleniu PR #13 | NOT_RUN | none | bieg push 42508240664 na 12e41f8 był w toku w chwili zapisu; wynik w raporcie sesji
test: CI PR #15 na końcowym SHA | NOT_RUN | none | uruchamiany po tym wpisie; wynik w raporcie sesji i w PR, nie jest tu przewidywany
test: skrypt na TV-boxie (S00-S12) | NOT_RUN | none | po wydaniu RC; brak urządzenia
test: TV-box (fokus, BACK, Szukaj, Moja lista, LEWO z akcji pustej listy, BACK ze szczegółów, zimny start) | NOT_RUN | none | brak urządzenia; pozycje w docs/TEST-CHECKLIST.md
```
