# Raport fazy D — 2026-11-01 (beta 0.1, Live TV)

Gałąź `faza-d` (PR #4, względem `faza-b`). Obejmuje też część faz P (polityka i failover) i E (EPG) oraz pierwsze ekrany TV potrzebne do oglądania kanałów. Wydanie 1.0 tylko TV (ADR `docs/adr/tv-first.md`).

## Zrobione
- **Dostawcy:** M3U (parser, `liveOnly`: filmy i seriale pomijane do czasu ekranów VOD) i Xtream Codes (logowanie, kanały na żywo, adres XMLTV). Dane logowania tylko w `SecretStore` (AES-GCM, klucz w Android Keystore); w bazie wyłącznie host. Test sprawdza, że bajty bazy nie zawierają hasła.
- **Katalog offline (Room v2, AutoMigration 1→2):** dostawcy, kanały, flagi (ulubione), EPG. Odświeżenie listy nie kasuje ulubionych. Łańcuch równoważnych kanałów u różnych dostawców (`equivalents`).
- **Nazwy kanałów:** `displayName` bez przedrostków dostawcy (`PL| `, `PL-VIP| `); `normName` wspólny dla EPG i failoveru.
- **EPG:** strumieniowy parser XMLTV, dopasowanie po `epgId` i po nazwie, atomowa podmiana (staging), single-flight, horyzont 72 h, odświeżanie co 6 h. Własne źródła EPG oraz flagi OVH/Teleman (Teleman domyślnie włączony).
- **Odtwarzacz (Media3, HLS, OkHttp):** nakładka z nazwą, status buforowania, panel błędu. **Failover:** `BkFailoverPolicy` (stałe ze spec) + `FailoverCoordinator` (ponowienia, rebuffery, wymuszenie po opóźnieniu TV, cooldown, maks. 2 cykle), komunikat „Przełączono na Lista n · nazwa”. Ustawienia failoveru (włącznik, czułość, opóźnienie TV) w DataStore.
- **Ekrany TV:** start (2 kolumny, wiersz „Ostatnio oglądane”: OK wznawia kanał), Dostawcy (M3U/Xtream), Na żywo (grupy, polskie na początku, ulubione długim OK, „teraz leci” z paskiem postępu), Przewodnik (siatka 12 h, poziome przewijanie fokusem), Odtwarzanie (failover), Źródła EPG, Wygląd. Wszystko wykonalne D-pad + OK + BACK.
- **Naprawy po testach na prawdziwej liście 29 MB (100 tys. wpisów):** import 8,1 s → 0,84 s, strumieniowe czytanie listy (`TextFetcher.fetchLines`, limit rozmiaru także po rozpakowaniu gzip), pusta grupa jako „Bez grupy”, przewijanie do wybranej grupy, stała szerokość znaczka kanału.
- **Scenariusze E2E na realistycznych danych (17):** pełny graf Hilt, prawdziwy Room i HTTP, UI sterowany dotykiem i pilotem (`ScenariosTest`): lista iptv-org, Xtream (dobre i złe hasło), ulubione, failover (ponowienie, przełączenie, wszystkie zepsute, wyłączony, zawieszone buforowanie), EPG z listy i po nazwie, lista 29 MB, odświeżenie z awarią serwera, restart aktywności, motyw, OK z przewodnika, Enter w formularzu. Reguła w `CLAUDE.md`: każda funkcja ma scenariusz E2E.
- **Weryfikacja na emulatorze Android TV (CI, prawdziwy Keystore i ExoPlayer):** dodanie dwóch list przez interfejs (Enter w polach), pobranie z lokalnego serwera, lista A z 404, przełączenie na listę B i odtwarzanie HLS z ffmpeg (zrzuty `docs/qa/emulator/06–13`, log serwera potwierdza segmenty 200).
- **Błędy znalezione przez scenariusze i naprawione:** crash zapisu dostawcy (Keystore odrzucał IV podany przez wywołującego), DÓŁ w polu tekstowym trafiał w klawiaturę (teraz Enter: dalej/zapis), przewodnik nie pobierał EPG bez wizyty w „Na żywo”, ExoPlayer ponawiał 404 siedem razy (teraz 4xx kończy od razu), liczba mnoga zależna od języka urządzenia („Kanały: N”), start ekranu startowego w dwóch kolumnach.
- **Crash przy starcie** (`rememberSaveable` na `SnapshotStateList`) naprawiony; `AppStartTest` z prawdziwym grafem Hilt zapobiega regresji.

## DoD — checklista
| Punkt | Stan | Dowód |
|---|---|---|
| Dostawca M3U: dodanie, odświeżenie, usunięcie | ✅ | `CatalogRepositoryTest`, `ProvidersScreenTest` |
| Dostawca Xtream | ✅ | `XtreamTest`, `CatalogRepositoryTest` |
| Brak sekretów w bazie i repo | ✅ | test bajtów bazy, gitleaks w CI |
| EPG: import, dopasowanie, podmiana atomowa | ✅ | testy `core:data/epg`, `XmlTvParserTest` |
| Stalker | ❌ | poza zakresem tej iteracji |
| Filmy i seriale (M3U/Xtream) | ❌ | świadomie pominięte (`liveOnly`), ekrany VOD w kolejnej fazie |
| Failover: logika | ✅ | 10 testów na wirtualnym czasie |
| Failover: golden master ze starej aplikacji | ✅ | `BkFailoverPolicyGoldenTest` (fixture wygenerowany kodem referencyjnym poza repo) |
| Failover na prawdziwym ExoPlayerze: 404 na pierwszej liście, przełączenie na drugą | ✅ | emulator w CI (zrzuty 12–13, log serwera: 2× 404, segmenty 200) |
| Failover: zacięcie i błędy 5xx na prawdziwym ExoPlayerze | ⏳ | logika na wirtualnym czasie i E2E z atrapą; emulator tylko 404 |
| Zapis dostawców na prawdziwym Android Keystore | ✅ | emulator w CI (`ui_po_zapisie_A.xml`: „Kanały: 1”) |
| Ekrany: Roborazzi + test fokusu D-pad | ✅ | każdy nowy ekran ma oba |
| Emulator Android TV w CI: start i nawigacja | ✅ | `docs/qa/emulator/*.png` |
| Weryfikacja na prawdziwym TV | ❌ | do wykonania przez właściciela |

## Metryki
- Import listy 100 tys. wpisów (4578 na żywo): 0,84 s na JVM (Robolectric), wcześniej 8,1 s.
- Szczyt pamięci przy imporcie listy 29 MB (JVM): 226 MB (pełny tekst) → 72 MB (import strumieniowy `fetchLines`), po parsowaniu ok. 6 MB.

## Odstępstwa od konspektu
- M3U zapisuje tylko kanały na żywo (filmy i seriale w M3U pominięte do czasu ekranów VOD).
- Długie OK jako sposób na ulubione (zamiast osobnego przycisku), zgodne z regułą D-pad + OK + BACK.
- Podwyższony próg Detekt dla liczby funkcji w interfejsach (15), `CatalogRepository` jest fasadą katalogu.

## Ryzyka / znane problemy
- Pamięć przy bardzo dużych listach na prawdziwych słabych dekoderach nietestowana (import jest już strumieniowy; pomiar tylko na JVM).
- Przewodnik: okno 12 h od pełnej półgodziny (bez przesuwania na kolejne dni), OK na audycji otwiera kanał (bez szczegółów).
- Wygląd i fokus na prawdziwym TV (overscan, odległość, panel) niesprawdzone; emulator i Roborazzi pokrywają układ i fokus.
- Przy pierwszym dodaniu dostawcy ExoPlayer czeka na pierwszy segment; czas startu na słabym boxie niesprawdzony.
- Hasła podane w czacie przy testach: właściciel powinien je zmienić u dostawców.

## Pytania do właściciela
**Blokujące 🔒**
1. Czy fixture'y/golden master polityki failoveru ze starej aplikacji są dostępne? Bez nich zostaje status „wnioski ze spec”.
2. Test na prawdziwym TV box: dodanie listy `https://iptv-org.github.io/iptv/countries/pl.m3u` lub własnej, odtwarzanie, failover (jedna lista zepsuta, druga działa).

**Nieblokujące**
3. Czy filmy i seriale z M3U mają być w 1.0, czy dopiero z Xtream (VOD API)?

## Plan następnej fazy
Po teście bety na boxie przez właściciela: poprawki z listy błędów, potem VOD (filmy, seriale), szczegóły audycji, wyszukiwanie i Ustawienia ogólne.
