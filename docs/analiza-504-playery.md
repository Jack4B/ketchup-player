# Analiza: jak otwarte playery pobierają listę IPTV (a Ketchup dostaje 504)

Stan: 9.11.2026. Tylko odczyt cudzego kodu i opis zachowania; **zero zmian w kodzie aplikacji**. Notatka nie jest
specyfikacją wiążącą (to `docs/KONSPEKT.md`), tylko podstawą do decyzji o PR 1b.

## 1. Wniosek w pięciu zdaniach

1. Oba playery, które sięgają po listę „get.php”, **nie pobierają jej jako jednego pliku M3U**. OwnTV rozpoznaje
   w linku `get.php?username=…&password=…` konto Xtream, sprawdza je tanim zapytaniem do `player_api.php`
   i ładuje katalog przez `player_api.php` GET (kategorie, potem strumienie; przy błędzie wielkiej odpowiedzi osobno
   per kategoria). StreamVault robi to samo (rozbija link na serwer, login i hasło).
2. Żaden z nich nie używa POST. Ketchup w rc3 dalej wysyła jedno wielkie żądanie `get.php` (GET albo POST), które na
   panelach za reverse proxy najłatwiej kończy się 504 (proxy ucina generowanie całej listy).
3. Główna kandydatka do poprawki: **import konta Xtream przez `player_api.php` zamiast `get.php`**, z pobieraniem
   per kategoria jako ścieżką awaryjną.
4. Drugie: retry z narastającym opóźnieniem na 5xx/429 przed odczytem ciała (OwnTV: 750 ms × próba, max 3 s)
   i uszanowanie `Retry-After` (StreamVault).
5. Nagłówki, UA i wersja HTTP są wątkiem pobocznym: OwnTV wysyła tylko `User-Agent: VLC/3.0.20 LibVLC/3.0.20`
   i wymusza HTTP/1.1. Do rozstrzygnięcia przez diagnostykę (rozdz. 7), nie przez zgadywanie.

Czego **nie wiemy**: jak działają TiviMate i IPTV Extreme (zamknięte kody). Nie widzieliśmy też prawdziwej listy
użytkownika ani jego panelu; wszystkie hipotezy o 504 to wnioski z kodu i z objawów, nie pomiary.

## 2. Metoda i licencje

Płytkie klony publicznych repozytoriów (`--depth 1`, bez budowania, bez uruchamiania), tylko czytanie. Numery linii
dotyczą wymienionych SHA.

| Repozytorium | SHA | Licencja | Co wolno |
|---|---|---|---|
| [ahXN00/OwnTV](https://github.com/ahXN00/OwnTV) (aplikacja, UI) | `4205733704dc` | GPLv3 | tylko zachowanie |
| [ahXN00/OwnTV_Core](https://github.com/ahXN00/OwnTV_Core) (silnik: cała sieć i synchronizacja) | `0b60ef6d25e0` | GPLv3 | tylko zachowanie |
| [Davidona/StreamVault-IPTV](https://github.com/Davidona/StreamVault-IPTV) | `bacaae7972f4` | własna „source-available, non-commercial” | tylko zachowanie; kodu nie przenosimy |
| [yaoxieyoulei/mytv-android](https://github.com/yaoxieyoulei/mytv-android) | `575aa365c077` | MIT | zachowanie (kod też dałoby się, ale nic tu nie wnosi) |
| [oxyroid/M3UAndroid](https://github.com/oxyroid/M3UAndroid) | `32f3658d4cd6` | GPLv3 | tylko zachowanie |
| [kodi-pvr/pvr.iptvsimple](https://github.com/kodi-pvr/pvr.iptvsimple) | `f91704459a34` | GPLv2 | tylko zachowanie |

Zasada dla PR 1b: **kod z tych repozytoriów nie trafia do Ketchupa**, także w postaci „przepisanej linia po linii”.
Przenosimy wyłącznie zachowanie (np. „sondę konta robimy zapytaniem bez `action`”, „opóźnienie rośnie z numerem próby”),
bo fakty o protokole Xtream i ogólne wzorce nie podlegają prawu autorskiemu, a implementację piszemy po swojemu na
własnych testach. GPLv3 w OwnTV wymagałoby przy kopiowaniu zmiany licencji całego Ketchupa, a licencja StreamVault
zabrania użycia komercyjnego. Repozytorium Ketchupa nie ma dziś pliku `LICENSE`; to kolejny powód, żeby nic stamtąd nie
wklejać. Nazwy produktów i linki w tej notatce służą tylko opisowi.

Linki w rozdz. 3 i 4 to permalinki (SHA w adresie), więc zostają ważne po zmianach w tamtych repo.

## 3. OwnTV (priorytet 1)

Cała sieć jest w OwnTV_Core; aplikacja OwnTV tylko ją składa (`README.md`, sekcja o OwnTV_Core).
Skrót ścieżki: `core/src/main/java/tv/own/owntv/core/` = **`C/`**; permalink do pliku:
`https://github.com/ahXN00/OwnTV_Core/blob/0b60ef6d25e03a24193f1a5680cb239816fd8625/core/src/main/java/tv/own/owntv/core/<plik>#L<n>`.

### 3.1 Klient HTTP i konfiguracja

| Element | Wartość | Źródło |
|---|---|---|
| Biblioteka | OkHttp | `C/di/DataModule.kt` L47 |
| Timeouty | connect 15 s, read 20 s, write 20 s (read = czas między bajtami, nie całość) | `DataModule.kt` L48–50 |
| `retryOnConnectionFailure` | **false** (ponawianie robi warstwa wyżej) | `DataModule.kt` L51 |
| Wersja HTTP | **wymuszone HTTP/1.1** (`protocols(listOf(HTTP_1_1))`), bo panele i CDN-y mają kapryśne HTTP/2 (RST_STREAM) | `DataModule.kt` L63–67 |
| Przekierowania | domyślne OkHttp: 301/302/303 → GET, 307/308 zachowują metodę; limit 20 | brak własnej konfiguracji |
| Cookies | brak `CookieJar` (domyślnie żadne) | brak wpisu w kodzie |
| DNS / proxy | własne `Dns` i `ProxySelector` (ustawienia użytkownika), domyślnie systemowe | `DataModule.kt` L52–59 |
| Zaufanie TLS | platforma + dodatkowe korzenie Let's Encrypt | `DataModule.kt` L60–62 |
| User-Agent | domyślnie **`VLC/3.0.20 LibVLC/3.0.20`**, nadpisywany per źródło; UA zapasowy przeglądarkowy tylko po odmowie (nigdy domyślny) | `C/network/HttpClient.kt` L36, L123, L138–139 |
| Inne nagłówki | **żadne** poza UA (brak `Accept`, `Referer` tylko jeśli użytkownik poda; OkHttp sam dodaje `Host`, `Connection: Keep-Alive`, `Accept-Encoding: gzip`) | `HttpClient.kt` L37–43, komentarz L74 |
| Metoda | tylko GET (`Request.Builder().url(url)` bez `.post`) | `HttpClient.kt` L37–38 |

### 3.2 Retry i backoff

`HttpClient.get(..., maxAttempts = 1)` (`HttpClient.kt` L28–35). Ponawia **tylko** wtedy, gdy blok czytający ciało
jeszcze nie ruszył: brak odpowiedzi albo status 5xx lub 429 (`HttpStatusException`, L94–95). Błędy w trakcie ciała
nie są ponawiane (L91–93). Opóźnienie: `750 ms × numer próby`, maksymalnie 3 s (L106, L140–141).

Gdzie ile prób: kategorie Xtream **3** (`C/parser/XtreamClient.kt` L123, L1026), pobranie M3U **2**
(`C/sync/M3uSyncer.kt` L461, L607), EPG 3 (`C/repository/EpgRepository.kt` L400, L1012), `get_series_info` 2
(`XtreamClient.kt` L263, L1028). **Zbiorcze `get_live_streams` / VOD / seriale: 1 próba** (domyślne `maxAttempts`;
`XtreamClient.kt` L173) — ratuje je nie retry, tylko przejście na pobieranie per kategoria (3.4).
`Retry-After` jest uwzględniany tylko dla Stalker (`C/sync/StalkerSyncer.kt` ok. L1164, L1312–1321), nie dla Xtream.

### 3.3 Xtream czy get.php? (kluczowa różnica)

1. Użytkownik wkleja link „M3U”. `XtreamM3uLink.parse` rozpoznaje `…/get.php` z `username` i `password`
   (`C/parser/XtreamM3uLink.kt` L27–47) i wyciąga `server`, `username`, `password`, `preferHls`.
   Komentarz autora (L6–13) mówi wprost: eksport M3U wrzuca filmy i seriale do Live, a **„niektóre panele odrzucają
   `get.php` (HTTP 884), choć to samo konto działa na `player_api.php`”**.
2. `SourceImporter` robi sondę konta `isXtreamAccount` (`C/setup/SourceImporter.kt` L252–264).
   Sonda = **`GET {server}/player_api.php?username=U&password=P`, bez parametru `action`**
   (`C/parser/XtreamClient.kt` L642–645, L772–779); sukces, gdy JSON ma `user_info`/`server_info`, albo gdy auth
   się nie powiodło, ale panel odpowiedział w formacie Xtream.
3. Sonda OK → źródło zapisane jako **XTREAM** (`SourceImporter.kt` L265–271). Sonda nie przeszła → dopiero wtedy
   zwykły import M3U z `get.php` (L274 i dalej; 2 próby).

Wszystkie adresy API mają postać `…/player_api.php?username=U&password=P&action=A[&category_id=X]`, parametry
kodowane `URLEncoder` (`XtreamClient.kt` L912–916, `categoryParam` L919–920). Metoda GET.

### 3.4 Kolejność pobierania katalogu i parsowanie

- `XtreamSyncer` uruchamia fazy **live, filmy, seriale równolegle** (semafor 3: `C/sync/XtreamSyncer.kt` L55–60,
  L502). Liczy się „trzy żądania, nie trzy strumienie” (komentarz L495–501).
- Faza (`syncXtreamPhase`, L214–219): odświeżenie kategorii → **zbiorcza lista strumieni** → jeśli zbiorcza rzuci
  wyjątek, `bulkOrFallback` (L396–404) zwraca `false` i wchodzi **`sliceByCategory`** (L417–490): `&category_id=X`
  po kolei, z przerwą **150 ms** między kategoriami (`C/sync/SyncSupport.kt` L625; użycie `XtreamSyncer.kt` L438–440).
  Komentarz L390–394: panele zwracają niestandardowe 512 na gigantyczną odpowiedź, a per kategoria działają.
- Błąd pojedynczej kategorii z „HTTP…” → kategoria pominięta; błąd sieci (timeout, DNS) → przerwanie całego
  fallbacku (L446–459). Zabezpieczenie przed panelem ignorującym `category_id` (L470–476).
- Parsowanie: **strumieniowe** `JsonReader` (`XtreamClient.kt` L170–175, `streamItems`), wstawianie partiami do
  bazy; kategorie czytane w całości (małe).

### 3.5 Co to znaczy dla 504

OwnTV nigdy nie prosi panelu o wygenerowanie całej listy M3U naraz, jeśli panel zna `player_api.php`. Pierwsze
zapytanie jest malutkie (JSON konta), a największe zapytania, jeśli w ogóle wystąpią, są dzielone na kategorie.
Przy 504 z reverse proxy (czyli odcięcie po czasie generowania) to jest dokładnie ta różnica, która decyduje.

## 4. StreamVault (priorytet 2, kontrprzykład)

Permalinki: `https://github.com/Davidona/StreamVault-IPTV/blob/bacaae7972f4ebe322b12e38ea57cfc31ff48552/<plik>#L<n>`.
Skrót: **`SV/`** = katalog `data/src/main/java/com/streamvault/data/`.

| Element | Wartość | Źródło |
|---|---|---|
| Klient | OkHttp, `followRedirects(true)`, `followSslRedirects(true)`, pula 10 połączeń, dispatcher 64/10 na host | `app/src/main/java/com/streamvault/app/di/NetworkModule.kt` L64–101 |
| Timeouty bazowe | connect/read/write **30 s**; EPG read 120 s | `SV/remote/NetworkTimeoutConfig.kt` L4–7 |
| Timeouty katalogu Xtream | „segmentowe” read/write 45 s, call 50 s; „ciężkie” read 300 s, write 60 s, call 330 s | `NetworkTimeoutConfig.kt` L13–18; użycie `SV/remote/xtream/OkHttpXtreamApiService.kt` L83–96 |
| UA | `StreamVault/<wersja> (Android; Media3; OkHttp)` (przechwytywacz wstawia go zawsze) | `SV/remote/http/RequestIdentity.kt` L70–81 |
| Wersja HTTP | domyślna OkHttp (h2 przy HTTPS), bez pinowania | `NetworkModule.kt` (brak `protocols`) |
| Cookies | brak `CookieJar` | `NetworkModule.kt` |
| Metoda | GET | `OkHttpXtreamApiService.kt` L215–237 |
| Link get.php | `parseXtreamPlaylistUrl`: `/get.php` + `username` + `password` + `type` (m3u lub m3u_plus) → źródło Xtream (serwer, login, hasło); walidacja długości i zakaz danych w authority | `domain/src/main/java/com/streamvault/domain/usecase/ValidateAndAddProvider.kt` L598–640 |
| 5xx / 429 | mapowane na `XtreamNetworkException`; `Retry-After` jest parsowany (`XtreamRetryAfter.kt`) | `OkHttpXtreamApiService.kt` L265–275, L313–330 |
| Retry katalogu | `isRetryable`: sieć/IO tak, auth/parsowanie/żądanie nie | `SV/sync/SyncManagerXtreamSupport.kt` L391–397 |
| Zmiana strategii | przy timeoutach, zbyt dużej odpowiedzi, błędach parsowania: porzucenie „pełnego katalogu” na rzecz podziału | `SV/sync/SyncManagerCatalogStrategySupport.kt` L80–100 |

**Czym różni się od OwnTV** (to „gorzej radzi sobie”, które zgłosił użytkownik, ale podobny rdzeń):

- link `get.php` **zawsze** staje się kontem Xtream (nie ma wariantu „spróbuj, a jak sonda nie przejdzie, M3U”); wymaga
  też `type=m3u|m3u_plus` w linku — link bez `type` nie jest rozpoznany;
- UA własny, nie playerowy; HTTP/2 nie jest wyłączone;
- dłuższe timeouty (30 s i do 300–330 s dla „ciężkich”), co ukrywa wolne panele zamiast je omijać;
- retry sterowany klasą błędu, a podział katalogu (segmentacja) włączany wynikami poprzednich prób.

Wniosek: to, że działa u użytkownika, wspiera tezę o `player_api.php`; różnice UA i HTTP/2 wskazują, że **UA i wersja HTTP
raczej nie są przyczyną** (StreamVault ma inny UA i nie wymusza HTTP/1.1, a działa).

## 5. Pozostałe playery (skrótowo)

- **mytv-android** (`app/src/main/java/top/yogiczy/mytv/data/repositories/iptv/IptvRepository.kt` L22–32): zwykły
  `OkHttpClient()` z domyślnymi timeoutami, jedno `GET` na adres źródła, całe ciało do `String`; brak UA, retry,
  Xtream. Pokazuje tylko, że „goły GET” działa na zwykłych plikach M3U.
- **M3UAndroid** (`data/src/main/java/com/m3u/data/parser/ParserUtils.kt` L16–45, `parser/xtream/XtreamParser.kt`):
  osobny parser Xtream (`dev.oxyroid.parser.xtream`) po `player_api.php`, `Request.Builder().url(url).build()`
  — czyste GET bez własnych nagłówków i bez retry. Wniosek: ścieżka Xtream, nie `get.php`.
- **pvr.iptvsimple** (`src/iptvsimple/utilities/CurlUtils.cpp` L12–25): libcurl Kodi: `connection-timeout` 5 s,
  `acceptencoding: gzip, deflate`, `redirect-limit` 5, plik niecofalny; to czysty GET listy M3U, bez Xtream.
  Jest przykładem klienta, który **nie** obsłuży panelu odcinającego `get.php`.

## 6. Tabela porównawcza

Oznaczenia w kolumnie Ketchup: **⚠ główna kandydatka** = różnica względem OwnTV, którą warto poprawić;
**◇** = różnica, którą najpierw ma rozstrzygnąć diagnostyka; **=** = zgodne.

| Aspekt | OwnTV | StreamVault | Ketchup Player (rc3) |
|---|---|---|---|
| Klient | OkHttp 4 | OkHttp | OkHttp 4.12 (`core/network/.../NetworkClients.kt`, `OkHttpTextFetcher.kt`) |
| Link `get.php` z loginem | rozpoznany → sonda → konto **Xtream**; M3U tylko gdy sonda nie przeszła | zawsze **Xtream** (wymaga `type=m3u/m3u_plus`) | **⚠** jedno żądanie `get.php` jako M3U (GET/POST), bez sondy i bez konwersji (`provider/m3u/.../M3uRequest.kt` L11–28) |
| Endpoint katalogu | `player_api.php?…&action=` | `player_api.php` | `player_api.php` tylko dla ręcznie dodanego konta Xtream (`XtreamProvider.kt` L35–82) |
| Metoda | GET | GET | GET lub POST (wybór użytkownika) |
| Sonda konta | `player_api.php` bez `action` (`XtreamClient.kt` L642–645) | walidacja przy dodawaniu | **⚠** brak |
| Kolejność pobierania | kategorie → bulk strumieni → fallback per kategoria; 3 fazy równolegle | segmentacja + zmiana strategii po timeoutach | **⚠** live: kategorie + bulk, bez fallbacku per kategoria; VOD/seriale bulk (`XtreamProvider.kt` L53–54, L79–82) |
| Przerwa między żądaniami | 150 ms w fallbacku per kategoria | brak stałej (zmiana strategii) | **⚠** brak (nie ma trybu per kategoria) |
| Parsowanie | strumieniowe JSON, partie do DB | strumieniowe + limity pamięci | VOD strumieniowo, **⚠ live streams buforowane** w całości (`fetch`, L53–54) |
| Timeouty | connect 15 s, read 20 s | connect/read 30 s; „ciężkie” 300 s | connect 20 s, read 60 s, strumieniowe 180 s (`STREAM_READ_TIMEOUT_S`) |
| Retry 5xx/429 | tak, 3 próby kategorii, 2 M3U; 750 ms × próba, max 3 s | tak, wg klasy błędu; `Retry-After` | **⚠** brak (jedna próba) |
| `Retry-After` | tylko Stalker | parsowany | tylko w komunikacie błędu (`ResponseDetail.kt`) |
| Wersja HTTP | **HTTP/1.1 wymuszone** | domyślna (h2 przy HTTPS) | domyślna OkHttp (h2 przy HTTPS) **◇** |
| User-Agent | `VLC/3.0.20 LibVLC/3.0.20` | `StreamVault/<ver> (Android; Media3; OkHttp)` | `OTT Navigator/1.7.2.1 (Linux;Android 12) ExoPlayerLib/2.19.1` **◇** |
| Nagłówki | tylko UA (+ OkHttp: Host, Keep-Alive, gzip) | UA | UA + **`Accept: */*`** dodane przez nas (`NetworkClients.kt` L46–47) **◇** |
| Cookies | brak | brak | brak (=) |
| Przekierowania | domyślne OkHttp | `followRedirects`, `followSslRedirects` | domyślne OkHttp + `PostRedirectGuard` dla POST (=) |
| Licencja | GPLv3 | source-available, non-commercial | — |

## 7. Hipotezy przyczyny 504 (ranga to subiektywne oszacowanie)

504 oznacza, że **bramka** (reverse proxy/CDN przed panelem) nie dostała odpowiedzi od zaplecza w czasie limitu.
To nie jest błąd klienta, nagłówków ani metody, dopóki zaplecze nie reaguje na nie inaczej.

| # | Hipoteza | Ranga | Za | Przeciw / niewiadoma |
|---|---|---|---|---|
| H1 | **`get.php` generuje całą listę na żądanie** (live + VOD + seriale, setki tysięcy wpisów) dłużej niż limit bramki; `player_api.php` (JSON, cache, małe porcje) mieści się w limicie | wysoka | oba działające playery omijają `get.php`; komentarz OwnTV o odrzucaniu `get.php` przez panele; 504 także z POST (metoda bez znaczenia) | brak pomiaru czasu do 504 z urządzenia użytkownika |
| H2 | **Pierwsze żądanie „rozgrzewa” cache**: zaplecze kończy generowanie po odcięciu przez bramkę i następne żądanie za kilkadziesiąt sekund przechodzi | średnia | typowy wzorzec paneli z cache listy; Ketchup nie ponawia, a ponowienie OwnTV (750 ms) jest za szybkie, więc to nie tłumaczy, że działa | rc3 jest nadal 504 po wielu próbach w odstępach; może też działać throttling (H3) |
| H3 | **Limit/ban per konto lub IP na `get.php`** (anty-scraping: jedno pobranie na N minut, liczenie prób z wcześniejszych rc) | średnia | wcześniejsze rc wysyłały wiele żądań; 5xx zamiast 429 jest u paneli normą | `Retry-After` nie widziano; diag z pauzami ≥ 30 s ma to odróżnić |
| H4 | **Reguła po UA/odcisku klienta** (np. OTT Navigator + OkHttp odrzucane/spowalniane, VLC nie) | niska–średnia | OwnTV ma udokumentowany WAF blokujący playerowe UA po nazwie | StreamVault ma inny UA i działa; objaw to zwykle 403, nie 504 |
| H5 | **HTTP/2 vs HTTP/1.1** (bramka źle obsługuje h2 do zaplecza) | niska | komentarz OwnTV (`DataModule.kt` L63–67) o RST_STREAM | StreamVault nie wymusza HTTP/1.1 i działa; objaw byłby raczej resetem strumienia, nie 504 |
| H6 | **Parametry get.php** (`type=m3u_plus` i `output=ts` dają największą odpowiedź; `type=m3u` mniejszą) | niska–średnia | rozmiar odpowiedzi ~ czas generowania | część H1 |
| H7 | **Nagłówki** (`Accept: */*`, `Accept-Encoding`, `Connection`) | bardzo niska | OwnTV wysyła mniej niż my | żaden znany panel nie odrzuca przez `Accept: */*` |
| H8 | **Ścieżka Xtream w Ketchupie też da 504**, bo `get_live_streams` bez `category_id` jest buforowane w całości i bez fallbacku | do sprawdzenia | `XtreamProvider.kt` L53–54 | nie wiemy, czy użytkownik próbował dodać konto jako Xtream, a nie M3U |

### 7.1 Co `ketchup_list_diag.sh` już sprawdza (diag-1)

| Krok | Co robi | Którą hipotezę rozstrzyga |
|---|---|---|
| B | GET `get.php`, UA aplikacji | linia odniesienia; czas do 504 (`time_total`) i `http_version` w raporcie → H1 (stały czas = limit bramki) |
| V1 | GET `player_api.php&action=get_live_categories` | H1 (czy API w ogóle odpowiada szybko) |
| V2 | POST `get.php`, dane w ciele | metoda (użytkownik już widział 504 z POST) |
| V3 | POST `player_api.php`, dane w ciele | metoda dla API |
| V4 | GET `get.php`, UA VLC | H4 |

Przerwy ≥ 30 s i `Retry-After` są wbudowane; raport nie zawiera adresu, hosta, loginu, hasła, `Server` ani `Set-Cookie`.

### 7.2 Czego brakuje

1. **Sonda konta jak w OwnTV** (`player_api.php` bez `action`) — to jest dokładnie pierwsze żądanie OwnTV.
2. **`get_live_streams` bez kategorii i z `category_id`** — V1 testuje tylko kategorie, więc nie pokaże, czy „ciężka”
   lista przechodzi (H1, H8).
3. **Powtórzenie B po ≥ 60 s** (H2) i porównanie czasów do 504 (H1).
4. **HTTP/1.1 jawnie** (`--http1.1`) i **bez nagłówka `Accept`** (`-H 'Accept:'`) (H5, H7).
5. **`type=m3u` zamiast `m3u_plus`** (H6).

## 8. Propozycja: wariant „jak OwnTV” w skrypcie diag

Osobny tryb `--jak-owntv` (zamiast V1–V4; nadal baza B + maks. 4 żądania, bo tak ustaliliśmy limit ruchu na panel),
uruchamiany po B. **Zero zmian w skrypcie w tej notatce — to tylko specyfikacja.**

Wspólne dla każdego kroku (1:1 z klientem OwnTV, w granicach tego, co robi curl):

- `--http1.1`, `-A 'VLC/3.0.20 LibVLC/3.0.20'`, usunięty domyślny `Accept` (`-H 'Accept:'`),
  `-H 'Accept-Encoding: gzip'`, `-H 'Connection: Keep-Alive'`; nagłówek `Host` zostawiamy curlowi;
- metoda GET, bez ciała i bez cookies (curl domyślnie bez `-b`), przekierowania `-L --max-redirs 20`;
- `--connect-timeout 15` i limit bezczynności 20 s zamiast całkowitego (`--speed-limit 1 --speed-time 20`),
  bez `--max-time` dłuższego niż w istniejącym skrypcie (`MAX_TIME`);
- przerwa ≥ 30 s między krokami, `Retry-After` honorowany (istniejąca `wait_before_next`);
  **to świadoma różnica względem OwnTV** (tam bez przerw, 150 ms), żeby nie wywołać blokady;
- ten sam szkielet `do_request` (adres, login i hasło tylko w pliku konfiguracyjnym `chmod 600`).

| Krok | Żądanie (1:1 z OwnTV) | Co zapisuje raport |
|---|---|---|
| OW1 | `GET {serwer}/player_api.php?username=U&password=P` (sonda konta) | status, czas, `http_version`, czy w ciele jest `user_info` (grep bez zapisu treści) |
| OW2 | `GET …&action=get_live_categories` | status, czas, liczba kategorii (przybliżona), pierwszy `category_id` (liczba) |
| OW3 | `GET …&action=get_live_streams&category_id=<pierwszy>` | status, czas, rozmiar |
| OW4 | `GET …&action=get_live_streams` (zbiorcza), limit bajtów jak `VARIANT_MAX_BYTES` | status, czas do pierwszego bajtu i całkowity, rozmiar, czy 504 |

Opcjonalnie `OW4b`: powtórzenie kroku, który dał 5xx, po ≥ 60 s (H2), zamiast dodatkowego wariantu.
Sanityzacja: ta sama co w `write_report` (literalne czyszczenie adresu, hosta, loginu, hasła i ich kodowań; bez
`Server`, `Set-Cookie`); `category_id` jest liczbą z panelu, więc może zostać; **nigdy nie zapisujemy treści ciała**.
Testy: rozszerzenie `tools/tvbox/tests/fake_curl` o odpowiedzi 200/504/503+`Retry-After` dla czterech adresów,
sprawdzenie, że w raporcie nie ma sekretów, `bash -n` i ShellCheck.

## 9. Plan PR 1b (bez implementacji)

Zależność: wynik `diag.md` od użytkownika (H1/H2/H3) i jego zgoda. Nie czekając na diag można przygotować
punkty 1–3, bo są poprawne niezależnie od przyczyny (oparte na zachowaniu obu działających playerów).

1. **Xtream-first dla linków `get.php` z loginem.**
   - Rozpoznanie (`username` + `password` w zapytaniu, końcówka `/get.php`) — logikę ma już `XtreamCredentials` /
     `M3uRequest`; do dopisania ścieżka „sonda → typ źródła”.
   - Sonda: `GET player_api.php?username&password` (bez `action`), 1 próba + retry z pkt 3; JSON z `user_info`
     = konto Xtream → zapis jako `XTREAM` (serwer, login, hasło w `SecretStore`, nie w Room).
   - Sonda nie przeszła → dotychczasowy M3U (GET/POST), tak jak dziś. Użytkownik ma w Ustawieniach przełącznik
     „Źródło: Auto / Tylko lista M3U”, żeby wymusić stare zachowanie (domyślnie Auto).
   - Dotyczy też już zapisanych list M3U? Tylko po ponownym dodaniu lub jednorazowej akcji „Przejdź na API konta”;
     bez cichej zmiany danych użytkownika.
2. **Pobieranie katalogu Xtream per kategoria jako ścieżka awaryjna.**
   - `get_live_streams` czytane strumieniowo (dziś buforowane, `XtreamProvider.kt` L53–54).
   - Bulk → przy 5xx/timeoucie `category_id` po kolei z przerwą ok. 150 ms; kategoria z błędem HTTP jest pomijana,
     błąd sieci przerywa całość; zabezpieczenie przed panelem ignorującym `category_id`.
   - Faza live najpierw, VOD/seriale w tle (zgodnie z krokiem 3 planu finalnego).
3. **Retry z backoffem na 5xx/429/brak odpowiedzi, przed odczytem ciała.**
   - Do `core/network`: do 3 prób, opóźnienie `750 ms × próba`, max 3 s; **`Retry-After` ma pierwszeństwo**
     (górny limit np. 60 s, a powyżej błąd z czytelnym komunikatem); nie ponawiamy 4xx ani błędów w trakcie ciała;
     zbiorcze listy 1 próba (ratuje je pkt 2); POST ponawiamy tylko dla znanych idempotentnych odczytów.
   - Opóźnienia wstrzykiwane (wirtualny czas w testach).
4. **Warunkowo, po diagnostyce:** profil UA (np. wybór „VLC”), wymuszenie HTTP/1.1 dla hosta, dodatkowe usunięcie
   nagłówka `Accept`. Nie zmieniamy domyślnych bez dowodu z diag (H4, H5, H7 są nisko).
5. **Komunikaty:** 504 po wyczerpaniu prób mówi, że to limit bramki panelu, i proponuje tryb API konta
   (rozszerzenie `friendlyImportError`/`POST_HINT`), bez adresu i danych dostępowych (`Redactor` bez zmian).

Testy offline (MockWebServer, dane syntetyczne o kształcie prawdziwych, zero danych użytkownika):

- panel: `get.php` zawsze 504, `player_api.php` OK → dodanie linku M3U kończy się źródłem XTREAM z kanałami;
- sonda zwraca HTML/401/404 → powrót do M3U (jak dziś);
- bulk `get_live_streams` 504/512 → import per kategoria, jedna kategoria 512 pominięta, reszta jest w bazie;
- panel ignoruje `category_id` → zatrzymanie fallbacku, bez pętli;
- 503 z `Retry-After` → jedno ponowienie po wskazanym czasie (wirtualny zegar); 429 bez nagłówka → backoff;
- 4xx nie jest ponawiane; błąd w połowie ciała nie jest ponawiany;
- E2E (pełny Hilt, prawdziwy Room, serwer HTTP, UI D-pad): „dodaj listę z panelem, który odcina get.php” + scenariusz
  w `.github/scripts/emulator-smoke.sh` (zgodnie z regułą z `CLAUDE.md`);
- testy bezpieczeństwa: brak loginu/hasła/hosta w `lastError`, logach i raporcie (istniejące testy `Redactor`).

Szacunek: pkt 1–3 razem to rozmiar M (jedna sesja ≈ jeden mikro-PR → rozbić na trzy PR: sonda+konwersja, fallback
per kategoria, retry).

## 10. Ograniczenia tej analizy

- Kod OwnTV czytany w wersji z podanych SHA; nie uruchamialiśmy go na panelu użytkownika.
- TiviMate i IPTV Extreme: kod zamknięty, brak wniosków. Jedyny dowód, że „działają”, to relacja użytkownika.
- Hipotezy H1–H8 to nie wyniki pomiarów. Rozstrzygną je wyniki `ketchup_list_diag.sh` (obecnego) i wariantu
  „jak OwnTV” (rozdz. 8) na prawdziwej liście, **wykonane przez użytkownika** (nie używamy jego listy w sesji).
- Nie sprawdzaliśmy, czy panel użytkownika zna `player_api.php` (to rozstrzygnie V1/OW1).
