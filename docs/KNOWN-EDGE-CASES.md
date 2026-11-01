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
