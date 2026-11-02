# Dziennik decyzji projektowych (Ketchup Player)

Jedna linia na decyzję: co wygrało, co przegrało, jaka zasada z tego wynika (krok 8 skilla `ketchup-design-taste`).

- 2026-11-02 Wygląd: siatka motywów 2×2 z miniaturami wygrała z rzędem 4 kart po 200 dp. Rzut: panel ma ~460 dp szerokości, a
  rząd kart o stałej szerokości wypada poza ekran. Zasada: karty w panelu mają szerokość względną (`weight`), nigdy stałą.
- 2026-11-02 Grupy w Na żywo: licznik odsunięty od nazwy (12 dp), bo długa nazwa w dwóch liniach „sklejała się” z liczbą.
  Zasada: sąsiednie teksty w jednej linii mają odstęp, a nazwa dostaje wagę; `LayoutAudit` pilnuje.
- 2026-11-02 Czcionki 13 sp podniesione do 14 sp (opisy pomocnicze). Zasada: absolutne minimum 14 sp, treść 16-18 sp.
- 2026-11-02 Panel Ustawień nieprzezroczysty (97% → 100%): przez szkło przebijał tekst sekcji pod spodem. Zasada: panel nad
  treścią bez prześwitów; szkło tylko tam, gdzie pod spodem jest obraz, nie tekst.
- 2026-11-02 Oceny: pigułki z podpisem źródła (IMDb, TMDB, RT) zamiast jednej gwiazdki. Zasada: dane podpisujemy źródłem.
- 2026-11-02 Skrypty z zewnątrz (screenshot.mjs itp.) nie wchodzą do repo; pomiar układu robimy własnym `LayoutAudit`.

## Luki w systemie designu (do decyzji)
- Brak tokenów typografii (role: caption/body/title/headline) w `core:designsystem`; dziś rozmiary są literałami `sp`.
- Brak tokenów odstępów (4/8/12/16/24/32); dziś literały `dp`.
- Hero na Home bywa przycięte u góry po przewinięciu (wymaga decyzji: przewijać całą sekcję czy tylko rzędy).
- 2.11.2026 Makiety v5 (Artifact, 8 plansz TV 1920x1080): oceny w pigułkach z podpisem źródła (IMDb/TMDB/RT), nowość = kropka + etykieta, panel INFO z diagnostyką (bufor, stabilność, połączenia), kreator motywu Własny z podglądem i kontrastem, stany puste/błąd/ładowanie/brak kluczy. Zasada: dane zawsze z podpisem źródła, stan nigdy samym kolorem. Luka: plansze nie mają jeszcze Seriali i Przewodnika (do v5.1).
- 2.11.2026 v5 wdrożenie 1: makieta daje układ i treść, rozmiary zostają w dp (piksele makiety dałyby czcionki < 14 sp). Pigułka listy w nagłówku (połączenia N z M), „N z M kanałów” nad listą, ocena z podpisem źródła na plakacie, plakietka nowości z kropką i podpisem, podgląd i kontrast akcentu w edytorze, podpowiedź o kluczach w szczegółach. Zasada: fokus pod panelem blokuje `LocalFocusBlocked` (FocusItem ma dwa cele fokusu, canFocus z rodzica dziedziczy tylko najbliższy). Luki: karty list jako pigułki, pasek zaznaczonego w Filmach, szklany pasek odtwarzacza.
