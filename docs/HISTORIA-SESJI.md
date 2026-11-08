# Historia sesji

Krótkie wpisy dopisywane chronologicznie (nowe na końcu); starych nie przepisujemy. Aktualny stan: `docs/HANDOFF.md`.

## 2026-11-07 (Europe/Warsaw): przekazywanie stanu sesji

- Gałąź sesji `ccr-a5500ce0-233anr` przesunięta fast-forward do `origin/feat/v5.1-mockups-docs-from-dev` (`677cdde`, PR #8 otwarty); bez publikacji.
- Dodano `docs/HANDOFF.md`, tę historię, walidator `tools/handoff/` (testy `unittest`, najpierw RED), komendy `/wczytaj-projekt` i `/domknij-projekt`; poprawiono `/next-step`, `/verify`, `ketchup-workflow`, `ketchup-pamiec`, `CLAUDE.md`.
- Wynik: 53 testy walidatora PASS lokalnie (`working_tree`); gradle NOT_RUN (brak dostępu do wtyczki Android); CI na tej paczce jeszcze nie było.
- Po przeglądzie użytkownik zatwierdził commit, push na `ccr-a5500ce0-233anr` i PR do `dev` (bez merge). Wynik CI na końcowym SHA: w raporcie sesji i w PR.
- Następny krok: decyzja użytkownika o kolejności merge (najpierw PR #8, potem nowy PR).

## 2026-11-07 (Europe/Warsaw), domknięcie: scalenie PR #10, #8, #9

- PR #10 (deterministyczne zrzuty Roborazzi: zegar UI, Home, 22 wzorce, `--continue` i raporty JUnit) scalony do `dev` zwykłym merge'em po dwóch wykonaniach verify na `f441650`.
- `dev` po #10 i po #8: CI zielone (tryb record), 0 zmian w PNG Roborazzi; bot dopisał wyłącznie `docs/qa/emulator/`.
- PR #8 zaktualizowany o `dev` (konflikt `ci.yml` rozwiązany na korzyść `dev`: gałęzie `feat/*` przechodzą prawdziwy verify), CI zielone (verify, 298 testów), scalony do `dev`.
- PR #9: gałąź zaktualizowana o `dev` bez konfliktów, własny diff = 11 plików handoff, 53 testy walidatora PASS na `f48d447`; HANDOFF zaktualizowany przed końcowym CI.
- Następny krok: nowa sesja na `dev`, `/wczytaj-projekt`; kolejna funkcja nie rozpoczęta.

## 2026-11-08 (Europe/Warsaw): D-pad w Szukaj i Moja lista (PR #11, w toku)

- Gałąź sesji przesunięta na `dev` (`338800b`, fast-forward); 53 testy walidatora PASS na tym SHA; bot zmienił tylko `docs/qa/emulator/`.
- Draft PR #11: testy D-pad dla trybów Moja lista i Szukaj (moduł `feature:vod`), scenariusz E2E i rozszerzony audyt układu; kod produkcyjny bez zmian.
- Wyniki CI: asercje fokusu czerwone, bo `FocusItem` nie pokazuje fokusu w drzewie semantyki Robolectric (tryb wejścia zostaje Touch); przyczyna nieustalona, sonda `FocusItemReproTest` dopisana, wynik czeka na działający build. Strzałki w polu wyszukiwania zostają w polu.
- Analiza dużej listy M3U użytkownika (prywatny plik poza repo): parser aplikacji na JVM bez błędów; odtwarzanie nie sprawdzone (polityka sieci). `Redactor` nie maskuje adresów `host/użytkownik/hasło/id`.
- Blokada: pobieranie zależności w CI (Gradle HTTP 504, potem brak ktlint-cli). Bez zmian w CI. Następny krok: powtórzyć CI, gdy pobieranie działa.
- Dopisane: syntetyczny katalog o cechach dużej listy (fixture i scenariusz Room, Szukaj, ulubione; niewykonane), luka Redactor zgłoszona jako issue #12. CI na `50b979c`: Gradle 504 na pierwszym kroku; bez dalszych prób.
