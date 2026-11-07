# Ketchup Player — makiety v5.1

Uzupełnienie języka v5 (DESIGN-LOG, 2.11.2026) o brakujące plansze z recenzji (seria, przewodnik/Radar, 720p, telefon, PIN, stany, fokus). Tokeny kolorów biorą się z `core/designsystem/.../Palette.kt` i `AccentId`, nie z v3/Granat HTML.

- Pełny zestaw ekranów: motyw **Morski** (domyślny, tło `#0B1112`, akcent `#35C2B4`).
- Porównanie 2×2: Morski, Grafit (`#121315`), Czerń OLED (akcent `#EDEDED`, promień 6), Własny (hue 195°, kolory z `Palettes.custom` / `AccentFit.fromHsl`).
- Home dodatkowo w Grafit, Czerń i Własny (`tv1080-home-*`).
- Język v5: pigułki ocen z podpisem źródła, nowość = kropka + etykieta, pigułka listy „N z M”, „N z M kanałów”, diagnostyka INFO, kreator motywów 2×2 z kontrastem, podpowiedź o kluczach API, szklany pasek odtwarzacza.
- Pokrycie z recenzji: 720p, telefon (podgląd 2.0), PIN (podgląd 1.1), puste/błąd/ładowanie, fokus D-pad, placeholdery (gradient + monogram, bez cudzych plakatów), wspólny katalog danych (19:42, Info 24 / Rozmowa dnia 19:15–20:15).
- HTML: `ketchup-player-makiety-v5.1.html` (generator `generate.py`, zrzuty `screenshot.py`).
- v3 w `docs/mockups/v3-*` zostaje jako historia. Nie zastępować tokenów z kodu paletą v3 ani jednomotywowym „v4” z 6.11.

Zaakceptowane jako kierunek uzupełnienia v5: 6.11.2026.
