# Plan wydania 1.0 (rewizja z 1.11.2026)

Zakres 1.0 = to, o co chodziło użytkownikowi: **Na żywo + EPG w stylu TiViMate, Radar, Filmy/Seriale, odtwarzacz z OSD
(ścieżka audio, napisy, przesunięcie dźwięku i napisów, inne bajery), szybki import dużych list, motywy**.
Tylko TV poziomo, D-pad + OK + BACK. Poza 1.0 (przesunięte na 1.1): nagrywanie, VPN/NAS, migracja z BK Player Next,
Stalker, profile z PIN-em (zostaje jeden profil), Trakt/TMDB. Debrid/scrapery poza zakresem na stałe.

## Stan na dziś (beta 0.4.1)
Gotowe: import M3U/Xtream (do 100 tys. pozycji), kreator, Na żywo (nowy układ), Przewodnik + Radar, Filmy, Seriale, szczegóły,
Moja lista, Szukaj, odtwarzacz VOD z wznowieniem, 3 motywy + akcent, ustawienia dwupoziomowe, failover, EPG, skrypt testowy boxa.

## Kamienie milowe (każdy = jedna paczka commitów + jedno czekanie na CI + jeden APK)
| # | Zawartość | Test bramkowy |
|---|---|---|
| M1 | **Odtwarzacz OSD** (§3.4 raportu TiViMate): HUD z gradientem, auto-hide 4 s, pasek postępu/seek; panel **Audio** (lista ścieżek, wybór), **Napisy** (lista, wyłącz, rozmiar/kolor), **przesunięcie dźwięku** (±25 ms) i **napisów** (±100 ms), tempo odtwarzania, proporcje obrazu, licznik uśpienia, informacje o strumieniu (kodek, bitrate, klatki) | Test jednostkowy logiki ścieżek/offsetów (`PlayerTracks`), Roborazzi każdego panelu, test D-pad (otwórz panel → wybierz → BACK), scenariusz E2E na FakePlayer, test na emulatorze (HLS z kilkoma ścieżkami audio z ffmpeg) |
| M2 | **Szuflada nawigacji** (szyna z etykietami + scrim, panel playlist/grup, 180 ms) i **Ustawienia jako panel z prawej** (250 ms) | Roborazzi, test fokusu i restauracji fokusu, scenariusz restartu |
| M3 | **EPG split-view z podglądem wideo** (jedna oś czasu, `EpgTimeState`, żywy podgląd, focus tylko kolorem tła, czas kursora) + lista kanałów jako nakładka w odtwarzaczu + menu kontekstowe (długie OK) | Test oś czasu (jednostkowy), Roborazzi, scenariusz „OK zmienia podgląd, drugie OK = pełny ekran”, pomiar na boxie |
| M4 | **Radar kompletny**: zakładka „Moje zainteresowania”, przypomnienia o audycjach, zielone znaczniki nowych odcinków | Golden master (jest), test silnika, Roborazzi, scenariusz |
| M5 | **Wydajność**: import w tle po pokazaniu kanałów (VOD dopisywane po), diff przy odświeżaniu, stronicowanie dużych list (Paging), płynność na słabym boxie | Test budżetu czasu (jest dla parsera), pomiar na boxie skryptem |
| M6 | **Polityki z §10.2** (BkSmartBufferPolicy, StreamRecoveryPolicy, SeriesEpisodePolicy, BkTvFailoverDeadline) z golden master na starym kodzie | Golden master + testy |
| M7 | **Wydanie**: podpis (sekrety CI), baseline profile, ikona/banner na boxie, `KNOWN-EDGE-CASES.md`, checklista ręczna, README | Pełny CI + emulator + przebieg skryptu na boxie bez FAIL |

Kolejność: M1 (największa wartość, najbardziej brakuje) → M2 → M3 → M4 → M5 → M6 → M7. Po każdym milestone APK do testu na boxie.

## Przegląd designu (po M3): wzorce z poprzedniej aplikacji użytkownika (zdjęcia z 1.11.2026)
- **Home:** hero na całą szerokość z grafiką w tle i gradientem, duży tytuł, pigułki (rok, gatunki, czas, „Lektor PL”), opis, przycisk główny w kolorze akcentu + „Szczegóły”; rzędy z nagłówkiem z akcentowym paskiem i licznikiem „1/32”; karty poziome z grafiką i tekstem na gradiencie (tytuł, rok, ★ ocena).
- **Szczegóły filmu:** grafika w tle na pełny ekran, tytuł, pigułki (FILM, rok, czas, oceny), gatunki, długi opis, obsada, rząd przycisków (Kontynuuj, Od początku, Ulubione, Opcje, Zamknij), rząd „Podobne”. Dane opisowe z Xtream `get_vod_info` / `get_series_info` (plot, cast, backdrop, ocena, czas), bez TMDB.
- **Na żywo/EPG:** podgląd wideo u góry po lewej, duży opis audycji po prawej (godziny, „Pozostało 18 min”), lista grup po lewej, stan odtwarzania („bufor 4 s”), licznik „1 / 24 pozycji · filtr”.
- **Zegar w odtwarzaczu:** zrobione (prawy górny róg, ustawienie).
- Zasady: jedna skala odstępów, jedna typografia, grafika zamiast pustych kafli, animacje krótkie, bez migotania na słabym SoC.

## Opisy, oceny, nowe odcinki
- **Opisy i obsada:** najpierw z dostawcy (Xtream `get_vod_info`/`get_series_info`), potem opcjonalnie TMDB (własny klucz użytkownika w Ustawieniach, opisy po polsku, grafiki, obsada). IMDb nie ma darmowego API (OMDb z kluczem użytkownika jako opcja). Trakt (oficjalne API, logowanie kodem na TV): ocena, watchlista, kalendarz, scrobbling; przewidziane na 1.1. moviesranking.com nie ma publicznego API: tylko inspiracja.
- **Zielone kropki „nowy odcinek”:** stan „widziane” per serial; Xtream: pole `last_modified` z `get_series`; M3U: zmiana liczby odcinków po odświeżeniu. Kropka na karcie serialu, na pozycji „Seriale” w szufladzie, rząd „Nowe odcinki” na Home. Zbieranie przy odświeżaniu listy, bez dodatkowego ruchu.

## Trzy funkcje, których nie mają inne odtwarzacze (propozycje)
1. **Zdrowie źródeł:** aplikacja sama mierzy, które listy i kanały działają (czas startu, buforowanie, błędy) i wybiera najlepsze źródło zanim wciśniesz OK; zielone/żółte/czerwone kropki przy kanałach i panel „Zdrowie list”. Inni przełączają dopiero po awarii.
2. **„Mam X minut”:** wybierasz, ile masz czasu (np. 45 min, do 22:00), a aplikacja pokazuje filmy, odcinki i audycje na żywo, które się w tym zmieszczą i skończą na czas.
3. **Kanały wirtualne z mojej biblioteki:** automatyczne kanały „Kryminały”, „Komedie”, „Dla dzieci” odtwarzające kolejne odcinki z twojego katalogu 24/7, z własnym EPG w przewodniku.

## Zasady pracy (oszczędzanie limitu)
1. **Paczki:** jeden commit/push na milestone (lub podetap), nie po każdej poprawce. Push anuluje trwający CI, więc po push czekam na wynik, zamiast pchać dalej.
2. **Lokalnie najpierw** `spotlessApply detekt` + testy zmienionych modułów, dopiero potem push (CI nie ma łapać stylu).
3. **Test przed poprawką** każdego błędu z boxa.
4. **Czekanie bez tokenów:** Monitor/until-loop na wynik CI i testów, nie ankietowanie.
5. **Zrzuty Roborazzi** commituje bot CI na gałęziach `faza-*`; przy konflikcie binarnych zrzutów biorę wersję lokalną.
6. **Podział sesji:** po każdym milestone aktualizuję ten plik (sekcja „Stan”) i `docs/reports/`, żeby nowa sesja wystartowała z tym dokumentem.

## Pakiet dostarczania (przy KAŻDYM APK)
APK + aktualny `ketchup_tvbox_test.sh` + gotowe komendy (patrz `docs/TVBOX-TESTY.md`, sekcja „Komplet komend”).
Zawsze: nazwa APK z numerem wersji i skrótem commita, informacja co sprawdzić ręcznie, co jest znane i nieprzetestowane.

## Ryzyka
- Wiele ścieżek audio/napisów zależy od strumienia z listy użytkownika; testuję na syntetycznym HLS (ffmpeg) i FakePlayer, na boxie ręcznie.
- Podgląd wideo w EPG (M3) = jedna instancja ExoPlayer, uwaga na pamięć i dekoder słabego SoC.
- Czas importu na boxie (M5) zależy od sieci; licznik MB pokaże, ile to sieć, a ile procesor.
