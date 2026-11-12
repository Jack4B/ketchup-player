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

## 2026-11-08 (Europe/Warsaw), scenariusz pilota E2E po przygotowaniu danych (PR #11, w toku)

- Biegi 74de962 → 5cc19cc: strażnik skrócił zawieszenie (OOM po ok. 7 min) do 60 s i wskazał miejsce: helper `click` w przygotowaniu danych w oknie startującym w trybie nie-dotykowym. Przyczyna **nierozstrzygnięta** (zapis jako osobne ustalenie); hipotezy o wstrzykiwaniu dotyku nie traktujemy jako ustalonej.
- Scenariusz pilota przerobiony: dane w trybie dotyku, potem okno w trybie klawiszowym (`requestFocusFromTouch`), kontrola okna i Compose kanarkiem, przywrócenie trybu w `finally`; zakres opisany jako obsługa pilotem po przygotowaniu danych, nie dowód zimnego startu. Na 5cc19cc przełączenie potwierdzone, ścieżki trzech sekcji pilotem przechodzą (509/510).
- Odkrycie: BACK ze szczegółów filmu przenosi fokus na szynę, szczegóły zostają, drugi BACK prowadzi na Home (`Shell.BackHandler`, `DetailsRoute` bez `onBack`); test zapisuje obecne zachowanie, decyzja o zmianie należy do właściciela (checklista box).
- Usunięto sondę `sondaFokusuPoWyborzeSekcji` i wydruki; zostaje strażnik i znaczniki etapów bez wydruków. Lokalne `detekt-cli` i `ktlint-cli` (te same wersje co CI) wyłapały błędy lintu przed pushem.

## 2026-11-08 (Europe/Warsaw), domknięcie PR #11 do review

- Pełne CI na `3f32752` zielone (build z 509 testami, Roborazzi verify, Lint, buildy, gitleaks, emulator); log emulatora nie wypisuje wyników per scenariusz, dowodem są artefakty i kod wyjścia skryptu.
- Wycofane tymczasowe `--info --stacktrace` z kroku Spotless + Detekt (zgoda użytkownika); `networkTimeout` 60000 zostaje; workflow względem `dev` bez zmian.
- Otwarte (nie zamknięte): zimny start w trybie klawiszowym, BACK ze szczegółów (test pilnuje obecnego zachowania, nie akceptuje go), LEWO na pustej Mojej liście, testy fizycznego boxa. Punkty planu wymagające boxa bez odhaczenia.

## 2026-11-08 (Europe/Warsaw), poprawki po końcowym przeglądzie PR #11

- Pełne CI na `85494a7` zielone (junit-unit 509 testów, junit-roborazzi-verify 316, 0 failures i 0 skipped, emulator); przegląd względem `dev` bez zastrzeżeń blokujących.
- Dwa drobiazgi: (1) kanarek `ComposeView` w `inRemoteMode` sprzątany idempotentnie w `finally` także po błędzie (tryb najpierw, potem kanarek, pierwotny wyjątek nie jest maskowany), z testem wymuszonego błędu; (2) zdanie punktu 1.3 w `PLAN-1.0.md` zaktualizowane (testy modułowe i scenariusz E2E istnieją, ograniczenia i testy boxa wymienione), checkbox nieodhaczony.

## 2026-11-08 (Europe/Warsaw), scalenie PR #11 i aktualizacja PR #13

- PR #11 (D-pad w Szukaj i Moja lista, audyt układu) scalony do `dev` (`1989e91`); CI push na tym SHA zielone (build, gitleaks, emulator); bot dopisał zrzuty emulatora (`ba6c2bc`). Punkt 1.3 nie zamknięty: zimny start w trybie klawiszowym, BACK ze szczegółów, LEWO na pustej Mojej liście i testy boxa pozostają otwarte.
- PR #13 (skrypt testów RC na boxie): przegląd tylko do odczytu (bash -n, ShellCheck, 129 testów offline na atrapach), następnie zwykły merge `dev` do gałęzi (bez rebase i force-push; konfliktów brak, skrypt i dokumentacja bez zmian). Skrypt nie sprawdza BACK ze szczegółów, LEWO z akcji pustej listy ani strzałek w polu wyszukiwania; nie jest zamiennikiem testu na boxie.
- Decyzje właściciela: issue #12 (maskowanie `host/użytkownik/hasło/id`) naprawiane przed następnym RC (najpierw testy `Redactor`), wstępnie RC `0.9.11-rc2`; wydanie dopiero po zatwierdzonych scaleniach i weryfikacji; personalizacja (PR #14) po pierwszym RC.

## 2026-11-08 (Europe/Warsaw), scalenie PR #13 i naprawa issue #12 (PR #15, w toku)

- PR #13 (skrypt testów RC na boxie) scalony do `dev` zwykłym merge'em (`12e41f8`) po sprawdzeniu live (head, baza, kontrole, brak konfliktów i uwag); skrypt z 1155 liniami trafi do następnego RC przez `rc.yml`.
- Issue #12 w osobnej gałęzi `fix/redactor-url-credentials` (PR #15): testy najpierw, potem szeroka reguła maskowania w kontekście URL (userinfo, parametry, ścieżki z prefiksem i bez, zagnieżdżone adresy usuwane w całości), wspólne bezpieczne komunikaty błędów (`ErrorText`, `SafeErrorMessage`) w miejscach wymienionych w zgłoszeniu oraz odczyt `lastError` z maskowaniem. Fałszywe trafienia dopuszczone przez właściciela i opisane w testach i PR.
- Granice zapisane jawnie: logi bibliotek i systemu poza zasięgiem, adres bez schematu i JSON niemaskowane, stare wpisy `lastError` w bazie bez czyszczenia masowego. RC `0.9.11-rc2` (versionCode 91102) zatwierdzony wstępnie, nie wydany.
- CI PR #15: pierwszy bieg padł na kompilacji `provider:m3u` (supertyp `SafeErrorMessage` niewidoczny; `core:common` w `core:network` zmieniony na `api`), drugi zielony (522 testy jednostkowe, 321 Roborazzi verify, emulator). Wykryto istniejącą lukę: `ci.yml` nie uruchamia testów JVM `core:common`, `core:network`, `epg:xmltv`, `provider:m3u`, `provider:xtream`; zmiana CI czeka na decyzję, a kontrakt Redactora ma osobny test w `core:data`. CI dev po scaleniu PR #13 (`12e41f8`) zielone.
- PR #18 (priorytet po potwierdzeniu dostawcy): metoda żądania GET/POST per lista (kreator, Ustawienia -> Sieć, Room v8). Zrzuty Roborazzi czterech zmienionych ekranów wzięte z artefaktu CI po weryfikacji (zasada: nowa wersja ekranu = nowe wzorce z CI). Poprawki testów UI po pierwszych biegach: stała ucięta przez edycję pliku, kolejność wierszy w teście Sieci, fokus w trybie dotyku Robolectric (wyjście przez requestFocusFromTouch), przewinięcie do wyników w leniwej liście.
- PR #15 (issue #12) scalony (fc1d30e) i wydane testowe 0.9.11-rc2 z tagu rc-0.9.11-rc2 (push rcbuild na commit bota z [skip ci] nie uruchamia rc.yml; sesja ma 403 przy pushu tagów; dwa pierwsze biegi RC padły na 504 przy pobieraniu pluginu Kotlin, trzeci przeszedł). Zgłoszenie z telefonu: 504 przy imporcie get.php, brak OSD/dotyku w odtwarzaczu, oceny IMDb nie dla wszystkich filmów. Plan zaakceptowany: PR #16 (diagnostyka listy: skrypt curl bez ADB), równolegle czyszczenie tytułów i ocen; poprawka 504 dopiero po diag.md, dotyk po nich.
- PR #17 (czyszczenie tytułów VOD i dopasowanie ocen IMDb): wzorce nazw z paneli (rok bez nawiasów, [rok], .rok., sufiksy, platformy, [ENG]/DUAL) wcześniej dawały 0% trafień w indeksie; zawężony shortTag przestał obcinać "Saw:"/"Rio -"/"Ice:"/"Up:"; bez roku wybieramy tylko wyraźnego lidera. Pomiar na syntetycznych nazwach z indeksu (nie prawdziwa lista). Komunikat commita z dosłownym znacznikiem pomijającym CI nie uruchamia biegu (PR #16 stracił tak jeden bieg): w komunikatach nie wolno go cytować.

## 2026-11-09 (Europe/Warsaw): analiza 504 (PR #19) i dotyk w odtwarzaczu (PR #20)

- rc3 (tag `rc-0.9.11-rc3` na `2e3e35e`, bieg 42619214582) przyjęty; korekta wpisu o rc2: rc2 jednak wyszedł (bieg 42569216056, trzecia próba).
- PR #19: notatka `docs/analiza-504-playery.md` (OwnTV, StreamVault, trzy mniejsze playery; hipotezy 504, plan PR 1b); tylko dokumentacja.
- PR #20: dotyk w odtwarzaczu (telefon, minimalnie). Pierwszy bieg CI padł na brakujących importach `click`/`swipe` w teście, drugi na brakujących zrzutach bazowych Roborazzi (zrzuty z artefaktu, obejrzane). Merge tylko po zgodzie właściciela.

## 2026-11-09 (Europe/Warsaw), po przechwycie IPTV Extreme: Xtream przez player_api.php i UA (PR #21)

- Przechwyt właściciela (PCAPdroid): w IPTV Extreme lista działa; wszystkie zapytania `GET player_api.php` z czterema nagłówkami i UA `okhttp/4.10.0`, kolejność: konto, live kategorie i strumienie, vod kategorie i strumienie, seriale kategorie i seriale. Nasz UA OTT Navigator dostaje 520 (Cloudflare).
- PR #21: domyślny UA okhttp/4.10.0 i cztery nagłówki w tej kolejności (test z prawdziwym OkHttp), UA odtwarzacza VLC/3.4.3, link get.php z loginem jako konto Xtream (get.php zapasowo), Room `providers.userAgent` (8 -> 9), własny UA w SecretStore, warianty E1-E3 w diagnostyce, scenariusz emulatora z panelem odpowiadającym 520 na obce nagłówki.
- CI: spotless/detekt (formatowanie i długość linii), oczekiwania testów po zmianie nagłówków i pełnej sekwencji import, 9.json i zrzuty Roborazzi z artefaktu CI (obejrzane).
- Emulator (nowy scenariusz panelu) znalazł błąd: treść odpowiedzi tekstowych (`fetch`, `fetchWithBody`) była czytana na wątku głównym, więc import konta Xtream kończył się `NetworkOnMainThreadException`. Test z udawanym wątkiem głównym czerwony przed poprawką, zielony po; poprawka: `withContext(Dispatchers.IO)`.
- Ten sam scenariusz znalazł drugi błąd: źródło EPG zwracające nie-XML (JSON z `xmltv.php`) kończyło aplikację `XmlPullParserException` (nie `IOException`). Test czerwony przed poprawką; teraz porażka źródła EPG.
- Poprawki po przeglądzie właściciela (PR #21): własny UA w kreatorze i w Sieci (per lista, SecretStore), UA listy w EPG i osłona przy przekierowaniu na inny host, migracja starego DataStore, przenoszenie ulubionych i filmów po numerze strumienia przy zmianie trybu M3U ↔ API (seriale zostają pod starymi id), kontrolowany fallback do get.php z Retry-After i cooldownem, odtwarzacz: Accept-Language i początkowy Range tylko dla bezpośredniego .ts, testy przez prawdziwą fabrykę Media3. Działanie na prawdziwym panelu czeka na test właściciela.

## 2026-11-09 (Europe/Warsaw): aktualizacja gałęzi dotyku (PR #20) po PR #21

- PR #21 (poprawka importu: player_api.php, UA, tożsamość kanałów) scalony do `dev` (`d8091a1`, CI push zielone); `dev` ma jeszcze commit bota `4acd3fb` (tylko `docs/qa/emulator`).
- `feat/player-dotyk` zaktualizowana zwykłym merge `origin/dev` (bez rebase i force-push). Konflikty tylko w `docs/HANDOFF.md` i `docs/HISTORIA-SESJI.md`, rozwiązane z zachowaniem informacji obu stron; kod i testy bez konfliktów. Dotyk nie jest scalony, merge PR #20 wymaga osobnej zgody.

## 2026-11-12 (Europe/Warsaw): atrapa odtwarzacza (PR #22) i poprawki z testów rc4

- PR #22: poprawka atrapy odtwarzacza (wynik `play()` osobno od buforowania) do `dev`; ta sama poprawka w PR #14. CI obu zielone, bez merge.
- Poprawki rc4 na gałęzi `fix/rc4-playlisty-osd` (od `dev`, nie od #14): przycisk Wstecz w OSD dotykowym odsunięty od pasków systemu, edycja zapisanej listy bez duplikatu i bez pokazywania danych dostępu, grupy i kolejność kanałów związane z listą, wybór listy w Na żywo, Filmach i Serialach.
- Wniosek z testów: wstrzyknięcie `WindowInsets` przez widok w Robolectric nie dociera do Compose; odsunięcie testujemy parametrem `insets` komponentu. `upsert` (REPLACE) wiersza listy kasowałby kaskadowo kanały i VOD, dlatego edycja idzie przez `UPDATE`.

