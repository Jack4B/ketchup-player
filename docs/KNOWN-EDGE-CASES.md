# Znane przypadki brzegowe i ograniczenia (1.0-rc)

Stan na 1.11.2026. Każdy punkt: co się dzieje, dlaczego, co robić.

## Sieć i listy
- **HTTP 513 / odmowa przy pobieraniu listy.** To status odsyłany przez serwer dostawcy (blokada adresu IP, limit
  połączeń, filtr User-Agent). Komunikat w aplikacji zawiera typ serwera i oczyszczoną odpowiedź. Ustawienia → Sieć i VPN →
  „Testuj połączenie” sprawdza listę każdym nagłówkiem (1 zapytanie/s). Przy blokadzie IP pomaga tylko czekanie lub inny
  adres, nie zmiana nagłówka. Nie dodawaj tej samej listy wielokrotnie w krótkim czasie.
- **Jedno połączenie na konto.** Importy list idą po jednym; odtwarzanie kanału w trakcie importu tej samej listy może zostać
  odrzucone przez dostawcę z limitem 1 połączenia.
- **Domyślny User-Agent importu** to `okhttp/4.10.0`, a odtwarzacza `VLC/3.4.3 LibVLC/3.4.31` (jak IPTV Extreme na przechwyceniu
  z listy za Cloudflare, która aplikacji z nagłówkiem OTT Navigator odpowiadała 520). Import wysyła tylko cztery nagłówki w tej
  kolejności: `User-Agent`, `Host`, `Connection: Keep-Alive`, `Accept-Encoding: gzip` (bez `Accept`). OTT Navigator, VLC, TiviMate,
  IPTV Smarters i inne są gotowymi profilami; **własny** User-Agent wpisujesz bezpośrednio w kreatorze albo w Ustawieniach → Sieć
  (pozycja „Własny…”), osobno dla każdej listy. Wybór listy jest w bazie (`providers.userAgent`: id profilu albo `custom`),
  a wartość własnego User-Agent tylko w `SecretStore` (traktowana jak dane dostępu: bez logów, `toString`, eksportu i raportów;
  na liście ustawień pokazujemy „wartość ukryta”). User-Agent listy obowiązuje w całym imporcie tej listy: API, M3U, EPG,
  ponowieniach i przekierowaniach; wspólny klient nie jest zmieniany, więc listy nie nadpisują sobie ustawień.
  Przy przekierowaniu na **inny host** dalej idą tylko nagłówki tożsamości i zakresu (User-Agent, Accept*, Range…), a
  `Authorization`, `Cookie` i klucze w nagłówkach zostają (`CrossHostGuard`).
- **Migracja ustawień User-Agent.** Dawny wybór (DataStore `ua_profile_<id>`, `ua_custom_list`) jest przenoszony przy starcie
  aplikacji: klucz oznaczał świadomy wybór (profil, także OTT Navigator, albo własny), więc przechodzi bez zmiany wartości,
  a lista bez klucza dostaje nowy domyślny okhttp. Nowszy wybór w Room nie jest nadpisywany; klucze znikają po przeniesieniu.
- **Link `get.php` z loginem to konto Xtream.** Import idzie przez `player_api.php` GET (bez `action`, `get_live_categories`,
  `get_live_streams`, `get_vod_categories`, `get_vod_streams`, `get_series_categories`, `get_series`). `get.php` jest tylko
  trybem zapasowym i **tylko gdy API nie istnieje**: 404, 405, 410, 501 i inny 4xx poza dostępem i limitem, odpowiedź 200 z
  treścią spoza API Xtream, zwykły błąd 5xx bez `Retry-After`. **Bez fallbacku:** 401/403, błędne dane, konto nieaktywne lub
  wygasłe, 429, każdy błąd z `Retry-After`, błąd sieci bez odpowiedzi (brak połączenia, timeout) i pusta lub zepsuta lista kanałów.
  Po 429 albo błędzie z `Retry-After` kolejne odświeżenie tej listy nic nie wysyła, dopóki nie minie podany czas (bez
  `Retry-After` przy 429: 60 s; maks. 1 h; tylko w pamięci).
- **Ulubione, ukryte, „ostatnio oglądane” i filmy po zmianie trybu (M3U ↔ API).** Identyfikatory kanałów różnią się między
  trybami, więc przy udanym imporcie przenosimy `channel_flags` oraz „Moją listę”, postęp i oceny **filmów** na nowe id po
  numerze strumienia z adresu (`/live/U/P/101.ts` ↔ `stream_id` 101), w obrębie tego samego dostawcy, nigdy po nazwie i
  tylko dla par jednoznacznych (duplikat numeru po którejkolwiek stronie = bez zmiany). Przy nieudanym imporcie nic się nie
  zmienia; wpisy bez pary zostają pod starymi id (nic nie kasujemy), więc wracają po powrocie do poprzedniego trybu.
  **Ograniczenie (konkretne): seriale.** Serial z M3U (grupowany po nazwie odcinków) i serial z API (`series_id`) nie mają
  wspólnego numeru strumienia bez osobnych zapytań o każdy serial, więc „Moja lista”, postęp odcinków i znacznik „nowe
  odcinki” seriali zostają pod starymi id i po przejściu na API nie są widoczne (wracają po powrocie do M3U). Przewodnik EPG
  odbudowuje się przy następnym pobraniu.
- **EPG** pobiera się po dodaniu/odświeżeniu listy, przy starcie (starsze niż 6 h) oraz ręcznie (Telewizja → Odśwież EPG).
  Kanały bez `tvg-id` są dopasowywane po nazwie; brak dopasowania = „Brak danych przewodnika”.

## Odtwarzanie
- **Pauza po wyjściu z aplikacji.** Wznowienie po powrocie tylko, gdy wcześniej coś grało; kanał na żywo po pauzie może
  chwilę buforować.
- **Żywy podgląd w Na żywo** jest wyciszony i startuje 0,9 s po zaznaczeniu kanału. Na bardzo słabych urządzeniach wyłącz go
  (Ustawienia → Odtwarzanie), bo zajmuje drugi dekoder.
- **Wiele ścieżek audio/napisów** nie było sprawdzone na prawdziwych strumieniach wielościeżkowych (tylko FakePlayer).
  Przesunięcie napisów działa tylko w kierunku dodatnim.
- **Przewijanie** przyspiesza przy trzymaniu klawisza (10/30/60/90 s na krok); filtr zbyt gęstych naciśnięć (domyślnie
  250 ms, Ustawienia → Odtwarzanie → Czułość pilota) dotyczy tylko pojedynczych naciśnięć strzałek.

## Interfejs
- Ustawienia to panel z prawej nad bieżącą sekcją; BACK z listy kategorii zamyka panel.
- Filmy/Seriale: kategorie chowają się po wejściu na miniatury (6 kolumn); LEWO z pierwszej kolumny lub BACK je przywraca.
- Efekt szkła z rozmyciem (Ustawienia → Wygląd) wymaga Androida 12+ i może zacinać słabsze boxy; domyślnie wyłączony.
- Opisy i obsada filmów pochodzą z katalogu dostawcy (Xtream); listy M3U ich nie mają.
- Tylko TV poziomo; układ telefonu nie jest wspierany w 1.0.

## Poza 1.0
Nagrywanie, VPN/NAS, migracja z BK Player Next, Stalker, profile z PIN-em, TMDB/Trakt, „Mam X minut”, „Zdrowie źródeł”,
kanały wirtualne. Debrid, scrapery i resolver URL są poza zakresem na stałe.

## Lista kanałów: tytuł audycji w wierszu (ROZWIĄZANE)

Wiersz listy Na żywo czasem zostawał bez tytułu „teraz leci”, choć panel podglądu go miał. Przyczyna: każdy wiersz
ładował audycje sam (`produceState`) i gubił wynik po imporcie EPG. Teraz `GuideViewModel` daje jedną mapę
„co leci teraz” z bazy (`epg.onAir`, odświeżana po imporcie i co minutę), a wiersze tylko ją czytają.

## Metoda żądania listy: GET (domyślnie) albo POST (per lista)

Niektórzy dostawcy odrzucają GET dla `get.php` i `player_api.php` (np. 504 z `Retry-After`), a przyjmują POST. Metoda jest polem
listy (kreator „Dodaj listę” i Ustawienia → Sieć), zapisanym w Room (`providers.requestMethod`, migracja 7 → 8; dotychczasowe listy
dostają GET). Zasady:

- **M3U z `get.php`** (i każdy adres z `username` i `password` w zapytaniu): `username`, `password`, `type`, `output` idą w ciele
  `application/x-www-form-urlencoded`, adres zostaje bez nich (pozostałe parametry zostają w adresie). Inne adresy: POST z pustym ciałem
  i adresem bez zmian.
- **Xtream**: wszystkie wywołania `player_api.php` (uwierzytelnienie, kategorie, kanały, katalogi VOD i seriale, informacje o koncie,
  odcinki) idą POST-em z `username`, `password`, `action` i parametrami w ciele.
- **Bez zmian niezależnie od metody**: EPG (`xmltv.php`, osobny import z zapisanego adresu) i adresy strumieni (`/live/...`,
  `/movie/...`; odtwarza je ExoPlayer). Brak dowodu w kodzie lub dokumentacji, że dostawcy wymagają tam POST.
- **Przekierowania**: 307/308 zachowują POST i ciało (OkHttp), ale tylko na ten sam host i bez przejścia z HTTPS na HTTP
  (`PostRedirectGuard` zatrzymuje wysłanie loginu i hasła gdzie indziej); 301/302/303 zamieniają POST na GET bez ciała (zachowanie OkHttp).
- **`PostRedirectGuard`** (sieciowy interceptor OkHttp, `OkHttpTextFetcher.kt`): każde żądanie z ciałem pamięta serwer, do którego
  wysłał je użytkownik (`OriginHost`, w tagu żądania; tagi przechodzą na kolejne skoki przekierowań). Gdy po przekierowaniu 307/308
  host jest inny (porównanie bez względu na wielkość liter) albo schemat spada z HTTPS na HTTP, interceptor rzuca
  `PostRedirectBlockedException` i ciało (login, hasło) nigdzie nie jest wysyłane. Użytkownik widzi „Błąd sieci: PostRedirectBlockedException”.
  Kosztem jest, że dostawca przekierowujący POST na inny host (np. na adres balansera) przestanie działać do zmiany adresu listy.
  Testy: `OkHttpTextFetcherPostTest` (307 na inny host zatrzymane, drugie żądanie nie wychodzi; 307/308 na ten sam host zachowują POST).
- **Podpowiedź** przy 403, 405 i 5xx dla GET: „Dostawca może wymagać metody POST — zmień w ustawieniach listy.” Bez automatycznego przełączania.
- Ciało z hasłem nie jest logowane ani zapisywane; `FormBody.toString` go nie ujawnia, a błędy HTTP nie przepisują treści ani nagłówków.
## Tytuły VOD z paneli: czyszczenie i oceny IMDb (zgłoszenie z telefonu)

Oceny z wbudowanego indeksu (`film_index.db`) zależą od tytułu i roku wyciągniętych z nazwy w liście. `VodTitle.parse` usuwa
przedrostki języka i platform (`|PL|`, `PL -`, `A+ -`, `NETFLIX -`, `HBO |`), znaczniki w nawiasach (`[4K]`, `(MULTI SUB)`, `[ENG]`,
`DUAL`), znaczniki bez nawiasów z końca nazwy tylko WIELKIMI literami (`PL`, `HD`, `4K UHD`, `1080p BluRay x264`) oraz rok
(`(1999)`, `[1999]`, `Tytuł 1999`, `Tytuł.1999.1080p`). Znane koszty i granice:

- Tytuł kończący się liczbą z zakresu 1900-2029 (`Wonder Woman 1984`) jest rozbijany na tytuł i rok; indeks próbuje wtedy też pełnej
  formy, ale wyszukiwanie w TMDB/OMDb może wtedy nie trafić. Liczby spoza zakresu (`Blade Runner 2049`) i sam rok (`1917`) zostają.
- Przedrostek z dwukropkiem (`Saw: Spiral`, `Ice: Age`) zostaje. Przedrostek WIELKIMI literami z myślnikiem lub `|` (`ICE - Age`) jest
  uznawany za znacznik języka lub platformy i usuwany.
- Bez roku indeks wybiera tylko jedyną pozycję albo wyraźnego lidera (≥ 10 000 głosów i ≥ 20 razy więcej niż drugi); remake o zbliżonej
  popularności zostaje bez oceny zamiast z cudzą.
- Tytuły w tłumaczeniu różniącym się od etykiet Wikidata (pl/en) nadal nie trafią; indeks ma pozycje od 1980 roku.
- Pomiar regresji: `TitleMatchingRegressionTest` (syntetyczne nazwy zbudowane z popularnych pozycji indeksu; górna granica, nie pomiar
  prawdziwej listy).
