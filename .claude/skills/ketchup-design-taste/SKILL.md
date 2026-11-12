---
name: ketchup-design-taste
description: Proces i kontrola jakości UI dla Ketchup Player (Android TV, Compose for TV). Użyj przy każdym nowym lub zmienianym ekranie, przy prośbie „dopieść UI/UX”, przy przeglądzie zrzutów i raportów z boxa. Daje kroki pracy, pierwszeństwo źródeł, antywzorce, skalę oceny 0-100 i pętlę poprawek.
---
# Ketchup Player: smak projektowy dla TV

Adaptacja idei z repozytorium *agent-design-taste* (MIT, AI Evolution Polska) pod naszą aplikację: ekrany 10-foot, pilot
(D-pad + OK + BACK), palety Morski/Grafit/Czerń/Własny. Nie kopiujemy ich plików (są pod web: HTML/CSS, widoki 1440/768/390 px)
i nie uruchamiamy ich skryptów. Przyjęte są: proces z pisemnym rozpoznaniem, hierarchia pierwszeństwa, lista antywzorców
z poziomami, ocena punktowa i zapis decyzji. Uzupełnia `android-tv-ui` (reguły fokusu i D-pada) i `LayoutAudit` (pomiar).

## Pierwszeństwo (gdy źródła się różnią, wygrywa wyższe)
1. **Marka i spec:** `docs/KONSPEKT.md`, język v5 i makiety v5.1, tokeny w `core:designsystem` (Palette, Motion, FocusItem, PosterCard…).
   Nie przemalowujemy Morski/Grafit/Czerń. Zmiana wyglądu istniejącego elementu idzie przez tokeny, nie przez nowe wartości.
2. **Minimum użytkowe TV:** kontrast >= 4,5:1, widoczny fokus, wszystko wykonalne D-pad + OK + BACK, czcionka >= 14 sp,
   cel fokusu >= 36 dp (cel projektowy 48 dp), animacje wg `Motion`.
3. **Potrzeba użytkownika:** raport z boxa i jawne polecenia (np. „oceny na kartach”, „kropki nowych odcinków”).
4. **Zasady tego skilla** (smak, rytm, hierarchia).
5. **Własny gust agenta.** Ostateczność: jeśli wybór broni się tylko tym, cofnij się wyżej.

Kolor marki (turkus `0xFF35C2B4`) zostaje na wypełnieniach, obrysie fokusu i dużych elementach. Tekst na akcencie używa
`ON_ACCENT`. Luki w systemie (brak tokenu, brak stanu) zgłaszamy i dopisujemy do `docs/DESIGN-LOG.md`, nie łatamy
„najbliższym wzorcem”.

## Kroki pracy (nie pomijaj 1, 6, 8)
1. **ROZPOZNANIE (pisemnie, przed kodem):** kto patrzy (kanapa, ok. 3 m, pilot), zadanie ekranu, jedna główna akcja,
   gęstość (niska/średnia/wysoka), emocja. Wypisz stany: pusty, ładowanie, błąd, brak sieci, brak danych.
2. **MAPA:** które tokeny i komponenty już są (`FocusItem`, `PosterCard`, `SegmentedControl`, `PillChip`, `InfoPill`,
   `ScreenScaffold`, `Motion`, `Palettes`). Nowy token tylko przy braku; nigdy drugiej skali odstępów, promieni czy fontów.
3. **UKŁAD:** nazwij wzorzec (szyna + lista + podgląd; siatka plakatów; hero + rzędy; panel z prawej; lista dwukolumnowa).
   Jedno pierwsze miejsce uwagi na ekran. Hierarchia przez rozmiar, grubość i kontrast, nie przez kolor tylko.
4. **BUDOWA:** wartości z tokenów. Skala odstępów: 4, 8, 12, 16, 24, 32 dp. Skala czcionek: 14 (opis pomocniczy),
   16-18 (treść), 20-22 (tytuł sekcji), 28-36 (nagłówek ekranu). Min. 3 role typograficzne na ekran.
5. **TESTY:** scenariusz E2E z D-pad/dotykiem (jako TV, `TvBoxRule`) + ekran dodany do `LayoutAudit` + Roborazzi.
   Błąd z urządzenia = najpierw test, który go odtwarza.
6. **RENDER I OGLĄD:** zrzut z Roborazzi (`*/src/test/screenshots`) albo z emulatora (`docs/qa/emulator`). Obejrzyj go.
   Nie pisz „zweryfikowane wizualnie” bez zrzutu. Sprawdź: ucięcia, wyśrodkowanie, nachodzenie, czytelność z 3 m.
7. **OCENA:** `SCORE-TV.md` (0-100) i `ANTI-SLOP-TV.md`. Blokery = zero, wynik >= 75 (cel 85).
8. **ZAPIS:** jedna linia w `docs/DESIGN-LOG.md`: co wygrało, co przegrało, jaka zasada z tego wynika.

## Pętla poprawek (propozycja własna, nie z oryginału)
Render → Krytyka → Poprawka, maksymalnie 3 obiegi na ekran. W każdym: obejrzyj zrzut, wypisz 3 najgorsze problemy,
napraw, uruchom `LayoutAudit` i scenariusz D-pad. Stop: zero blokerów i wynik >= 85, albo 3 obiegi (wtedy zgłoś, co zostaje
i dlaczego). Decyzję o wydaniu podejmuje człowiek: pętla informuje.

## Twarde zasady
- Wartość bez tokenu to błąd. Kolor, odstęp, promień i czas animacji pochodzą z designsystemu.
- Fokus widoczny zawsze (skala + obrys + tło); BACK cofa o jeden poziom; LEWO z pierwszej kolumny wraca do szyny/kategorii.
- Dane na ekranie muszą być prawdziwe i podpisane źródłem (np. pigułki IMDb/TMDB/RT, nie goła gwiazdka). Żadnych
  zmyślonych liczb, ocen ani „Lorem”.
- Informacja nie może być przekazana wyłącznie kolorem (kropka nowości ma etykietę, stan listy ma tekst).
- Dwa elementy tekstowe w tej samej linii mają odstęp >= 4 dp (u nas realnie 8-12 dp).
- Rozmycie/szkło tylko z fallbackiem (poziomy szkła, sonda płynności); max 2 szklane powierzchnie na widok.
- Nie dodawaj trzeciego akcentu na ekranie; akcent ma znaczenie (fokus, wybór, główna akcja).
