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

## 2026-11-08 (Europe/Warsaw), diagnoza fokusu w Robolectric (PR #11, w toku)

- Bieg `cdf8952` (po jednym zatwierdzonym ponowieniu i poprawce sondy) wykonał 517 testów: 507 PASS, 10 FAIL; scenariusz syntetycznego katalogu PASS. Dane z sond: w Robolectric tryb wejścia zostaje Touch, semantyka `Focused` elementów `FocusItem` jest w nim pusta, choć OK klika właściwy element, a DÓŁ przenosi fokus; w trybie klawiszowym semantyka pokazuje fokus. Wniosek: ograniczenie środowiska testowego, kod produkcyjny bez zmian. Poprzednia hipoteza („FocusItem nie bierze fokusu”) obalona (pierwsza sonda nie opakowała treści w `KetchupTheme`).
- Poprawki testów: `FocusItemTest` zamiast sondy, tryb klawiszowy w testach modułowych (`SideEffect`, z kontrolą), scenariusze pilota E2E z oknem w trybie nie-dotykowym (`TvBoxRule` + `@StartsInRemoteMode`, `Instrumentation.setInTouchMode`, kontrola `requireRemoteMode`); wcześniejsza wersja z refleksją (`bcf8852`) zastąpiona. Asercji nie osłabiano.
- `bcf8852`: bieg 42476586646 padł na pobieraniu Gradle (timeout 10000 ms), testów nie wykonano. Następny krok: jeden bieg CI na końcowym SHA i odczyt JUnit; potem usunięcie sond.
- `676cf21`: bieg 42477518536 znów padł na pobieraniu Gradle (timeout 10000 ms), testów nie wykonano. Użytkownik zatwierdził jedną zmianę poza zakresem testów: `networkTimeout` 10000 -> 60000 w `gradle-wrapper.properties` (uzasadnienie: powtarzające się timeouty 10 s; nie naprawia HTTP 504). Jeden automatyczny bieg CI na tym SHA.

## 2026-11-08 (Europe/Warsaw), poprawki testów i diagnostyka OOM (PR #11, w toku)

- Bieg `6844d2f` (diagnostyczny `--info --stacktrace` w Spotless): Gradle, Spotless/ktlint i Detekt przeszły (pierwotny wyjątek ktlint nieuchwycony), 505 z 511 testów PASS. `FocusItemTest` 4/4 i scenariusz syntetycznego katalogu PASS; ścieżki Szukaj w module PASS. Czerwone: 3 testy Mojej listy (błędne oczekiwanie pierwszego plakatu: kolejność wg `addedMs`, nie wg listy), test LEWO na pustej liście (LEWO z akcji wchodzi w pole, strzałki zostają w polu) i 2 scenariusze pilota E2E z `OutOfMemoryError`.
- `06961d2`: jawna kolejność Mojej listy (czasy 500/300/100, kolejność wejściowa inna niż w siatce) i tożsamość elementu reagującego na OK; test trasy LEWO do pola i BACK na szynę (wygoda do oceny na boxie, dopisane do checklisty); znaczniki etapów, kontrola trybu okna i strażnik (60 s bez etapu: ostatni etap, stos wątku, przerwanie) w scenariuszach pilota; usunięte sondy modułu; `TvBoxRule` przywraca tryb dotyku. Bez zmian produkcji, sterty i trace.
- Następny krok: odczyt JUnit z biegu na SHA tego wpisu (KROK/STRAŻNIK), minimalna poprawka po ustaleniu przyczyny OOM, potem usunięcie sond i flag CI.

