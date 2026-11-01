# Plan wydania 1.0 (rewizja z 1.11.2026, wersja robocza 0.7.2)

Jedyne źródło prawdy o tym, co zrobione i co zostało. Odhaczać po każdej paczce (skill `ketchup-pamiec`).
Zakres 1.0: TV poziomo, D-pad + OK + BACK. Poza zakresem na stałe: Debrid, scrapery, resolver URL.

## Co już mamy (stan na 0.7.2, kod w gałęzi `faza-d`)
**Potwierdzone na boxie (raporty użytkownika):** import list M3U/Xtream (do ~100 tys. pozycji, ~80 s), kreator, Na żywo z EPG
i grupami, Radar i Zainteresowania, Filmy, Seriale, Szczegóły (opis, obsada, ocena z dostawcy), odtwarzacz z OSD (audio, napisy,
przesunięcia, tempo, proporcje, uśpienie, zegar), failover, 3 motywy + akcent, ustawienia dwupoziomowe, skrypt diagnostyczny.
**Zrobione w kodzie, jeszcze nieprzetestowane na boxie:** User-Agent wybierany w kreatorze (domyślnie OTT Navigator) i edytowalna
lista w Ustawieniach → Sieć; filtr grup/kategorii; EPG automatycznie (start, po dodaniu listy, przycisk „Odśwież EPG teraz”);
logo kanałów (Coil); Filmy z chowanymi kategoriami i 6 kolumnami; Home z kartami poziomymi i „Nowe odcinki”; OSD filmu z kartą
„O FILMIE”; pauza po wyjściu z aplikacji; przyspieszone przewijanie; filtr zbyt gęstych naciśnięć pilota; szyna z jawną nawigacją.
**Infrastruktura:** CI (spotless, detekt, lint, testy, Roborazzi, emulator, gitleaks), podpis wydania z sekretów, baseline profile (ręczny).

## ETAP 1: dojście do stabilnej bety 1.0-rc (jakość i brakujące sedno)
Cel: wszystko, co użytkownik codziennie robi, działa na boxie bez błędów.
- [ ] 1.1 Weryfikacja 0.7.2 na boxie: lista się wczytuje (wybór UA), EPG, pauza w tle, przewijanie, logo (użytkownik, raport zip)
- [ ] 1.2 Przeskakiwanie fokusu pilota: analiza `keyguard.txt`/`getevent.txt` z testu pilota, poprawka u źródła, zdjęcie obejścia
- [ ] 1.3 Pełny przegląd BACK/LEWO/PRAWO na każdym ekranie (Home, Na żywo, Filmy, Seriale, Szczegóły, Szukaj, Moja lista, Ustawienia, Odtwarzacz) + test na ekran
- [ ] 1.4 Ustawienia jako panel wsuwany z prawej (250 ms), nakładka na treść
- [ ] 1.5 Na żywo: żywy podgląd wideo (jeden ExoPlayer, zwalniany przed pełnym ekranem), lista kanałów jako nakładka w odtwarzaczu, menu kontekstowe (długie OK)
- [ ] 1.6 Jedna skala animacji (stałe czasu w designsystemie: fokus 120 ms, panele 180-250 ms) zastosowana wszędzie
- [ ] 1.7 Odtwarzacz: test na strumieniu wielościeżkowym (emulator HLS z ffmpeg) + poprawki audio/napisy
- [ ] 1.8 Wydajność: pomiar płynności na boxie (zacięcia VOD, otwarcie odtwarzacza), poprawki największych; import w tle po pokazaniu kanałów (VOD dopisywane po)
- [ ] 1.9 Polityki §10.2 (BkSmartBufferPolicy, StreamRecoveryPolicy, SeriesEpisodePolicy, BkTvFailoverDeadline) z golden masterami
- [ ] 1.10 Radar: przypomnienia o audycjach

## ETAP 2: wydanie 1.0 i szlif
Cel: podpisany APK, dokumentacja, brak znanych blokerów.
- [ ] 2.1 Keystore i sekrety GitHub (użytkownik, wg `docs/RELEASE.md`), workflow release, podpisany APK
- [ ] 2.2 Baseline profile zmierzony na urządzeniu (Macrobenchmark) zamiast ręcznego
- [ ] 2.3 Własna siatka EPG na LazyLayout (jeśli pomiary z 1.8 pokażą potrzebę) i prefetch obrazków Coil
- [ ] 2.4 Dokumenty: `KNOWN-EDGE-CASES.md`, aktualna `TEST-CHECKLIST.md`, README, ikona i banner TV
- [ ] 2.5 Pełny przebieg: CI zielony + emulator + skrypt na boxie bez FAIL + checklista ręczna
- [ ] 2.6 Tag 1.0.0 i wydanie

## Przesunięte na 1.1 (decyzja do potwierdzenia przez użytkownika)
- „Mam X minut” i „Zdrowie źródeł”, kanały wirtualne z biblioteki, TMDB/Trakt, nagrywanie, VPN/NAS, migracja z BK Player Next,
  Stalker, profile z PIN-em, efekt szkła z Haze (jeśli pomiar pozwoli), telefon (2.0).

## Dziennik zgłoszeń z boxa (do testów regresji)
Zrobione: lista 404 (nagłówki), wyjście z Ustawień pilotem, EPG nie dociąga, dźwięk w tle po wyjściu, biała karta w zrzutach, dubel w OSD.
Otwarte: przeskoki fokusu pilota (czeka na dane z testu pilota).
