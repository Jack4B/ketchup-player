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
updated_at: 2026-11-12T14:20:00+01:00
status: blocked
branch: dev
pr: none
base_sha: e2855badf354ae510a4b6b308464300773d3c58b
verified_code_sha: 81fac3b1b4f830ce63c0ad0b0d246ade53eb9c42
dirty_tree: clean
dirty_files: none
outside_handoff_changes: none
task_state: awaiting_device
plan_point: scalenie za zgodą właściciela w kolejności PR #22, #23, #14 (PLAN-FINALNY Krok 9: personalizacja MVP; poprawki z testów rc4); stan dev po scaleniu
done: PR #22 (atrapa odtwarzacza: wynik play() w osobnym obiegu pętli) scalony jako d740d43d9e77f0b1ef2e8082aa543a48794a0ba7; PR #23 (rc4: przycisk Wstecz w OSD z odsunięciem od pasków systemu, edycja zapisanej listy, grupy i pozycje związane z listą, wybór listy w Na żywo, Filmach i Serialach) scalony jako 2a7b74fcabd286f866fb3f3608a5570290a9aea1; PR #14 (personalizacja MVP etapy A+B+C, Room 9 do 10) scalony jako 81fac3b1b4f830ce63c0ad0b0d246ade53eb9c42 po scaleniu dev do gałęzi (0ee53a1, bez przepisywania historii; konflikty FakePlayerModule, PlayerViewModel, strings.xml Ustawień, DESIGN-LOG i HISTORIA-SESJI rozwiązane z zachowaniem obu funkcji); HEAD dev e2855badf354ae510a4b6b308464300773d3c58b (po 81fac3b tylko commit bota ze zrzutami emulatora)
unfinished: testy na urządzeniach po stronie właściciela: telefon z Androidem 15 (przycisk Wstecz w OSD pod paskiem stanu i wycięciem aparatu, dotyk) i TV-box (wybór listy pilotem przy dwóch listach, edycja listy, pasy personalizacji na Home, ekran Personalizacja, pamięć i płynność przy dużym katalogu, prawdziwe EPG); aktualizacja na wierzch poprzedniej wersji z danymi (automatyczna migracja Room 9 do 10 na urządzeniu, zachowanie list, ulubionych, Mojej listy i postępu); przycisk „nie lubię” bez UI; obserwowanie rozgrywek
next_action: właściciel: test na telefonie z Androidem 15 i TV-boxie, w tym aktualizacja na wierzch z migracją Room 9 do 10; RC tylko na decyzję właściciela
blockers: brak urządzeń (telefon z Androidem 15, TV-box) w sesji Cloud
do_not_touch: main; PR #5; workflow wydania, tagi i wydania rc; rcbuild, publikacja i instalacja APK bez decyzji; lista IPTV użytkownika i pliki PCAP; logika failoveru; kryteria wymagające fizycznego urządzenia (nie odhaczać)
test: CI push dev po PR #22 (build, gitleaks, emulator) | PASS | d740d43d9e77f0b1ef2e8082aa543a48794a0ba7 | https://github.com/Jack4B/ketchup-player/actions/runs/43207338517 (bieg #418)
test: CI push dev po PR #23 (build, gitleaks, emulator) | PASS | 2a7b74fcabd286f866fb3f3608a5570290a9aea1 | https://github.com/Jack4B/ketchup-player/actions/runs/43212034452 (bieg #420)
test: CI pull_request PR #14 po scaleniu dev (build, gitleaks, emulator) | PASS | 0ee53a11752171f235dd81e1f937e2cbfbfe363c | https://github.com/Jack4B/ketchup-player/actions/runs/43212349794 (bieg #422)
test: CI push dev po PR #14 (build, gitleaks, emulator; w build migracja Room 9 do 10 na JVM) | PASS | 81fac3b1b4f830ce63c0ad0b0d246ade53eb9c42 | https://github.com/Jack4B/ketchup-player/actions/runs/43218278622 (bieg #423)
test: telefon Android 15 (OSD dotykowe, przycisk Wstecz pod paskiem stanu) | NOT_RUN | none | brak urządzenia; po stronie właściciela
test: TV-box (pilot: wybór listy, edycja listy, personalizacja, wydajność, prawdziwe EPG) | NOT_RUN | none | brak urządzenia; po stronie właściciela
test: aktualizacja na wierzch z migracją Room 9 do 10 na urządzeniu (telefon i TV-box) | NOT_RUN | none | brak urządzenia; na JVM migracja z zachowaniem danych sprawdzona testem core:database
```
