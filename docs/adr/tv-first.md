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
