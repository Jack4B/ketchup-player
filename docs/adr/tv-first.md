# ADR: wydanie 1.0 tylko na TV (poziom), telefon w 2.0

Status: **zdecydowane przez właściciela (2026-10-31).**

## Decyzja
- **1.0:** Android TV / Google TV / Fire TV, ekran poziomy, sterowanie pilotem (D-pad + OK + BACK). Wszystkie kryteria akceptacji (fokus, overscan 48×27 dp, 10-foot UI) dotyczą TV.
- **2.0:** wariant mobilny (telefon/tablet): osobna nawigacja (dolny pasek zamiast szyny), układy pion/poziom, dotyk, Chromecast jako nadawca.
- Kod adaptacyjny z Fazy B (`DeviceClass`, `ScreenScaffold`, testy pion/poziom) **zostaje**: jest tani w utrzymaniu i pozwala testować UI na telefonie w trakcie developmentu, ale nie jest kryterium wydania 1.0.

## Konsekwencje
- Manifest: `android.software.leanback` jest **wymagany tylko w buildzie release** (placeholder `leanbackRequired`), więc sklep pokaże aplikację wyłącznie urządzeniom TV. Build debug zostaje instalowalny na telefonie (testy deweloperskie).
- Testy: macierz „telefon pion / telefon poziom / TV” zostaje, ale tylko TV jest bramką jakości. Błędy telefonu nie blokują wydania 1.0.
- Z 2.0 wynikają osobne ekrany mobilne (Home, Na żywo, Filmy…) oraz ADR nawigacji mobilnej.

## Plan wydań
| Wydanie | Zakres |
|---|---|
| 1.0 | TV: pełny zakres wg KONSPEKT (fazy 0–Q), bez wariantu mobilnego |
| 2.0 | Mobile: telefon/tablet (nawigacja, pion/poziom, dotyk, Cast sender) |

## Uzupełnienie (2026-11-09): dotyk w odtwarzaczu

Telefon: minimalna obsługa dotyku do testów; pełny telefon w 2.0. To nie zmienia decyzji tv-first ani bramki 1.0.

- Odtwarzacz ma tryb dotyku (`TouchMode` w `feature/player-ui`). Włącza go dopiero dotknięcie ekranu palcem lub rysikiem, wyłącza dowolny klawisz (poza BACK i głośnością). Na TV nikt nie dotyka ekranu, więc tryb nie włącza się: przyciski dotykowe nie są nawet komponowane, a obsługa pilota (`onPreviewKeyEvent`) jest bez zmian.
- W trybie dotyku: stuknięcie pokazuje albo chowa OSD, przyciski wyjścia, odtwórz/pauza, ±10 s i pasek postępu (stuknięcie lub przeciągnięcie, jedno przewinięcie po puszczeniu palca). Na żywo bez paska i bez skoków. OSD chowa się po tym samym czasie co dotąd, a dotyk go odświeża.
- Poza zakresem (2.0): opcje audio/napisy/obraz dotykiem, układ pionowy, gesty (podwójne stuknięcie, jasność, głośność), strojenie rozmiarów pod małe ekrany.

