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
- [ ] 1.3 Pełny przegląd BACK/LEWO/PRAWO na każdym ekranie + test na ekran. Zrobione: Home, Na żywo, Ustawienia (scenariusz), Filmy/Seriale (test siatki), Odtwarzacz. Do zrobienia: Szczegóły, Szukaj, Moja lista z danymi, panel grup w Na żywo
- [x] 1.4 Ustawienia jako panel wsuwany z prawej (250 ms), nakładka na treść (22:xx 1.11, commit w gałęzi faza-d)
- [~] 1.5 Na żywo: żywy podgląd wideo ZROBIONY (wyciszony, po 0,9 s, zwalniany przy wyjściu, przełącznik w Odtwarzaniu; nieprzetestowany na boxie). Zostaje: lista kanałów w odtwarzaczu, menu kontekstowe (długie OK)
- [x] 1.6 Jedna skala animacji: `Motion` w designsystemie (fokus 120, szyna 160, panele 220, fade 150), użyta w szynie, FocusItem, Filmach, Na żywo
- [ ] 1.7 (wymaga emulatora/strumienia, NIE ZROBIONE) Odtwarzacz: test na strumieniu wielościeżkowym (emulator HLS z ffmpeg) + poprawki audio/napisy
- [ ] 1.8 (wymaga pomiaru na boxie po 0.8) Wydajność: pomiar płynności na boxie (zacięcia VOD, otwarcie odtwarzacza), poprawki największych; import w tle po pokazaniu kanałów (VOD dopisywane po)
- [ ] 1.9 (NIE ZROBIONE, do 1.1 lub po testach na boxie) Polityki §10.2 (BkSmartBufferPolicy, StreamRecoveryPolicy, SeriesEpisodePolicy, BkTvFailoverDeadline) z golden masterami
- [ ] 1.10 Radar: przypomnienia o audycjach (PRZESUNIĘTE NA 1.1: wymaga powiadomień/harmonogramu, nie blokuje 1.0)

## ETAP 2: wydanie 1.0 i szlif
Cel: podpisany APK, dokumentacja, brak znanych blokerów.
- [ ] 2.1 Keystore i sekrety GitHub (użytkownik, wg `docs/RELEASE.md`), workflow release, podpisany APK
- [ ] 2.2 Baseline profile zmierzony na urządzeniu (Macrobenchmark) zamiast ręcznego
- [ ] 2.3 Własna siatka EPG na LazyLayout (jeśli pomiary z 1.8 pokażą potrzebę) i prefetch obrazków Coil
- [x] 2.4 Dokumenty: KNOWN-EDGE-CASES.md i dodatek do TEST-CHECKLIST.md gotowe (README i ikona/banner bez zmian)
- [ ] 2.5 Pełny przebieg: CI zielony + emulator + skrypt na boxie bez FAIL + checklista ręczna
- [ ] 2.6 Tag 1.0.0 i wydanie

## Przesunięte na 1.1 (decyzja do potwierdzenia przez użytkownika)
- „Mam X minut” i „Zdrowie źródeł”, kanały wirtualne z biblioteki, TMDB/Trakt, nagrywanie, VPN/NAS, migracja z BK Player Next,
  Stalker, profile z PIN-em, efekt szkła z Haze (jeśli pomiar pozwoli), telefon (2.0).

## Dziennik zgłoszeń z boxa (do testów regresji)
Zrobione: lista 404 (nagłówki), wyjście z Ustawień pilotem, EPG nie dociąga, dźwięk w tle po wyjściu, biała karta w zrzutach, dubel w OSD.
Otwarte: przeskoki fokusu pilota (czeka na dane z testu pilota).

## Uwagi procesowe
- 1.11 wieczór: przy edycji ScenariosTest skryptem usunąłem przez pomyłkę 3 scenariusze (Ustawienia/BACK/EPG); przywrócone z historii. Przy edycjach plików testowych: zawsze `git diff --stat` przed commitem.
- CI: exit 134 w `:app:testDebugUnitTest` (JniConstants F) = crash natywny runnera; ponowić run.

## Etap 3: UI v5 (decyzja użytkownika, 2026-11-01)

Makiety: artefakt „Ketchup Player v4 makiety” (4 plansze 1920x1080: Na żywo, Home, Filmy, System).

- [ ] Paleta stonowana jak TiviMate: motyw Morski (turkus #35C2B4 na ciemnym szaro-turkusowym tle, domyślny), Grafit, Czerń OLED, Własny (akcent + odcień tła + poziom szkła). Zielenie/niebieskie z v3 wycofane.
- [ ] Szkło: panele z półprzezroczystością i rozmyciem tła (Modifier.blur, API 31+), poziomy Pełne/Lekkie/Wyłączone; domyślny poziom wybiera test płynności na boxie (słabszy SoC = Lekkie).
- [ ] Szyna jako pływająca szklana kapsuła; aktywna pozycja: turkusowa poświata; fokus: obrys + glow.
- [ ] Ambientowe tło: rozmyta grafika zaznaczonego filmu/kanału pod całym ekranem; akcent opcjonalnie z grafiki (Palette API).
- [ ] Logo stacji, łańcuch: (1) tvg-logo z listy, (2) baza iptv-org (https://iptv-org.github.io/api/logos.json + channels.json, dopasowanie po tvg-id, potem nazwa/alt_names; dane CC0), (3) własne logo użytkownika, (4) zastępnik w tonie motywu. Repo tv-logo/tv-logos: licencja niejasna, tylko opcjonalnie, po sprawdzeniu LICENSE.
- [ ] EPG: źródła z listy (url-tvg), iptv-org/epg (PL) jako opcja, własne adresy XMLTV.
- [ ] Home wg makiety: hero z „Mam czas na…” (30 min/90 min/2 h+/Zaskocz mnie), rzędy Nowe odcinki i Na żywo teraz.
- [ ] Filmy/Seriale: segment sortowania w pasku narzędzi, ambientowe tło z plakatu.
- [ ] Zdrowie źródeł: stan list i EPG w Ustawieniach (sprawna/częściowo/napraw).
