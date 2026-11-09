# Znane przypadki brzegowe i ograniczenia (1.0-rc)

Stan na 1.11.2026. Każdy punkt: co się dzieje, dlaczego, co robić.

## Sieć i listy
- **HTTP 513 / odmowa przy pobieraniu listy.** To status odsyłany przez serwer dostawcy (blokada adresu IP, limit
  połączeń, filtr User-Agent). Komunikat w aplikacji zawiera typ serwera i oczyszczoną odpowiedź. Ustawienia → Sieć i VPN →
  „Testuj połączenie” sprawdza listę każdym nagłówkiem (1 zapytanie/s). Przy blokadzie IP pomaga tylko czekanie lub inny
  adres, nie zmiana nagłówka. Nie dodawaj tej samej listy wielokrotnie w krótkim czasie.
- **Jedno połączenie na konto.** Importy list idą po jednym; odtwarzanie kanału w trakcie importu tej samej listy może zostać
  odrzucone przez dostawcę z limitem 1 połączenia.
- **Domyślny User-Agent** to OTT Navigator. Panele z białą listą nagłówków mogą wymagać innego: wybierz z listy w kreatorze
  albo w Ustawieniach → Sieć (ptaszek przy wybranym), własne wpisy dodajesz tamże.
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
