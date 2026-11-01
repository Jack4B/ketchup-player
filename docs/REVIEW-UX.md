# Przegląd UI/UX (1.11.2026, beta 0.6.2)

Podstawa: zrzuty testowe Roborazzi, zdjęcia z boxa (Na żywo, Filmy, Home), zrzuty starej aplikacji i TiViMate.
Skala: 1–5 (5 = poziom TiViMate / nowoczesnych aplikacji TV).

| Obszar | Ocena | Uwagi |
|---|---|---|
| Nawigacja / logika menu | 4 | Szyna z ikonami, rozwija się z podpisami, popycha treść; Na żywo = jedna sekcja z zakładkami (Siatka, Radar, Zainteresowania) i panelem grup. Jasna hierarchia, wszystko D-pad + OK + BACK. Słabość: Ustawienia mają 12 kategorii, z czego część „wkrótce”. |
| Na żywo / EPG | 3,5 | Układ jak TiViMate (podgląd, opis, siatka). Brak żywego podglądu wideo i logo kanałów w siatce; linia „teraz” zasłaniała tytuły (poprawione). |
| Filmy / Seriale | 4 | Grafika w tle, szklany panel kategorii, karty z tytułem na obrazie, licznik pozycji, filtr kategorii. Do zrobienia: karty poziome z backdropem, rząd „Kontynuuj” na górze. |
| Home | 3,5 | Hero z grafiką i pigułkami, rzędy z akcentowym paskiem. Brak rzędu „Nowe odcinki” i „Mam X minut”. |
| Szczegóły | 4 | Grafika w tle, pigułki, opis i obsada od dostawcy. Brak „Podobnych” w układzie ze zdjęć. |
| Odtwarzacz / OSD | 4 | Audio, napisy, przesunięcie, tempo, proporcje, zegar. Nietestowane na wielościeżkowych strumieniach. |
| Czytelność (10 stóp) | 3,5 | Część tekstów miała 11–12 sp: za małe z kanapy. Podniesione do 13–14 sp. |
| Spójność wizualna | 4 | Trzy motywy + akcent, wspólne komponenty (glassPanel, InfoPill, PillChip). |
| Płynność | ? | Do zmierzenia na boxie (zacięcia przy otwarciu odtwarzacza 31 klatek, przewijanie VOD 829 ms). Pilot: filtr zbyt gęstych naciśnięć. |

## Wdrożone w tej rundzie
1. Czytelność: minimalny rozmiar tekstu kart i list 13 sp, podpisy czasu i opisy 14 sp.
2. Linia „teraz” w siatce EPG półprzezroczysta (nie zasłania tytułów).
3. Ustawienia: Grupy i kategorie (filtr z krajami), Sieć (nagłówek User-Agent), Czułość pilota, Efekt szkła.

## Plan kolejnych poprawek (kolejność wg wartości)
1. **Home:** rząd „Kontynuuj oglądanie” zawsze pierwszy, rząd „Nowe odcinki” z zieloną kropką, karty poziome z backdropem tam, gdzie dostawca go podaje.
2. **Na żywo:** żywy podgląd (jedna instancja ExoPlayer, zwalniana przed pełnym ekranem), logo kanałów w siatce, własna siatka na LazyLayout po pomiarze.
3. **Ustawienia:** ukryć kategorie „wkrótce”, panel Ustawień jako nakładka z prawej (250 ms).
4. **Spójność ruchu:** jedna skala animacji (focus 120 ms, panele 180–250 ms), bez animacji skoku fokusu.
5. **Funkcje wyróżniające:** „Mam X minut”, „Zdrowie źródeł”.
6. **Wydajność:** baseline profile zmierzony (Macrobenchmark), prefetch obrazków Coil.

## Zasady, które trzymamy
- Fokus widoczny zawsze (ring w kolorze akcentu 3 dp), D-pad bez pułapek, BACK zawsze wraca o jeden poziom.
- Tekst nie mniejszy niż 13 sp, ważne informacje 14+ sp.
- Półprzezroczystość zamiast rozmycia domyślnie; rozmycie tylko jako opcja na Androidzie 12+.
