# Plan UX po raporcie z boxa (2.11.2026, rc0.9.6-rc1)

Źródło: zrzuty i uwagi użytkowniczki. Zasada: błąd z urządzenia = najpierw test, który go odtwarza; układ mierzymy testem, nie okiem.

## Zrobione w tej paczce (kod na `dev`)
| Zgłoszenie | Przyczyna | Rozwiązanie | Dowód |
|---|---|---|---|
| Wygląd: widać tylko 2 motywy, nie ma „Własny” | 4 karty po 200 dp w wąskim panelu: 3. i 4. poza ekranem | `ThemePicker`: siatka 2×2 z miniaturami | test `wygladPokazujeWszystkieMotywyWPanelu` (odtworzył błąd), emulator |
| LEWO z opcji nie wraca do kategorii | brak jawnej obsługi wyjścia z grupy fokusu | `onExit` w grupie opcji | emulator: PRAWO/LEWO w Ustawieniach (fokus z drzewa UI) |
| Ghost text przez panel Ustawień | panel 97% krycia | panel w pełni nieprzezroczysty | zrzuty emulatora |
| Brak ocen i opisów | lista M3U nie ma metadanych | TMDB + OMDb, kolejka dla widocznych kart, cache w bazie, klucze w Ustawieniach | `MetadataTest` (6), 2 scenariusze E2E |
| Brak kropek nowych odcinków | kropka tylko po późniejszym odświeżeniu listy | serial zmieniony w 7 dni jest nowy do otwarcia | 2 testy `VodRepositoryTest` |
| Tytuły `A+ - ... (MULTI SUB)` | dwie kopie regexów, za wąskie | `VodTitle` w core:model | `VodTitleTest` (5) |
| Brak informacji o koncie listy | nie pobieraliśmy `user_info` | połączenia x z y, ważność, konto próbne na ekranie list | 3 testy Xtream, scenariusz E2E |
| Brak diagnostyki | brak statystyk odtwarzania | panel Info: bufor, łącze, przerwy, stabilność, połączenia | `StabilityScoreTest` (6), `PlaybackStatsCollectorTest` (5), scenariusz E2E |
| OK na chipie otwierało listę kanałów (TV na żywo) | kolejność warunków w obsłudze klawiszy | `!chipsOpen` | scenariusz diagnostyki (odtworzył błąd) |
| Czcionki 13 sp, licznik grupy „skleja się” z nazwą | brak reguł | 14 sp minimum, odstęp 12 dp; `LayoutAudit` pilnuje | `ukladGlownychEkranowSpelniaZasadyDesignu` |
| Skrypt boxa: D1/D2b/D4/J3 FAIL | skrypt trafiał D-padem w zakładkę „Moje zainteresowania” | `tap_first` po współrzędnych z drzewa UI | test parsera, do sprawdzenia na boxie |

## Jeszcze nie zrobione (uczciwie)
- **Filmweb, Trakt**: Filmweb nie ma publicznego API (scraping łamie regulamin i się psuje), więc nie wchodzi. Trakt: osobna paczka (klucz klienta, oceny społeczności).
- **Scoring listy zapamiętywany między sesjami** (dziś ocena stabilności dotyczy bieżącej sesji).
- **Pomiar płynności animacji**: wymaga urządzenia; emulator pokaże `dumpsys gfxinfo` dopiero w scenariuszu E6 (plan emulatora).
- Dalszy szlif wizualny wg makiet (hero Home przycięte u góry, rytm odstępów, skala typografii jako tokeny w designsystemie).
- Własne logo kanału per użytkownik, kanały wirtualne z biblioteki, ambientowe tło z plakatu.
