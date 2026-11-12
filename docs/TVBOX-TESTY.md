# Automatyczne testy RC na TV boxie (ADB z Termuxa)

Skrypt `tools/tvbox/ketchup_tvbox_test.sh` to **jeden samodzielny plik** (dołączany do RC przez `rc.yml`). Instaluje wskazane
APK, wykonuje możliwe testy zdarzeniami D-pad / OK / BACK i buduje **bezpieczną paczkę ZIP** z wynikami do przekazania.
Nie jest zamiennikiem oceny człowieka ani testów na fizycznym pilocie, a wynik testów offline (patrz niżej) nie jest testem na boxie.

## Przygotowanie (raz)
1. W Termuxie: `termux-setup-storage`.
2. **Zależności użytkownika w Termuxie** (wymagane do uruchomienia skryptu). Instalujesz je **sam**: skrypt niczego nie instaluje, a przy
   braku narzędzia kończy z kodem 3 i listą braków:
   `pkg install android-tools coreutils unzip zip gawk sed grep aapt`
   (`aapt` odczytuje pakiet, wersję i aktywność ze wskazanego APK; bez niego instalacja jest zablokowana). To jedyne wymagane narzędzia:
   `adb`, `timeout`, `sha256sum`, `stat`, `base64`, `mktemp`, `head`, `sort`, `cut`, `date`, `tr` (coreutils), `unzip`, `zip`, `awk`, `sed`, `grep`, `aapt`.
   **Nie potrzebujesz** `jq`, `python3` ani ShellChecka: używają ich wyłącznie testy deweloperskie (sekcja „Testy skryptu”).
3. Opcjonalnie prywatna konfiguracja **urządzenia** `~/.ketchup-test.env` (poza repo). Plik **nie jest wykonywany** jako kod;
   czytane są tylko klucze `DEVICE_IP`, `PORT`, `ADB_TARGET`. Wpisy `M3U_URL` i `XTREAM_*` są ignorowane (adres listy podajesz
   tylko w ukrytym monicie):
   ```
   DEVICE_IP="192.168.1.170"
   PORT="5555"
   ```
   Bez konfiguracji skrypt użyje jedynego podłączonego urządzenia ADB; przy zerze lub kilku zapyta o `host:port` (a bez terminala
   zakończy się statusem BLOCKED). Parowania ani uprawnień ADB nie obchodzi: przy `unauthorized` zatwierdź klucz na boxie.

## Uruchomienie RC (prosta instrukcja)
```
cp ~/storage/downloads/player/ketchup_tvbox_test.sh ~/ && sed -i 's/\r$//' ~/ketchup_tvbox_test.sh
bash ~/ketchup_tvbox_test.sh
```
Skrypt zada **dwa monity**: (1) **ścieżkę do pliku APK** (widoczną; spacje, cudzysłowy z wklejenia i `~/` są obsłużone; do 3 prób),
(2) **adres listy M3U z ukrytym wpisywaniem** (nie trafia do historii, argumentów, konfiguracji ani raportu). Ścieżkę APK możesz też
podać jako argument (skrót, najlepiej w cudzysłowie): `bash ~/ketchup_tvbox_test.sh "…/KetchupPlayer-<wersja>-<sha>.apk"`. Zamiast
monitu adresu możesz użyć `--lista-plik PLIK` (pierwsza linia pliku; zalecane `chmod 600`) albo `--bez-listy`. Bez terminala i bez
argumentu skrypt kończy się kodem 2. Wyniki: `Download/KetchupPlayerTest/<data>/` (`report.md`, `results.json`, `ketchup-wyniki-<data>.zip`).
**Do przekazania wystarczy ten ZIP**; zawiera wyłącznie oczyszczone materiały.

### Opcje
| Opcja | Znaczenie |
|---|---|
| `--lista-plik PLIK` / `--bez-listy` | adres listy z pliku / pominięcie scenariuszy z listą |
| `--test-pilota` | opcjonalny ręczny test 5 naciśnięć strzałki w dół na fizycznym pilocie |
| `--stres N` `--ziarno S` | opcjonalnie N losowych **strzałek** (bez OK i BACK), odtwarzalne z ziarnem; maks. 1000 |
| `--reset-dane` | **DESTRUKCYJNE**: czyści dane aplikacji po ręcznym wpisaniu `USUN DANE <pakiet>`; bez potwierdzenia nic nie robi |
| `--diag-lokalne` | zrzuty ekranu i drzewa UI tylko lokalnie w `lokalne/` (nigdy w ZIP; nigdy formularza z adresem) |
| `--urzadzenie H:P`, `--sha-kodu SHA` | adres urządzenia; SHA kodu z notatek wydania (zapisane jako niezweryfikowane) |

## Zasady bezpieczeństwa danych
- **Domyślnie dane aplikacji i ulubione są zachowane.** Brak `pm clear`, brak obniżania wersji (`-d`), brak odinstalowywania przy
  konflikcie podpisu (wtedy BLOCKED z kodem `INSTALL_FAILED:<przyczyna>`). Ustawień globalnych Androida nie zmienia.
- Pakiet, wersja i aktywność pochodzą **ze wskazanego APK**, nie z listy zainstalowanych aplikacji; po instalacji są potwierdzane
  dla aktywnego użytkownika Androida. Błąd instalacji kończy scenariusze aplikacji (nie testuje się starej wersji przez przypadek).
- Pusta Moja lista jest testowana tylko przy potwierdzonym pustym stanie. Ulubiony dodany przez test jest usuwany po teście
  (rozpoznany po miejscu w siatce Filmów); niepotwierdzone sprzątanie to MANUAL_REQUIRED. Dostawcy utworzonego przez import
  (`RC-test-<czas>`) skrypt **nie usuwa**: zrób to ręcznie.
- Bezpiecznik: przed każdym klawiszem sprawdza ADB i pierwszy plan (okno klawiatury ekranowej nad naszą aplikacją jest dozwolone).
  Po utracie połączenia lub wyjściu poza aplikację przerywa wysyłanie klawiszy, a raport powstaje mimo to.
- Każde wywołanie `adb` ma limit czasu. Ctrl+C kończy pracę od razu: raport i ZIP powstają bez zbierania logów, a katalog roboczy jest usuwany.

## Prywatność paczki
Paczka składa się z pól **zbudowanych przez skrypt** (statusy, liczby, kody, kroki) oraz opcjonalnie oczyszczonego logu aplikacji.
- Nie ma w niej surowych zrzutów UI ani zrzutów ekranu. Surowe pliki tymczasowe leżą w prywatnym katalogu roboczym (`chmod 700`)
  i są usuwane także po przerwaniu. Zrzut drzewa UI robiony przy formularzu z adresem jest kasowany od razu po sprawdzeniu pola.
- Oczyszczanie nie opiera się na pełnej ochronie regexami: maski adresów, parametrów `username=`/`password=`/`token=`, ścieżek
  `host/użytkownik/hasło/id`, IP i MAC **plus** dosłowna maska składników wpisanego adresu (także kodowanych: `%XX`, podwójnie,
  base64 par, `\/` w JSON). Potem **kontrola wycieku** (również po rozkodowaniu `%XX`); co jej nie przejdzie, jest wykluczane z paczki,
  a scenariusz dostaje kod przyczyny `REDACTION_FAILED` przy istniejącym statusie (PASS zmienia się na BLOCKED).
- Istniejący `Redactor` aplikacji nie jest tu podstawą bezpieczeństwa (znana luka: issue #12).
- Znane ograniczenie: tekst wpisywany przez `adb shell input text` przechodzi przez argumenty procesu `adb` na telefonie (widoczne
  w liście procesów tego użytkownika Termuxa przez czas wpisywania). Jest przekazywany w pojedynczym cudzysłowie, nie jako kod.
- Logi aplikacji: `logcat` tylko z PID aplikacji (poziom WARN i wyżej), blok `crash` z naszym pakietem i tag `KeyGuard`; nie zbieramy
  całych logów urządzenia. Po restarcie aplikacji stare PID-y giną z widoku, dlatego dodatkowo czytamy `dumpsys activity exit-info`.
  **Brak logów nie jest dowodem braku awarii.**

## Statusy i raport
`PASS`, `FAIL`, `BLOCKED` (nie da się wykonać lub ocenić: brak narzędzia, ADB, obserwowalności, sieci, źródła), `SKIPPED`
(nie dotyczy lub nie wybrano), `MANUAL_REQUIRED` (potrzebna decyzja człowieka). **Brak dowodu nigdy nie jest PASS.** Kody wyjścia:
0 brak FAIL i BLOCKED, 1 jest FAIL, 3 jest BLOCKED (bez FAIL), 130 przerwanie.

| ID | Scenariusz |
|---|---|
| S00 | środowisko i ADB (model, Android, rozdzielczość, aktywny użytkownik) |
| S01 | APK: SHA-256, pakiet/wersja/aktywność z `aapt`, instalacja bez `-d`, potwierdzenie po instalacji |
| S02 | start aplikacji, pierwszy plan, rozpoznanie kreatora albo Home |
| S03 | import dużej listy (czas, „Kanały: N”); wpisanie adresu przez ADB jest krokiem konfiguracji, nie testem pilota |
| S04 | sekcje (Na żywo, Filmy, Seriale, Moja lista, Szukaj, Ustawienia) osiągane **pilotem** z szyny |
| S05 | Szukaj: GÓRA do pola, fraza z istniejącego tytułu zawęża karty, fraza bez wyników, BACK na szynę (tytułów nie zapisujemy) |
| S06 | Moja lista pusta (tylko gdy lista jest naprawdę pusta), BACK na szynę |
| S07 | ulubione: dodanie, restart (`force-stop`), trwałość, usunięcie własnego wpisu; liczby przed/po |
| S08 | odtwarzanie próbki (kanał, film, odcinek): dowody (audio, proces, brak panelu błędu, warstwy powierzchni), BACK zatrzymuje; **ocena obrazu = MANUAL_REQUIRED** |
| S09 | ręczny test pilota: osobno utrata naciśnięć (LOSS), duplikaty (DUP) i opóźnienia (LAG) z porównania: deklaracja, sprzęt (`getevent`), aplikacja (`KeyGuard`) |
| S10 | crashe/ANR/OOM z `exit-info`, PID |
| S11 | opcjonalny stres strzałkami |
| S12 | logi aplikacji w paczce (PASS, SKIPPED albo BLOCKED `REDACTION_FAILED`) |

Fokus czytamy z `focused="true"` w świeżym zrzucie UI. **Compose nie musi ujawniać każdego celu fokusu**: gdy fokusu nie widać, wynik to
`BLOCKED NOT_OBSERVABLE`, nie błąd aplikacji i nie PASS. Błędy źródła/sieci (`SOURCE_ERROR`, `NETWORK_DOWN`) są rozdzielone od
`APP_CRASH` i `NOT_OBSERVABLE`.

### Test pilota (S09)
Wymaga builda **debug** (tag `KeyGuard`) i `getevent`. Brak jednego z nich = BLOCKED (`APP_DIAG_MISSING`, `GETEVENT_UNAVAILABLE`), nie sukces.
Za mało zdarzeń sprzętu = MANUAL_REQUIRED (`TOO_FEW_PRESSES`). Kompletna diagnostyka pokazująca utratę = FAIL (`LOST_BEFORE_APP`,
`DROPPED_BY_FILTER`). Duplikaty przyjęte przez aplikację = FAIL (`DUPLICATES_ACCEPTED`: zwiększ Czułość pilota w Ustawieniach,
Odtwarzanie). ADB nie odtwarza duplikatów fizycznego pilota; scenariusz tego nie udaje.

## Diagnostyka listy (telefon, Termux, bez ADB)
`tools/tvbox/ketchup_list_diag.sh` sprawdza z telefonu, jak panel odpowiada na pobranie listy (np. gdy aplikacja pokazuje HTTP 504, a inne
odtwarzacze działają). To samodzielny plik oparty wyłącznie na `curl`; **nie instaluje ani nie uruchamia aplikacji**.

Skąd wziąć plik: **od następnego RC** skrypt jest w wydaniu obok `ketchup_tvbox_test.sh`. W RC 0.9.11-rc2 go nie ma: pobierz
`tools/tvbox/ketchup_list_diag.sh` z repozytorium (gałąź `dev` po scaleniu; w GitHubie: plik → „Download raw file”) i skopiuj do
`Download/player/`.

Przygotowanie (raz): `pkg install curl coreutils gawk sed grep gzip`, `termux-setup-storage`.
```
cp ~/storage/downloads/player/ketchup_list_diag.sh ~/ && sed -i 's/\r$//' ~/ketchup_list_diag.sh
bash ~/ketchup_list_diag.sh
```
Skrypt zapyta o **adres listy z ukrytym wpisywaniem** (nie trafia do historii, argumentów procesów, raportu ani plików poza chwilowym
plikiem `chmod 600`, który jest usuwany). Zamiast monitu możesz użyć `--lista-plik PLIK` (pierwsza linia pliku). Opcje: `--przerwa S`
(domyślnie i minimum 30 s), `--max-czas S` (limit czasu jednego żądania, domyślnie 300 s), `--bez-wariantow` (tylko żądanie bazowe).

Żądania (jedno bazowe i 7 wariantów; przed każdym kolejnym przerwa co najmniej 30 s, a gdy serwer poda `Retry-After`, np. 120, to tyle
sekund, maks. 900):

| id | żądanie |
|---|---|
| B | GET `get.php` z nagłówkami jak w aplikacji (User-Agent `okhttp/4.10.0`, `Accept-Encoding`, `Connection`, bez `Accept`, HTTP/1.1) |
| V1 | GET `player_api.php?action=get_live_categories` (czy API Xtream odpowiada, gdy `get.php` daje 504) |
| V2 | POST `get.php`, w ciele `username`, `password`, `type` (`application/x-www-form-urlencoded`, bez parametrów w adresie) |
| V3 | POST `player_api.php`, w ciele `username`, `password`, `action=get_live_categories` |
| V4 | GET `get.php` z User-Agent VLC |
| E1 | GET `player_api.php` bez `action`, jak IPTV Extreme: UA `okhttp/4.10.0`, HTTP/1.1, tylko cztery nagłówki (User-Agent, Host, Connection, Accept-Encoding) |
| E2 | GET `player_api.php?action=get_live_categories`, ten sam zestaw co E1 |
| E3 | jak E2, ale z dotychczasowym UA aplikacji (OTT Navigator) i `Accept: */*`, dla porównania |

Curl nie pozwala ustawić kolejności nagłówków: `Host` zawsze idzie pierwszy. Zestaw w E1/E2 jest ten sam, co w aplikacji i IPTV Extreme.

Warianty V1-V4 wymagają adresu `.../get.php?username=...&password=...`; dla innych adresów skrypt wykona tylko żądanie bazowe. Dane do ciała
POST skrypt bierze z tego adresu. Po przekierowaniu curl zachowuje POST tylko przy 307/308 (przy 301/302 zamienia na GET, jak OkHttp).

Wynik: `Download/KetchupPlayerTest/diag-<data>/diag.md` (tabela: status HTTP, wersja, czasy DNS/połączenia/pierwszego bajtu/całości,
rozmiar, liczba przekierowań, typ i kodowanie odpowiedzi, `Retry-After` jako liczba, tak/nie dla obecności pośredników `via`, `cf-ray`,
`x-cache`, `x-served-by`, liczba pozycji M3U albo kategorii JSON oraz krótkie wskazówki). **Raport nie zawiera** adresu, hosta, loginu,
hasła ani nagłówków `Server` i `Set-Cookie`; po złożeniu jest czyszczony literalnie i z wariantami zakodowanymi (procentowo, ponownie
zakodowanymi i zdekodowanymi), a przy niepowodzeniu czyszczenia skrypt nie zapisuje pliku (`REDACTION_FAILED`). Do przekazania wystarczy
`diag.md`; przejrzyj go przed wysłaniem. Czas działania: kilka minut (przerwy). Ctrl+C przerywa i zapisuje raport z tego, co zebrano.

## Testy skryptu (offline, bez urządzenia): zależności tylko deweloperskie
Te narzędzia są potrzebne **wyłącznie** do uruchamiania testów skryptu na komputerze lub w Cloud, nie na boxie ani w Termuxie użytkownika:
`bash` 4+, `jq` (walidacja JSON), `python3` (wysłanie prawdziwego Ctrl+C w teście), `zip`/`unzip`, `awk`, `sed`, `grep`, `coreutils`
oraz opcjonalnie **ShellCheck** (`SHELLCHECK_BIN` albo w `PATH`; bez niego kontrola jest raportowana jako NOT_RUN).
```
bash tools/tvbox/tests/run_tests.sh          # atrapa adb/aapt, tylko fikcyjne sekrety; ShellCheck, jeśli jest (SHELLCHECK_BIN)
bash tools/tvbox/test_analyze_keys.sh        # analizator pilota na próbkach + zgodność z wbudowaną kopią
bash tools/tvbox/tests/run_diag_tests.sh     # diagnostyka listy: atrapa curl, fikcyjne dane (bez sieci)
```
Obecne CI tych testów **nie uruchamia**. Pokrywają: błąd instalacji (konflikt podpisu, obniżenie wersji, timeout, zły pakiet,
niezgodna wersja), brak narzędzi i danych, utratę pierwszego planu i ADB, Ctrl+C, nieodświeżone UI, wpisywanie bez interpretacji
w prawdziwej powłoce `sh` atrapy (apostrof, spacje, `&`, `;`, `$()`, backtick, `|`, cudzysłów, ukośnik; z kontrolą ujemną dowodzącą, że wykonanie
zostałoby wykryte), dwa monity (ścieżka APK, ukryty adres), plik konfiguracji bez wykonywania, brak adresu w raporcie/ZIP/środowisku, oczyszczanie kodowanych wariantów i
`REDACTION_FAILED`, JSON/Markdown, brak obserwowalności fokusu. **To nie jest test na boxie**: pełne scenariusze UI (Szukaj, Moja
lista, odtwarzanie) są sprawdzone tylko we fragmentach modelu; dopasowanie do prawdziwego układu ekranów nastąpi na pierwszym RC.

## Stan paczki
Paczka narzędziowa jest osobna od PR #11 (D-pad w Szukaj i Moja lista). `docs/HANDOFF.md` świadomie **nie** jest tu zmieniany, żeby nie
nadpisać stanu równoległej pracy; stan tej paczki opisuje ten dokument i opis jej PR. Do wykonania po wydaniu RC: przebieg na
rzeczywistym boxie, strojenie nawigacji (Ustawienia → Dostawcy, Szczegóły, odcinki) i odczyt wyniku dla duplikatów fizycznego pilota.
