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
updated_at: 2026-11-08T18:26:11+01:00
status: active
branch: feat/tvbox-rc-skrypt
pr: https://github.com/Jack4B/ketchup-player/pull/13
base_sha: ba6c2bc66e8e20de05bbcacd4eb11c33c0d6d413
verified_code_sha: 738455a42315bbfc702edf824500944607213315
dirty_tree: clean
dirty_files: none
outside_handoff_changes: none
task_state: awaiting_device
plan_point: Krok 1 planu finalnego (jakość 1.0 na boxie): skrypt testów RC tools/tvbox/ketchup_tvbox_test.sh i docs/TVBOX-TESTY.md; nie zamyka 1.1, 1.2 ani 1.3
done: PR #11 scalony do dev (merge 1989e9160d8176618dc02c5e4bc1b7f49db8cfc3); CI push na tym SHA zielone (build, gitleaks, emulator); po nim commit bota zrzutów emulatora (dev ba6c2bc); testy D-pad Szukaj i Moja lista (VodBrowseModesScreenTest 12 testów, scenariusz pilota E2E po przygotowaniu danych, FocusItemTest) są na dev, przyczyna czerwonych asercji fokusu to ograniczenie Robolectric (tryb dotyku), nie błąd aplikacji; PR #13: skrypt testów RC rozwinięty w jeden samodzielny plik (dwa monity: ścieżka APK widoczna i ukryty adres listy; instalacja z aapt bez -d, bez odinstalowania i bez pm clear, błąd instalacji kończy scenariusze aplikacji; D-pad/OK/BACK z warunkami i limitami; statusy PASS/FAIL/BLOCKED/SKIPPED/MANUAL_REQUIRED; oczyszczanie logów i REDACTION_FAILED; jeden ZIP bez zrzutów i surowych XML); aktualny dev włączony do gałęzi zwykłym merge'em (bez rebase i force-push); decyzje właściciela: issue #12 (maskowanie danych dostępu) naprawiane PRZED następnym RC, wstępna wersja RC 0.9.11-rc2 (versionCode 91102 wg rc.yml), personalizacja po pierwszym RC
unfinished: OCZEKUJE NA URZĄDZENIE (po wydaniu RC, nie teraz): skrypt S00-S12 na boxie, ręczny test pilota (1.1, 1.2), D-pad i wygoda w Szukaj i Mojej liście, ocena obrazu i dźwięku, wydajność (1.8); OTWARTE: zimny start w trybie klawiszowym (przygotowanie danych w oknie startującym nie-dotykowo zawieszało helper click w Robolectric; przyczyna nierozstrzygnięta), BACK ze szczegółów filmu (obecnie pierwszy BACK -> szyna, drugi -> Home; sprzeczność z zasadą cofania o poziom do decyzji właściciela), LEWO z akcji pustej Mojej listy (wchodzi w pole wyszukiwania); skrypt NIE sprawdza BACK ze szczegółów, LEWO z akcji pustej listy ani strzałek w polu wyszukiwania (checklista ręczna), S03 tworzy dostawcę RC-test-<czas> do ręcznego usunięcia; pełne CI tej gałęzi na końcowym SHA do odczytania; issue #12 (Redactor) nie rozpoczęte, czeka plan mikro-PR; RC nie rozpoczęty (bez zmiany wersji, rcbuild, tagu, publikacji)
next_action: odczytać pełne CI na końcowym SHA gałęzi (build z Roborazzi verify, emulator), zdjąć draft i zgłosić gotowość do merge'a; merge tylko za osobną zgodą właściciela; potem plan i mikro-PR issue #12 (testy Redactor najpierw), dopiero po zatwierdzonych scaleniach i weryfikacji RC 0.9.11-rc2 (push rcbuild/0.9.11-rc2 z dev po zielonym CI na tym SHA)
blockers: brak TV-boxa (testy fizyczne po RC); pobieranie zależności w CI bywa zawodne (HTTP 504, timeout Gradle, brak ktlint-cli bez kodu HTTP); odtwarzanie próbki strumieni z listy użytkownika zablokowane polityką sieci środowiska
do_not_touch: main; PR #5 (dev do main); PR #14 (personalizacja po pierwszym RC); lista IPTV (adres i dane dostępu tylko w prywatnym pliku poza repo, nigdy w commitach, logach i raportach); wydawanie RC, rcbuild, tag, publikacja i instalacja APK bez decyzji; produkcyjny UX BACK ze szczegółów bez decyzji właściciela
test: bash -n skryptów tools/tvbox (6 plików) | PASS | working_tree | exit 0 po włączeniu dev; obejmuje ketchup_tvbox_test.sh, analyze_keys.sh, test_analyze_keys.sh, tests/run_tests.sh, tests/fake_adb, tests/fake_aapt
test: ShellCheck 0.11.0 -S warning na skryptach i testach tools/tvbox | PASS | working_tree | 0 uwag, exit 0 (narzędzie tylko w Cloud)
test: bash tools/tvbox/tests/run_tests.sh | PASS | working_tree | 129 z 129 sprawdzeń, 3 min 30 s; atrapy adb i aapt, tylko fikcyjne sekrety; to NIE jest test na boxie
test: python3 -B -m unittest discover -s tools/handoff -p 'test_*.py' | PASS | working_tree | 53 testy, exit 0 (CI tych testów nie uruchamia)
test: CI dev po scaleniu PR #11 | PASS | 1989e9160d8176618dc02c5e4bc1b7f49db8cfc3 | https://github.com/Jack4B/ketchup-player/actions/runs/42498355469 ; build (Roborazzi w trybie record na dev), gitleaks, emulator
test: CI PR #13 na końcowym SHA gałęzi | NOT_RUN | none | uruchamiany po tym wpisie; wynik w raporcie sesji i w PR, nie jest tu przewidywany
test: skrypt na TV-boxie (S00-S12) | NOT_RUN | none | po wydaniu RC; brak urządzenia
test: TV-box (fokus, BACK, Szukaj, Moja lista, LEWO z akcji pustej listy, BACK ze szczegółów, zimny start) | NOT_RUN | none | brak urządzenia; pozycje w docs/TEST-CHECKLIST.md
```
