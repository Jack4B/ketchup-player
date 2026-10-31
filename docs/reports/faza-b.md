# Raport fazy B — 2026-10-31

Gałąź `faza-b` (PR #3, względem `faza-0`). Zakres zmieniony decyzjami właściciela: własny kolor akcentu, wydanie 1.0 tylko TV (ADR `docs/adr/tv-first.md`).

## Zrobione
- **Branding** (`tools/branding/generate.py`, odtwarzalny, bez projektowania logo od nowa): wersja przezroczysta bez halo (`branding/derived/logo-transparent.png` + podglądy na jasnym i ciemnym tle), sygnet wycentrowany według obwiedni pomidora z pikseli, adaptive icon + warstwa monochromatyczna, splash (SplashScreen API), banner TV 640×360 (tarcza z postacią + szeroka wstęga z napisem), wersja pozioma, feature-graphic 1024×500, ikona 512 do sklepu.
- **`:core:designsystem`:** 3 motywy v3 (Granat, Czerń, Bordo) + osobny wybór akcentu (6 gotowych + własny: HEX i suwaki HSL, automatyczna korekta kontrastu, ostrzeżenia dla zieleni/czerwieni), `KetchupTheme`, `FocusItem` (skala + obrys + tło), komponenty: `PosterCard`, `ChannelRow`, `KetchupButton`, `SettingsListItem`, `StateView`, `NewEpisodeBadge`, `StepSlider`, galeria Roborazzi.
- **Ekran „Wygląd”** (motyw, akcent, własny kolor, podgląd na żywo), zapis w DataStore, motyw stosowany globalnie w `MainActivity`.
- **Układ adaptacyjny:** `DeviceClass` (TV/telefon), `ScreenScaffold` (overscan 48×27 dp na TV, wcięcia systemowe na telefonie), dotyk dla komponentów TV.
- **Testy:** kontrast WCAG motywów i akcentów, logika własnego koloru, zapis wyglądu, macierz ekranów (telefon pion / telefon poziom / TV) ze zrzutami i kontrolą braku wyjścia poza ekran, D-pad i dotyk, smoke na emulatorze Android TV w CI.

## DoD — checklista
| Punkt | Stan | Dowód |
|---|---|---|
| Skrypt generujący assety z `ketchup-player-logo.jpg` bez nowego logo | ✅ | `tools/branding/generate.py`, wyniki w `branding/derived/` i `app/src/main/res` |
| Ikona adaptive + monochrome, splash | ✅ | zasoby w `app/src/main/res`; ikona wygenerowana i oceniona na pliku |
| Banner 320×180 dp, napis czytelny | ⚠️ | `drawable-nodpi/banner.png` (640×360), `branding/derived/banner-320x180.png`; napis czytelny na pliku, na kafelku launchera nie sprawdzony |
| Screenshoty ikony i banera na launcherze Google TV i Fire TV | ❌ | brak launchera/urządzenia w chmurze; do sprawdzenia przez właściciela |
| Przezroczysty wariant bez halo (jasne i ciemne tło) | ✅ | `branding/derived/preview-transparent-light.png`, `-dark.png` |
| `core:designsystem`: tokeny z palety, `FocusCard`/`PosterCard`, `ChannelRow`, `NewEpisodeBadge`, przyciski, `SettingsListItem`, stany loading/empty/error | ✅ | `core/designsystem`, galeria `components_*.png` |
| Galeria w `docs/mockups/components/` | ⚠️ | zrzuty w `core/designsystem/src/test/screenshots/`, kopia do `docs/mockups/components/` po akceptacji motywów |
| Tokeny zgodne z makietami v3 | ✅ | `PaletteContrastTest`: wartości z tabel kontrastu ±0,02, wszystkie akcenty AA we wszystkich motywach |
| Akceptacja właściciela | ⏳ | pytania poniżej |

## Metryki
Rozmiar APK debug ~28 MB (z zasobami brandingu). Release z R8 buduje się w CI.

## Odstępstwa od konspektu (ADR)
- **Wydanie 1.0 tylko TV**, telefon w 2.0 (`docs/adr/tv-first.md`); kod adaptacyjny zostaje, ale nie jest bramką wydania. `leanback` wymagany tylko w release.
- **Własny kolor akcentu** (HEX/suwaki) — poza makietami v3, na prośbę właściciela; domyślne 6 akcentów bez zmian.
- **Legacy mipmapy PNG pominięte** (minSdk 28 ≥ 26: wystarcza adaptive icon).
- **Banner jako `drawable-nodpi` 640×360** zamiast dwóch gęstości (lint `IconDipSize`).
- **Banner jako kompozycja** wycinków oryginału (tarcza z postacią + wstęga), żeby napis był czytelny w 320×180; piksele pochodzą wyłącznie z oryginalnego logo.
- **Lint:** `Typos` i `ObsoleteSdkInt` wyłączone (słownik angielski, AAPT2 wymaga `-v26` dla ikony adaptacyjnej).

## Ryzyka / znane problemy
- Wygląd na prawdziwym TV (overscan, odległość 3 m, kontrast na konkretnym panelu) niesprawdzony; emulator i Roborazzi pokrywają układ i fokus.
- Sygnet ma w tle fragment złotej ramki tarczy (kadr kołowy); do oceny wizualnej przez właściciela.
- Fokus FocusItem skaluje element (1,04–1,06×); przy elementach przy krawędzi overscanu może wystawać o kilka dp — do weryfikacji w ekranach z siatką (Faza U).
- Telefon: układy pion/poziom działają, ale są poza zakresem wydania 1.0.

## Pytania do właściciela
**Blokujące 🔒**
1. Akceptacja brandingu: ikona (sygnet), banner (kompozycja wstęgi), splash.
2. Podgląd ikony i banera na launcherze Google TV/Fire TV (ręcznie na boxie).

**Nieblokujące**
3. Czy dodać własny kolor tła (jak w TiViMate) i suwak przezroczystości nakładek (pytania z notatek makiet v3)?
4. Czy motyw jasny jest potrzebny (notatki v3, §7)?

## Plan następnej fazy
Faza D (dane i dostawcy) oraz dokończenie Fazy P po dostarczeniu fixture'ów; ekrany Home/Na żywo (Faza U) dopiero na komponentach z tej fazy, wraz z nawigacją TV (szyna) i jej testami fokusu.
