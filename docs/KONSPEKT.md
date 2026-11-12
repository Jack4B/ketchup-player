# Konspekt inżynieryjny — przepisanie od zera BK Player Next → **Ketchup Player** (Kotlin + Jetpack Compose for TV)

> **Odbiorca:** autonomiczny agent kodujący (Cloud AI).
> **Zleceniodawca / właściciel produktu:** Karesh Mubat.
> **Data:** 31.10.2026.
> **Stary projekt (tylko do odczytu, źródło wiedzy):** repo `bk-player-next` (Java 17, `pl.bkplayer.next.smartpreview`, v1.8.0.34 / code 636).
> **Materiały wejściowe:** `bk-player-next-ANALIZA.md`, `bk-player-next-PRZEBUDOWA.md`, gotowe logo `ketchup-player-logo.jpg` (patrz §4.0).
>
> Ten dokument jest **wiążący**. „MUSI” = nienegocjowalne. „ZALECANE” = możesz odstąpić, ale zapisujesz uzasadnienie jako ADR w `docs/adr/`.

---

## Spis treści
1. Cel i kontekst
2. Zakres funkcjonalny (parytet funkcji)
3. Stack i architektura docelowa
4. UX / makiety (w tym §4.0 Branding — gotowe logo)
5. Obsługa pilota (D-pad)
6. Przegląd i przeprojektowanie logiki menu
7. Nowe funkcje (kaskadowy failover, nowe odcinki)
8. Bezpieczeństwo
9. Migracja danych i ustawień użytkownika
10. Przenoszenie logiki ze starej aplikacji
11. Rekomendacje eksperta — o czym użytkownik mógł nie pomyśleć
12. Plan pracy w fazach dla Cloud AI
13. Ograniczenia i zasady
- Załącznik A: mapa „stara klasa → nowy moduł”
- Załącznik B: klucze ustawień do migracji
- Załącznik C: szablon raportu z etapu

---

## 1. Cel i kontekst

### 1.1 Co budujemy
Nową aplikację IPTV na **Android TV / Google TV / Fire TV / TV Boxy z Androidem** (cel główny) oraz telefony/tablety (cel drugorzędny, ten sam APK), napisaną **od zera** w **Kotlinie + Jetpack Compose (Compose for TV, `androidx.tv:tv-material`)**, z:
- pełnym parytetem funkcji starej aplikacji (§2),
- **nowymi makietami i nowym UI** pod „10-foot UI” (§4),
- **wzorcową obsługą pilota** zgodną z wytycznymi Android TV (§5),
- **uporządkowaną hierarchią menu** (§6),
- dwiema nowymi funkcjami flagowymi: **kaskadowy failover list** i **powiadomienia o nowych odcinkach** (§7),
- bezpieczeństwem na poziomie aplikacji produkcyjnej (§8),
- nowym brandingiem **Ketchup Player** z gotowego logo (§4.0).

### 1.2 Dlaczego rewrite, a nie refaktor
Wcześniejszy plan (`PRZEBUDOWA.md`) rekomendował przyrostowy refaktor (strangler fig). **Właściciel świadomie zmienił decyzję na greenfield** — ten konspekt przyjmuje to jako fakt. Uzasadnienie:

| Fakt o starej aplikacji | Konsekwencja |
|---|---|
| Aplikacja **nie działa w pełni i jest niestabilna** — nie ma stabilnego punktu odniesienia, który warto chronić 1:1 w warstwie UI. | Nie odtwarzamy starego UI piksel w piksel. |
| `Main.java` ma **~8 330 linii** i splata Home / TV / EPG / VOD / Seriale / Radar / Player / Failover; ~170 miejsc z `post`/`runOnUiThread`, wiele executorów w Activity (ryzyko wycieków przy recreate). | Wyjmowanie logiki z `Main` kosztuje mniej więcej tyle, co napisanie jej porządnie od nowa. |
| Cały UI budowany programowo w Javie (brak `res/layout`, brak Compose, brak Leanback). | Brak komponentów UI nadających się do ponownego użycia. |
| ~146 z 238 testów to **testy „tekstowe”** (czytają źródła `Main.java`), nie testy zachowania. | Przy każdej przebudowie i tak padną — nie są siatką bezpieczeństwa. |
| Właściciel chce **nowego UI, nowych makiet i nowego brandingu**. | Wygląd stref FROZEN zostaje **odmrożony decyzją właściciela**; zachowanie logiki chronimy osobno (§10.3). |

### 1.3 Czego NIE wyrzucamy (kluczowe)
Rewrite **nie oznacza odkrywania wszystkiego od nowa**. Największa wartość starego projektu to wiedza zaszyta w:
- **~70 klasach `*Policy` / `*Match` / `*Plan`** — czyste funkcje decyzyjne (bufor, failover, dopasowanie kanałów/filmów, nawigacja, pamięć),
- logice dostawców (Xtream `player_api.php` / `get.php` / `xmltv.php`, Stalker: MAC + token odświeżany co 120 s, paginacja),
- failoverze i smart-buforze (`BkFailoverPolicy`, `BkTvFailoverMatch`, `BkVodFailoverMatch`, `BkSmartBufferPolicy`, `StreamRecoveryPolicy`, `TvBufferSessionPolicy`, watchdogi z `Main`),
- Radarze/Teleman (cache trwały, limity RAM, single-flight EPG, etapowanie 0–6 h / 6–12 h / 28 h),
- ~300 plikach `WERYFIKACJA-*` / `BK-UPDATE-*` z opisem quirków (Fire TV, 2 GB RAM, dostawca „365”, firewall DROP).

**Zasada nadrzędna:** logikę **portujemy/tłumaczymy** (Java → Kotlin, pod osłoną testów zachowania), UI **projektujemy od nowa**.

### 1.4 Kryteria sukcesu
- [ ] Zimny start do Home ≤ 2,5 s na Fire TV Stick 4K / boxie 2 GB RAM (z cache).
- [ ] Zap kanału (CH+/CH−) do pierwszej klatki ≤ 1,5 s na typowym strumieniu TS/HLS.
- [ ] Zero ANR i crashy w 2-godzinnym teście „monkey + pilot” na emulatorze Android TV.
- [ ] Każdy ekran w pełni obsługiwalny pilotem 5-kierunkowym + BACK (bez dotyku i myszy).
- [ ] Przełączenie failover na kolejną listę bez wyjścia z odtwarzacza, w czasie ≤ próg + 3 s.
- [ ] Brak sekretów w postaci jawnej na dysku i w logach.

---

## 2. Zakres funkcjonalny — checklista parytetu

Każdy punkt MUSI mieć: implementację, test i wiersz w `docs/PARITY.md` ze statusem. „Źródło” = gdzie w starym kodzie szukać zachowania referencyjnego.

### 2.1 Dostawcy
- [ ] **M3U / M3U8** — URL i plik lokalny; atrybuty `tvg-id`, `tvg-name`, `tvg-logo`, `group-title`, `catchup*`, `#EXTVLCOPT` (User-Agent/Referrer). *Źródło:* `Importers.java`.
- [ ] **Xtream Codes** — `player_api.php` (kategorie + strumienie live/vod/series, `get_series_info`, `get_vod_info`), `get.php`, `xmltv.php`, data wygaśnięcia konta, limit połączeń. *Źródło:* `Api.java`, `ServiceApi.java`, `ProviderDetails.java`.
- [ ] **Stalker / MAG portal** — handshake, logowanie MAC, token, odświeżanie co 120 s, `create_link`, paginacja; **bez catch-up** (zachowanie zastane). *Źródło:* `Api.java`.
- [ ] **Import TXT wielu dostawców** naraz (plik lub URL HTTP/HTTPS, `NAME` + auto-detekcja M3U/Xtream/Stalker). *Źródło:* `PRZYKLAD-IMPORT-WIELU-LIST.txt`, `Importers`.
- [ ] Status dostawcy, odświeżanie pojedyncze i „Odśwież wszystkich”, data wygaśnięcia. *Źródło:* `ProviderStatus.java`, `manageProviders()`.
- [ ] Per dostawca: profil bufora (np. „365 · stabilny TS · start 10 s · rebuffer 15 s”), dekoder (inherit/auto/hardware/software), User-Agent. *Źródło:* `providerBufferChoice`, `providerDecoderChoice`, `StreamUserAgent.java`.

### 2.2 Live TV + EPG
- [ ] Kanały w grupach, ukrywanie grup (`visibility:tv:provider:group`), ręczne sortowanie w grupie (tryb „Przenieś kanał”: GÓRA/DÓŁ, OK zapis, BACK anuluj), sortowanie alfabetyczne vs kolejność dostawcy.
- [ ] Ulubione, ostatnio oglądane, „Uruchamiaj ostatni kanał” przy starcie.
- [ ] **EPG XMLTV** (domyślnie `https://epg.ovh/pltv.xml.gz`), własne źródła XMLTV / `xmltv.php` przypisane do dostawcy lub „wszystkich”, horyzont godzin, aliasy kanałów.
- [ ] Single-flight EPG (to samo źródło nigdy nie jest pobierane/importowane równolegle); stary cache dostępny do atomowej podmiany nowego.
- [ ] Siatka EPG, „Teraz / Za chwilę”, postęp i pozostały czas, prefetch EPG przy przewijaniu kanałów.
- [ ] Rzędy TV: „Teraz warto”, „Za chwilę”, „Sport i inne” + klasyfikacja audycji. *Źródło:* `TvPremiumContentPolicy`, `TvCarouselPolicy`.
- [ ] Catch-up / archiwum (Xtream, M3U z `catchup`), nie dla Stalkera.
- [ ] HUD bufora, wskaźnik VPN, zegar w pełnym ekranie (opcja), AFR, PiP.

### 2.3 Radar (polski przewodnik „co warto obejrzeć”)
- [ ] Sekcje **TOP DNIA, Teraz, Za chwilę, Wieczorem**; dziś = Teleman, przyszłe dni (Jutro, +2, +3) = OVH.
- [ ] Metadane Teleman (opisy, oceny IMDb/Filmweb, gatunki) z **trwałym cache dyskowym** (`RadarTelemanPersistentCache`).
- [ ] „MOJE zainteresowania”, powiązanie audycji z kanałami, loga, szczegóły, odtwarzanie.
- [ ] Limity: do 800 kanałów, etapy 0–6 h → 6–12 h → 28 h, pełne 28 h **nie** indeksowane w RAM przy wejściu (ochrona 2 GB).
- [ ] Diagnostyka `BK-RADAR-TEXT`; Teleman za **feature flagą** (§13.3).

### 2.4 VOD
- [ ] Kategorie, sortowanie „od najnowszych” z fallbackiem dat (`added` / `last_modified` / `date` / `year`), wyszukiwanie (DÓŁ z pola → wyniki, nie kategorie).
- [ ] Szczegóły: backdrop, opis, ocena, reżyser, obsada, rok, czas, „Podobne”, zwiastun YouTube.
- [ ] Wznowienie (`CinemaResumePolicy`), „Kontynuuj oglądanie”, historia per profil.
- [ ] TMDB (token v4 podaje użytkownik, „TMDB automatycznie po zaznaczeniu”).
- [ ] Ukryte pozycje, blokada rodzicielska pozycji (`flags.locked`).

### 2.5 Seriale
- [ ] Kategorie, sortowanie, wyszukiwanie, sezony/odcinki (`SeriesEpisodePicker`, `SeriesEpisodePolicy`), autoodtwarzanie następnego odcinka, wznowienie.
- [ ] **NOWE:** wskaźnik nowych odcinków (§7.2).

### 2.6 Odtwarzacz
- [ ] Media3 ExoPlayer: HLS, DASH, TS, OkHttp DataSource, własny User-Agent/nagłówki.
- [ ] Ścieżki audio/napisów, napisy zewnętrzne (SRT/VTT), **OpenSubtitles** (klucz API + logowanie).
- [ ] Konfigurowalny czas nakładki, PiP, Cast, wykrywanie zamrożonego obrazu w stanie READY.

### 2.7 Failover i smart buffer
- [ ] Przełączniki globalnie / TV / VOD / seriale; czułość Szybka / Standardowa / Ostrożna; komunikat o zmianie; opóźnienie TV 5/10/15/25 s (domyślnie 25). *Źródło:* `BkFailoverPolicy`.
- [ ] VOD: zmiana źródła z zachowaniem pozycji; 20 s BUFFERING bez postępu, 25 s bez pierwszego READY, 20 s zablokowanego `resolve`, przedwczesny koniec ≥ 60 s przed znanym końcem.
- [ ] Smart Buffer 2.0: SMART / Szybki / Stabilny + zaawansowane (Mały/Średni/Duży/Bardzo duży); uczenie per kanał tylko po rebufferze ≥ 3 s; limit 50 s dla 2 GB RAM; profil dostawcy ma pierwszeństwo.
- [ ] Diagnostyka „Sprawdź zapasowe źródła oglądanego materiału”.
- [ ] **NOWE:** kaskada list rezerwowych (§7.1).

### 2.8 Nagrywanie
- [ ] Nagrywanie na żywo i z harmonogramu (foreground service), folder (urządzenie / NAS), lista nagrań, przypomnienia, przywracanie harmonogramu po `BOOT_COMPLETED`. *Źródło:* `Recorder.java`, `Schedule.java`.

### 2.9 Chromecast
- [ ] Sesja Cast; zmiana kanału w trakcie Cast zostaje na TV; powrót do lokalnego po rozłączeniu; wznowienie sesji bez restartu VOD. *Źródło:* `CastController.java`.

### 2.10 VPN WireGuard
- [ ] Wbudowany tunel (`com.wireguard.android:tunnel`), dostawcy Windscribe / Surfshark / PIA × 30 krajów, import `.conf`, status, szybka reakcja na włącz/wyłącz, wykrycie „tunelem zarządza inna aplikacja”. *Źródło:* `VpnBridge.java`, `VpnProviders.java`.

### 2.11 SMB / NAS
- [ ] Przeglądanie udziałów SMB2/3 (jcifs-ng), odtwarzanie plików, zapis nagrań na NAS; zachować wymogi LGPL (`third-party/`, źródła). *Źródło:* `Nas.java`.

### 2.12 Profile, PIN, backup, wygląd, pilot
- [ ] Profile, profil domyślny po starcie, kontrola rodzicielska (odblokowanie na 10 min), PIN 4–12 cyfr (hash + sól).
- [ ] Backup/restore „Ustawienia i listy” (`.bkl`) i „Pełny”: AES-GCM + PBKDF2-HMAC-SHA256 (120 000 iteracji), magic `BKP1–5` / `BKL1–5`, manifest v5/v7. **Nowa aplikacja MUSI wczytać stary backup** (§9).
- [ ] Motywy (nowe, z palety logo — §4.0), profil wydajności (auto/słaby/mocny), limit cache grafik (64/128/256/512 MB).
- [ ] Pilot: klawisze kolorowe, MENU, GUIDE, MEDIA_NEXT/PREVIOUS; tryb zgodności Android 10/starsze boxy; diagnostyka klawiszy.

---

## 3. Stack i architektura docelowa

### 3.1 Stack (MUSI)
| Obszar | Wybór | Uwagi |
|---|---|---|
| Język | **Kotlin 2.x (K2)**, JVM 17 | Zero nowej Javy. |
| UI | **Jetpack Compose + Compose for TV** (`androidx.tv:tv-material`) | **NIE Leanback** (tryb utrzymania). Listy: standardowe `LazyRow`/`LazyColumn`/`LazyVerticalGrid` z `compose.foundation` (TV-lazy z `tv-foundation` są zdeprecjonowane). Material 3 na telefon. |
| Wzorzec | **MVVM + UDF (MVI-lite)** | `ViewModel` → `StateFlow<UiState>`, `onEvent(UiEvent)`, jednorazowe efekty przez `Channel`. |
| Współbieżność | **Coroutines + Flow** | Zakaz `Thread`, `Handler.post`, `runOnUiThread`, ręcznych executorów w UI. Dispatchery wstrzykiwane. |
| DI | **Hilt** | `@HiltViewModel`, `@HiltWorker`. |
| Baza | **Room** (KSP), `exportSchema = true`, FTS4 | |
| Ustawienia | **DataStore** (Proto zalecane) | Typowane, nie JSON w kolumnie. |
| Tło | **WorkManager** (EPG, katalog, nowe odcinki, prefetch Radaru) | Nagrywanie: foreground service (typ zgodny z targetSdk). |
| Odtwarzacz | **Media3**: exoplayer, hls, dash, datasource-okhttp, session, ui-compose, cast | `MediaSessionService`. |
| Sieć | **OkHttp** + **kotlinx.serialization** (Retrofit ZALECANE dla TMDB/OpenSubtitles/Xtream) | Dwa klienty: `MetadataHttpClient` (tylko TLS), `StreamHttpClient` (dopuszcza HTTP). |
| Obrazy | **Coil 3** | Limity cache z profilu wydajności. |
| Parsowanie | XMLTV: `XmlPullParser` strumieniowo + gzip strumieniowo; HTML Teleman: **Jsoup** | |
| Kryptografia | **Tink** (keyset chroniony kluczem z **Android Keystore**) | `EncryptedSharedPreferences` jest zdeprecjonowane — nie używać. |
| Logi | **Timber** + `RedactingTree` | §8.4. |
| Build | Gradle **Kotlin DSL**, **version catalog**, **convention plugins** (`build-logic/`) | |
| Jakość | ktlint/Spotless, **Detekt** (zakaz pustych catch), Android Lint (warnings = errors w CI) | |
| Testy | JUnit, **Turbine**, **MockK**, **MockWebServer**, Robolectric, **Roborazzi**, Compose UI Test, **Macrobenchmark** + Baseline Profiles | |
| SDK | `minSdk 28` (Android 9 — Fire TV i starsze boxy), `targetSdk 35` (lub wymagany przez Play), `compileSdk 35` | podniesione z 26 wg decyzji; patrz sekcja „Kompatybilność i wymagania platformy” |

### 3.2 Moduły (MUSI — drobne można scalić, reguł zależności nie wolno łamać)
```
:app                     – Application, jedyna MainActivity, NavHost, DI root, branding
:build-logic             – convention plugins

:core:model              – typy domenowe (Channel, VodItem, Series, Episode, EpgProgramme,
                           ProviderConfig, StreamSpec, PlayRequest, SourceCandidate) — czysta JVM
:core:policy             – PORT *Policy/*Match/*Plan + testy zachowania — czysta JVM, zero android.*
:core:common             – Result/AppError, dispatchers, Clock, redakcja sekretów
:core:network            – OkHttp (2 klienty), interceptory (UA, redakcja, retry/backoff)
:core:database           – Room: encje, DAO, migracje, LegacyDbImporter
:core:datastore          – DataStore ustawień
:core:security           – SecretStore (Tink/Keystore), PinHasher, LegacyBackupCrypto
:core:data               – repozytoria: Catalog, Epg, Metadata, Subtitles, Settings, Resume, History
:core:designsystem       – motyw Ketchup, typografia TV, komponenty (FocusCard, PosterCard, Badge…)
:core:ui                 – wspólne composable, narzędzia focusu, stany ładowania/błędu
:core:testing            – fake'i, fixture'y (Xtream/Stalker/M3U/XMLTV/HTML Teleman)

:provider:api            – IptvProvider + Capability + abstrakcyjne testy kontraktowe
:provider:m3u | :provider:xtream | :provider:stalker
:epg:xmltv               – parser strumieniowy, import batchowy, aliasy, single-flight
:epg:teleman             – scraping (izolowany, feature flag), cache trwały
:epg:radar               – silnik Radaru (port Radar*Policy), bez UI
:player:engine           – PlayerController, impl. Media3, PlaybackService, LoadControlFactory,
                           detektor zamrożonego obrazu, diagnostyka
:player:failover         – FailoverCoordinator, kaskada list (§7.1), watchdogi, SmartBuffer

:feature:home | :feature:livetv | :feature:guide (EPG + Radar) | :feature:vod | :feature:series
:feature:details | :feature:player-ui | :feature:search | :feature:mylist | :feature:settings
:feature:providers | :feature:profiles | :feature:recording | :feature:onboarding
:integration:cast | :integration:vpn | :integration:nas | :integration:tvprovider (Watch Next)
```
**Reguły zależności (egzekwowane w CI):**
- `feature:*` → `core:*`, API `player:*`, `provider:api`. **Feature nie zależy od innego feature**; nawigacja przez `:app` (kontrakty tras w `core:ui`). To jest realna separacja TV ↔ VOD, której w starej aplikacji brakowało.
- `core:model`, `core:policy` — czysta JVM, **zero** `android.*`.
- `provider:*` nie znają Room ani UI.
- Tylko `:core:security` dotyka kluczy i sekretów.

### 3.3 Abstrakcja dostawców (MUSI)
```kotlin
interface IptvProvider {
    val id: ProviderId
    val type: ProviderType                 // M3U, XTREAM, STALKER
    val capabilities: Set<Capability>      // LIVE, VOD, SERIES, CATCHUP, EPG_URL, ACCOUNT_INFO
    suspend fun authenticate(): AuthResult
    suspend fun accountInfo(): AccountInfo?            // wygaśnięcie, limit połączeń
    fun liveChannels(): Flow<List<Channel>>            // partiami — 10k+ kanałów
    suspend fun categories(kind: ContentKind): List<Category>
    suspend fun vod(page: PageRequest): Page<VodItem>
    suspend fun series(page: PageRequest): Page<Series>
    suspend fun seriesDetails(seriesId: String): SeriesDetails   // sezony + odcinki (+ last_modified)
    suspend fun resolveStream(playable: Playable): StreamSpec    // URL + nagłówki; Stalker: create_link
    fun epgSources(): List<EpgSourceSpec>
}
```
- **`resolveStream` późno:** w bazie tylko ID strumienia; URL z loginem/hasłem budowany w pamięci przy odtwarzaniu z `SecretStore`. Pełnego URL Xtream nie zapisujemy nigdy (DB, logi).
- `StalkerSessionManager`: LRU per portal + odświeżanie tokenu co 120 s w coroutine. Stary `sessions.clear()` przy > 32 sesjach wylogowywał wszystkie portale — nie powtarzać.
- `ProviderRegistry` budowany z `ProviderEntity` + sekretów.
- **Testy kontraktowe:** jeden `IptvProviderContractTest` uruchamiany na 3 implementacjach z MockWebServer i zanonimizowanymi fixture'ami.

### 3.4 Odtwarzacz (MUSI)
```kotlin
interface PlayerController {
    val state: StateFlow<PlaybackState>     // Idle/Preparing/Buffering/Ready/Ended/Error + pozycja, ścieżki, źródło
    val events: Flow<PlaybackEvent>         // diagnostyka, failover, stall, frozen-frame
    fun play(request: PlayRequest)          // źródło + łańcuch kandydatów + typ LIVE/VOD/EPISODE/CATCHUP
    fun switchSource(candidate: SourceCandidate, keepPosition: Boolean)
    fun stop(); fun seekTo(ms: Long); fun selectTrack(selection: TrackSelection)
}
```
- Player żyje w **`PlaybackService : MediaSessionService`** — przeżywa nawigację, PiP, Cast (`CastPlayer`), klawisze multimedialne przez `MediaSession`.
- `LoadControlFactory` bierze parametry z portu `BkSmartBufferPolicy`. **Semantyka zachowana:** nowy `LoadControl` działa dopiero w nowej instancji playera — failover z innym profilem bufora tworzy nowy player podpięty do tego samego surface, bez zmiany ekranu.
- UI w Compose: `PlayerSurface` (media3-ui-compose). `PlayerView` w `AndroidView` tylko, jeśli benchmark na konkretnym boxie wykaże problem (ADR).

### 3.5 Warstwa danych (MUSI)
- Encje: `ProviderEntity` (bez sekretów, z `secretRef`), `ProviderPriorityEntity` (§7.1), `ChannelEntity` (`providerId`, `streamId`, `name`, `normName`, `number`, `epgId`, `logo`, `group`, `catchup`), `CategoryEntity`, `VodEntity`, `SeriesEntity`, `EpisodeEntity`, `EpgProgrammeEntity` (indeks `channelKey, start`), `ChannelAliasEntity`, `FlagEntity` (fav/hidden/locked/rank/position), `GroupVisibilityEntity`, `ResumeEntity`, `HistoryEntity` (per profil), `ProfileEntity`, `RecordingEntity`, `ScheduleEntity`, `SeriesWatchStateEntity` (§7.2), `SourceHealthEntity` i `ChannelEquivalenceEntity` (§7.1), FTS: kanały / VOD / seriale.
- EPG: wstawianie batchowe w transakcji (~2 000 wierszy), czyszczenie starszych niż N dni, import do tabeli tymczasowej + atomowa podmiana (odpowiednik „stary cache do końca przebudowy”).
- Repozytoria: odczyt jako `Flow`, komendy `suspend`; offline-first (UI zawsze czyta z Room).
- Import starej bazy `bk-next.db` (schemat v6: `config`, `items`, `flags`, `membership`, `guide`, `aliases`) — §9.

---

## 4. UX / makiety

### 4.0 Branding — gotowe logo (MUSI; NIE projektować od nowa)
- **Logo jest gotowe** i dołączone do zlecenia: `ketchup-player-logo.jpg` (2752 × 1536 px, JPG/RGB, **bez kanału przezroczystości**, jednolite ciemne tło ≈ `#111217`). Przedstawia maskotkę-pomidora w neonowych goglach i czarnej bluzie z czerwonym przyciskiem ▶ na piersi, pentagonalną tarczę ze złotą ramką, butelkę ketchupu w kominiarce, małego pomidora z laptopem, krople ketchupu, pikselowe „glitche” i napis **„KETCHUP PLAYER”** złotymi literami na czerwonej wstędze.
- **Cloud AI NIE projektuje logo, NIE generuje alternatywnej maskotki, NIE zmienia napisu ani kolorów logo.** Ten plik jest jedynym źródłem brandingu. Oryginał trafia do repo nietknięty jako `branding/source/ketchup-player-logo.jpg`.
- Z oryginału wygeneruj (odtwarzalny skrypt `tools/branding/generate.py` lub Gradle task; wyniki commitowane):
  - [ ] **Wariant z przezroczystym tłem** `branding/derived/logo-transparent.png` — usunięcie jednolitego tła (maska po kolorze/flood-fill od krawędzi, ewentualnie `rembg`) z zachowaniem kropli i glitchy; kontrola krawędzi na jasnym i ciemnym tle (bez halo).
  - [ ] **Sygnet** `logo-mark.png` — wycięta głowa pomidora w goglach (przezroczyste tło), do małych rozmiarów.
  - [ ] **Ikona launchera (adaptive icon, API 26+):** foreground = sygnet (cały logotyp z napisem jest nieczytelny w 48 dp), mieszczący się w bezpiecznym kole 66 dp z 108 dp; background = jednolity `#111217`. **Warstwa monochromatyczna** (themed icons Android 13+) = sylwetka głowy / ▶.
  - [ ] Legacy mipmapy `mdpi…xxxhdpi` (48–192 px) + `play-store-icon-512.png`.
  - [ ] **Banner Android TV** `banner.png` **320 × 180 px** (xhdpi) + 640 × 360 (xxhdpi): tarcza + napis „KETCHUP PLAYER” wyśrodkowane na `#111217`; napis MUSI być czytelny na kafelku launchera Google TV i Fire TV.
  - [ ] **Splash** (SplashScreen API, Android 12+; biblioteka `core-splashscreen` dla 26–31): ikona = sygnet, tło `#111217`, opcjonalnie pulsujący ▶ ≤ 1 s. Bez osobnej Activity splash.
  - [ ] Wersja pozioma (tarcza + napis) do szuflady nawigacji, onboardingu i „O aplikacji”; `feature-graphic` 1024 × 500 do sklepów.
- **Paleta motywu wyprowadzona z logo** (wartości ostateczne spróbkuj z pliku): tło `#111217`, powierzchnie `#1A1C22` / `#24262E`, akcent pomidorowy/neon ≈ `#E5322D`, złoto ≈ `#D4A64A` (ring focusu, wyróżnienia), zieleń szypułki ≈ `#3DBE4B` — **zarezerwowana wyłącznie dla wskaźnika nowych odcinków** (§7.2), żeby zielony zawsze znaczył jedno. Kontrast tekstu ≥ 4.5:1.
- Nazwa w UI: **„Ketchup Player”**. Stare `branding/BK-Player-logo-1024.png` — do archiwum, nie używać.
- 🔒 Do potwierdzenia przez właściciela przed release: `applicationId` (propozycja `pl.ketchupplayer.tv`; patrz §9.1).

### 4.1 Zasady 10-foot UI (MUSI)
- Oglądanie z ~3 m: tekst body ≥ **18 sp**, tytuły rzędów 22–24 sp, tytuł hero 40–48 sp; dla < 20 sp min. grubość Medium.
- **Overscan / safe area:** 48 dp poziomo, 27 dp pionowo.
- **Focus wyraźny:** skala 1.05–1.08 + obrys 3 dp (złoto/biel) + poświata + rozjaśnienie — co najmniej dwa sygnały naraz, nie tylko kolor. Animacja focusu ≤ 150 ms.
- **Hit-area:** każdy fokusowalny element ≥ 48 dp w obu osiach; przyciski akcji ≥ 56 dp wysokości.
- Rzędy przewijane poziomo, ekran pionowo; jeden poziom poziomego przewijania na rząd; zero ukrytych gestów.
- Ciemny motyw domyślnie, bez czystej bieli na dużych powierzchniach.
- Każdy ekran ma stany: ładowanie (szkielet), pusty (z akcją, np. „Dodaj listę”), błąd („Spróbuj ponownie”), offline.
- Responsywność: TV 720p/1080p/4K, tablet, telefon landscape — `WindowSizeClass` przełącza nawigację (szuflada ↔ bottom bar).

### 4.2 Makiety do zaprojektowania (MUSI — jako `@Preview` 1920×1080 + screenshoty Roborazzi w `docs/mockups/`)

**A. Home**
```
┌──────────────────────────────────────────────────────────────────────┐
│[≡] 🍅 Ketchup Player           🔍 Szukaj        👤 Profil  21:37 VPN●│
│┌──────────── HERO (≈ 40% wysokości) ──────────────────────────────┐  │
││ backdrop + gradient w lewo │ TYTUŁ · 2024 · ★7.8 · 2h10          │  │
││                            │ opis (2 linie)                      │  │
││                            │ [▶ Oglądaj] [+ Moja lista] [i]      │  │
│└──────────────────────────────────────────────────────────────────┘  │
│ Kontynuuj oglądanie ▸ [kafel 16:9 + pasek postępu + „S2E5”] …        │
│ Na żywo teraz       ▸ [logo kanału + tytuł audycji + postęp] …       │
│ Nowe odcinki ●      ▸ [plakaty seriali z zielonym znacznikiem] …     │
│ Radar · TOP DNIA    ▸ …                                              │
│ Ulubione kanały · Ostatnio dodane filmy · Polecane (TMDB) …          │
└──────────────────────────────────────────────────────────────────────┘
```
- Hero aktualizuje się z debounce 400 ms po focusie (nie przy każdym przeskoku), crossfade backdropu 250 ms.
- „Kontynuuj oglądanie” zawsze pierwszy rząd, jeśli niepusty; długie OK → „Usuń z kontynuacji / Oznacz jako obejrzane”.
- Rzędy konfigurowalne (włącz/wyłącz, kolejność) w Ustawienia → Wygląd → Home.

**B. Na żywo (lista + podgląd)**
```
┌ Grupy ──────┬ Kanały ──────────────────────┬ Podgląd na żywo ─────────────┐
│ ★ Ulubione  │ 101 [logo] TVP1  Wiadomości  │ [ wideo 16:9 ]               │
│ Ostatnie    │     ▓▓▓▓▓░░ 19:30–20:00      │ Wiadomości · 19:30–20:00     │
│ Polskie     │ 102 [logo] TVN   Fakty       │ opis…   Następnie: Sport     │
│ Sport       │ 103 …                        │ Źródło: Lista 1 · Dostawca A │
└─────────────┴──────────────────────────────┴──────────────────────────────┘
```
- OK na kanale = pełny ekran; PRAWO = szczegóły audycji / mini-EPG kanału; LEWO = grupy. Numer kanału i postęp audycji widoczne zawsze.

**C. Przewodnik EPG (siatka czasowa)** — nowoczesny układ (Google TV Live / TiviMate):
- Oś czasu u góry (sloty 30 min, ~240 dp), kolumna kanałów 280 dp z logo i numerem, czerwona pionowa linia „teraz”.
- Wiersz 72 dp; komórka ≥ 48 dp (krótkie audycje skracane „…”, pełny tytuł w panelu podglądu).
- Panel szczegółów na górze (tytuł, czas, opis, kategoria, ocena z Radaru), aktualizowany przy focusie.
- LEWO/PRAWO po audycjach; **FAST-FORWARD/REWIND = ±1 h**, CH± = strona kanałów; OK na przeszłej audycji z archiwum = catch-up, na przyszłej = Przypomnij/Nagraj.
- Implementacja w Compose z wirtualizacją 2D (własny `LazyLayout` albo `LazyColumn` wierszy ze wspólnym poziomym przesunięciem czasu). **Fallback:** port `GuideView` jako custom View w `AndroidView`, jeśli Macrobenchmark na boxie 2 GB pokaże > 5% janky frames — decyzja przez ADR po pomiarze.
- Sekcja Przewodnik ma zakładki: **Siatka | Radar | Moje zainteresowania** (§6).

**D. Filmy (VOD)** — zwijany pasek kategorii po lewej, siatka plakatów 2:3 (6 kolumn na 1080p), chipy sortowania (Najnowsze / A–Z / Ocena / Rok), pole wyszukiwania u góry (DÓŁ → wyniki). Plakat: tytuł 2 linie (marquee tylko na focusie), rok, ocena, pasek postępu jeśli rozpoczęty.

**E. Seriale** — jak Filmy + znacznik nowych odcinków (§7.2) + filtr „Z nowymi odcinkami”.

**F. Szczegóły (film / serial)** — film bez pionowego scrolla na 1080p (zachowane z „CINEMA-DETAILS-BEZ-SCROLL”):
- Pełny backdrop; po lewej tytuł, meta (rok · czas · ★ · gatunki · reżyser), opis 4 linie, przyciski **[▶ Oglądaj / Wznów 34:12] [Od początku] [Zwiastun] [+ Moja lista] [Źródła (3)]**.
- Serial: chipy sezonów → rząd odcinków (kafle 16:9: numer, tytuł, czas, postęp, etykieta **„NOWY”**).
- Dolny rząd „Podobne”. „Źródła (N)” = lista dostawców, u których znaleziono tytuł (diagnostyka failover VOD) z ręcznym wyborem.

**G. Odtwarzacz (overlay)**
```
┌──────────────────────────────────────────────────────────────────────┐
│ 102 TVN · Fakty 19:00–19:30                Lista 2 · Dostawca B ⚡ 6 s│
│                              (wideo)                                 │
│ ▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░ 12:03 / 45:00            (VOD: seek-bar)        │
│ [⏮] [⏪] [⏯] [⏩] [⏭]    [Audio] [Napisy] [Źródło] [Info] [⋯]        │
└──────────────────────────────────────────────────────────────────────┘
```
- Overlay znika po czasie z ustawień (domyślnie 3 s); pierwsze naciśnięcie dowolnego klawisza tylko go **budzi**, bez wykonywania akcji.
- Live bez overlay: GÓRA/DÓŁ = zmiana kanału (konfigurowalne), OK = mini-lista kanałów nad wideo.
- Toast failover: „Przełączono na Lista 2 · Dostawca B (Lista 1 nie odpowiada)” — 4 s, nie zabiera focusu.

**H. Ustawienia** — układ dwupanelowy (kategorie po lewej, opcje po prawej), wartości po prawej stronie wiersza, przełączniki `Switch`, wybory `RadioButton`. Hierarchia w §6.

**I. Onboarding (pierwszy start)** — 3 kroki: język/profil → „Dodaj pierwszą listę” (M3U URL / Xtream / Stalker / Import TXT / **Importuj z BK Player**) → ładowanie z postępem (kanały → EPG → VOD).

### 4.3 Proces makiet
1. `core:designsystem`: tokeny (kolory, typografia, odstępy, kształty, focus) + galeria komponentów w stanach normal/focused/pressed/disabled.
2. Ekrany jako statyczne composable na danych fake (`core:testing`).
3. Screenshoty → `docs/mockups/*.png` → **akceptacja właściciela przed podpięciem logiki** (bramka fazy U).

---

## 5. Obsługa pilota (D-pad) — standard Android TV / Google TV

### 5.1 Zasady (MUSI)
- Po wejściu na ekran focus jest **zawsze** na sensownym elemencie (nigdy „nigdzie”): `FocusRequester` + `LaunchedEffect` po gotowości danych i układu.
- **Przywracanie focusu:** po BACK focus wraca na element, z którego wyszliśmy — `Modifier.focusRestorer()` na rzędach/siatkach + zapamiętany **klucz** (ID, nie indeks) w `SavedStateHandle`. Po odświeżeniu danych focus zostaje na tym samym ID.
- Przewidywalność: ruch zawsze zgodny z kierunkiem, bez „teleportów”; wyjście z rzędu w pionie trafia na element zapamiętany w danym rzędzie.
- `focusProperties { enter / exit }` dla wejścia w szufladę i panele; **LEWO na pierwszym elemencie** otwiera szufladę nawigacji (tylko ekrany główne).
- Fokusowalne elementy z komponentów `tv-material` (`Card`, `Surface`, `ClickableSurface`, `ListItem`) — nie gołe `focusable()` + `clickable()` bez stanu wizualnego.
- Brak pułapek focusu: dialog przejmuje focus, BACK zawsze zamyka.
- Scroll do focusu z wyprzedzeniem (`BringIntoViewSpec`, pivot ~30% od krawędzi — styl Google TV), żeby następny element był widoczny.

### 5.2 Mapowanie klawiszy (MUSI; „konfig.” = konfigurowalne)
| Klawisz | Kontekst | Akcja |
|---|---|---|
| `DPAD_CENTER` / `ENTER` / `NUMPAD_ENTER` / `BUTTON_A` | wszędzie | aktywacja (krótkie) |
| **długie OK** (≥ 500 ms) | kafel / kanał | menu kontekstowe (Ulubione, Ukryj, Przenieś, Szczegóły, Nagraj, Usuń z Kontynuuj, Oznacz jako obejrzane) |
| `BACK` / `ESCAPE` / `BUTTON_B` | wszędzie | poziom wstecz; pełny ekran: pokaż overlay → schowaj → wyjdź; Home: drugie BACK w 2 s lub dialog „Zamknąć?” |
| `MENU` | odtwarzacz | panel szybkich opcji (Źródło, Audio, Napisy, Bufor, Proporcje, Wyłącznik czasowy, Info) |
| `MENU` | listy | menu kontekstowe (jak długie OK) |
| `GUIDE` | wszędzie | Przewodnik |
| `CHANNEL_UP/DOWN`, `PAGE_UP/DOWN` | live / listy | następny/poprzedni kanał; w listach — strona |
| `DPAD_UP/DOWN` | live bez overlay | zmiana kanału (konfig.) |
| Cyfry `0–9` | live | wpisywanie numeru z OSD, zatwierdzenie po 1,5 s lub OK |
| `LAST_CHANNEL` | live | poprzedni kanał |
| `MEDIA_PLAY_PAUSE` / `PLAY` / `PAUSE` / `STOP` | odtwarzacz | standard (przez `MediaSession`) |
| `MEDIA_FAST_FORWARD` / `REWIND` | VOD | przewijanie z akceleracją przy przytrzymaniu: 10 s → 30 s → 60 s → 5 min (zmiana co ~1 s) |
| `DPAD_LEFT/RIGHT` | VOD, seek-bar | krok 10 s, przytrzymanie = akceleracja; podgląd czasu docelowego, faktyczny seek po puszczeniu (debounce 600 ms) |
| `MEDIA_NEXT/PREVIOUS` | serial / live | następny/poprzedni odcinek / kanał |
| `PROG_RED/GREEN/YELLOW/BLUE` | wszędzie | skróty (konfig.; domyślnie: Czerwony = Nagraj, Zielony = Ulubione, Żółty = Audio/Napisy, Niebieski = Źródło) |
| `INFO` / `CAPTIONS` | odtwarzacz | informacje / przełącz napisy |
| `SEARCH` / mikrofon | wszędzie | wyszukiwanie globalne (głosowe, jeśli dostępne) |

### 5.3 Implementacja (MUSI)
- Jeden **`KeyActionDispatcher`** w root composable (`onPreviewKeyEvent`) mapuje `KeyEvent → AppKeyAction` (port `BkRemoteScrollPolicy` + mapowanie klawiszy kolorowych z ustawień); ekrany odbierają akcje przez `CompositionLocal`. Lokalne `onKeyEvent` tylko dla specyfiki ekranu (seek-bar, wpisywanie numeru).
- Long-press: `ACTION_DOWN` + `repeatCount`/czas; **akcja krótka wykonywana na `ACTION_UP`**, jeśli nie było long-press (inaczej long-press odpala też short).
- **Szybkie przewijanie list:** przy przytrzymaniu (`repeatCount > 0`) przewijanie bez animacji focusu (0–50 ms), wstrzymanie ładowania obrazów niższego priorytetu, aktualizacja hero/panelu szczegółów po 300–400 ms bezruchu. Port `BkRemoteScrollPolicy`, `HomeCarouselMotionPolicy`.
- Throttling: maks. ~20 kroków/s przy przewijaniu (stare boxy wysyłają repeat co 30–50 ms i zapychają kolejkę).
- **Tryb zgodności starszych pilotów** (port `legacyRemoteCompat`): `ENTER`/`NUMPAD_ENTER`/`BUTTON_SELECT` = OK, `BUTTON_B`/`ESCAPE` = BACK.
- **Diagnostyka klawiszy:** ekran pokazujący `keyCode`, `scanCode`, nazwę, `repeatCount`, urządzenie wejściowe + przypisanie nieznanego klawisza do akcji.
- Predictive Back: na TV wyłączony (jak w starej aplikacji, zgodność z Fire OS), na telefonie włączony; w Compose `BackHandler`.
- Air-mouse, mysz i dotyk muszą działać, ale nie są wymagane do żadnej akcji.
- **Wszystko musi być wykonalne zestawem minimalnym: D-pad + OK + BACK (+ Play/Pause)** — piloty CEC z telewizora często nie mają nic więcej; klawisze kolorowe, cyfry i MENU to tylko skróty.

### 5.4 Testy pilota (MUSI)
- Compose UI testy z `performKeyInput { pressKey(Key.DirectionRight) }` dla ścieżek krytycznych (Home → kafel → szczegóły → odtwarzanie → BACK → focus na tym samym kaflu; Live → zap → EPG → BACK).
- Test „focus nigdy nie ginie”: po każdym kroku scenariusza asercja, że istnieje węzeł z focusem.
- Ręczna matryca: Fire TV Stick (Fire OS 7/8), Chromecast z Google TV, Xiaomi Mi Box S, tani box AOSP Android 9/10 (Amlogic/Rockchip) z pilotem IR, pilot BT Fire TV, air-mouse.

---

## 6. Przegląd i przeprojektowanie logiki menu

### 6.1 Stan obecny (fakty ze starego kodu)
**Boczna szyna** (`Main.java`, ok. l. 1242): `Home · TV · VOD · Seriale · EPG · Ulub. · Szukaj · ⎯ · Więcej · Opcje`. Radar osiągalny głównie z kafli/„docku” na Home (`premiumHomeDock`: TV / Filmy / Seriale / Radar / Moja lista / Szukaj).

**„Więcej”** (`moreMenu()`): `EPG · EPG i przewodnik · Historia i Kontynuuj oglądanie · Nagrania i przypomnienia · WireGuard VPN · Listy i dostawcy`.

**„Ustawienia BK player Next”** (`options()`; w szynie podpisane „Opcje”):
```
Wygląd ─ HOME·ustawienia i rekomendacje, Panel informacyjny ładowania, Monitor CPU/RAM/I/O, …
Odtwarzanie ─ Uruchamiaj ostatni kanał, AFR, Dekoder wideo, Buforowanie TV i VOD, Wskaźnik bufora,
              PiP, Autoodtwarzanie następnego odcinka, Zegar w pełnym ekranie, Czas nakładki,
              User-Agent HTTP, BK SMART FAILOVER 2.0                      (11 pozycji)
TV i EPG ─┬ TV i EPG ─ TV Premium, EPG (otwiera przewodnik!), EPG·MOJE zainteresowania,
          │            Pełna siatka (otwiera siatkę!), Źródła i aktualizacja EPG,
          │            Wygląd przewodnika i nawigacja, Sortowanie kanałów TV
          └ Nagrywanie i harmonogram ─ Folder nagrań, Nagrania, Zezwolenie na dokładne harmonogramy
VOD i Seriale ─ Sortowanie VOD, Sortowanie seriali, Zarządzanie grupami, Historia i Kontynuuj,
                Pokaż ukryte pozycje, Autoodtwarzanie następnego odcinka
Profile i PIN ─ Profile, Profil domyślny, Odblokuj kontrolę rodzicielską (10 min), Zmiana PIN
Pamięć i backup ─ Profil wydajności, Pamięć podręczna („Cache RAM” w środku przekierowuje
                  do profilu wydajności), Kopie zapasowe i dane
Pilot i integracje ─┬ Pilot i skróty
                    └ Napisy i integracje (= TMDB + OpenSubtitles)
VPN
Informacje ─ O aplikacji, Pamięć/dysk/wydajność, Diagnostyka biblioteki, Status list
```
Bufor i dekoder **per dostawca** są dostępne tylko w szczegółach dostawcy. Wszystko renderowane jednym generycznym `choose(tytuł, String[])` — płaska lista tekstów ze stanem doklejonym do etykiety.

### 6.2 Co jest nielogiczne / źle rozmieszczone
| # | Problem | Przykład |
|---|---|---|
| 1 | **Duplikaty wejść** — to samo miejsce w 3–4 ścieżkach, różnie nazwane. | EPG: szyna „EPG”, Więcej → „EPG”, Więcej → „EPG i przewodnik”, Ustawienia → TV i EPG → „EPG”. VPN: Więcej i Ustawienia. Historia: Więcej i VOD i Seriale. Nagrania: Więcej i TV → Nagrywanie. |
| 2 | **Nawigacja wymieszana z ustawieniami** — pozycje w Ustawieniach otwierają ekrany treści. | „EPG”, „Pełna siatka wszystkich kanałów”, „Nagrania”, „Historia” w menu ustawień. |
| 3 | **Ta sama opcja w dwóch miejscach** (ryzyko rozjazdu). | „Autoodtwarzanie następnego odcinka” w Odtwarzanie i w VOD i Seriale. |
| 4 | **Sztuczne poziomy pośrednie z 2 pozycjami.** | „TV i EPG” → („TV i EPG”, „Nagrywanie”); „Pilot i integracje” → („Pilot”, „Napisy i integracje”). |
| 5 | **Złe grupowanie semantyczne.** | TMDB/OpenSubtitles pod „Pilot”; Failover jako 11. pozycja Odtwarzania, a bufor/dekoder per dostawca zupełnie gdzie indziej; „Listy i dostawcy” w „Więcej”, a „Status list” w „Informacje”; User-Agent (sieć) w Odtwarzaniu. |
| 6 | **„Ulub.” w szynie to przełącznik filtra**, a nie miejsce (`favorites = !favorites`) — ukryty tryb, użytkownik nie wie, czy filtr jest aktywny. | |
| 7 | **Radar nie ma stałego miejsca w nawigacji** — tylko kafel na Home. | |
| 8 | **Niespójne nazewnictwo.** | Szyna „Opcje” vs tytuł „Ustawienia BK player Next”; „VOD” vs „Filmy” w docku; „EPG” vs „Przewodnik TV”. |
| 9 | **Stan doklejony do etykiety, w różnych formatach**, czasem surowe `true/false`. | „Uruchamiaj ostatni kanał: true”, „AFR: false” obok „WŁ./WYŁ.” i „WŁĄCZONY/wyłączony”. |
| 10 | **Za długie płaskie listy** bez podgrup i opisów. | „Odtwarzanie”: 11 pozycji mieszających wideo, bufor, sieć i UI nakładki. |
| 11 | **Opcje diagnostyczne wymieszane z użytkowymi.** | „Monitor CPU/RAM/I/O”, „Panel informacyjny ładowania” w „Wygląd”. |
| 12 | **Brak kontekstowego menu w odtwarzaczu** — zmiana źródła/bufora wymaga wyjścia do ustawień. | |

### 6.3 Nowa nawigacja główna
Boczna szuflada **`NavigationDrawer` (`tv-material`)** — zwinięta: ikony 56 dp; rozwija się po wejściu focusu (ikona + etykieta). Kolejność wg częstości użycia:
```
[🍅]  sygnet (niefokusowalny)
👤   Profil (avatar; zmiana profilu)
🔍   Szukaj
🏠   Home
📺   Na żywo           lista kanałów + podgląd
🗓   Przewodnik        zakładki: Siatka | Radar | Moje zainteresowania
🎬   Filmy
🎞   Seriale    ●      zielona kropka, gdy są nowe odcinki
⭐   Moja lista        zakładki: Ulubione kanały | Moja lista | Kontynuuj | Historia | Nagrania
────
⚙   Ustawienia
```
- Usunięte: „Więcej” (każda jego pozycja ma teraz jedno logiczne miejsce), osobny „EPG”, przełącznik „Ulub.”.
- Ulubione = **miejsce** (Moja lista) oraz **grupa „★ Ulubione”** na górze listy grup w „Na żywo” — stan zawsze widoczny.
- Telefon: `NavigationBar` z 5 pozycjami (Home, Na żywo, Filmy, Seriale, Więcej → reszta).

### 6.4 Nowa hierarchia Ustawień (max 3 poziomy; każda opcja w JEDNYM miejscu)
```
Ustawienia
├─ Listy i dostawcy
│   ├─ [Dostawca X] → dane logowania, status/wygaśnięcie, odśwież, bufor, dekoder, User-Agent,
│   │                 przypisane EPG, włącz/wyłącz, usuń
│   ├─ + Dodaj dostawcę (M3U / Xtream / Stalker) · Import TXT wielu list · Odśwież wszystkich
│   ├─ Kolejność i listy rezerwowe (failover)                     ← NOWE, §7.1
│   └─ Powiązania kanałów między listami                          ← NOWE, §7.1
├─ Odtwarzanie
│   ├─ Wideo: dekoder globalny, AFR, proporcje domyślne, jakość HLS/DASH
│   ├─ Buforowanie: SMART / Szybki / Stabilny · profile zaawansowane · wskaźnik bufora
│   ├─ Automatyczna zmiana źródła: globalnie/TV/VOD/seriale, czułość, opóźnienie TV, komunikaty
│   ├─ Audio i napisy: preferowane języki, styl napisów, OpenSubtitles automatycznie
│   └─ Zachowanie: uruchamiaj ostatni kanał, autoodtwarzanie następnego odcinka, czas nakładki,
│                   zegar w pełnym ekranie, PiP, GÓRA/DÓŁ zmienia kanał
├─ Telewizja i przewodnik
│   ├─ Źródła EPG (XMLTV / xmltv.php, przypisania, horyzont, przesunięcie czasu, odśwież)
│   ├─ Kanały: sortowanie, widoczność grup, numeracja
│   ├─ Radar: Teleman WŁ./WYŁ., moje zainteresowania
│   └─ Wygląd przewodnika: wideo w tle / podgląd, zwarte EPG, przezroczystość paneli
├─ Filmy i seriale: sortowanie domyślne, grupy (ukryj/kolejność), pokaż ukryte,
│                   powiadomienia o nowych odcinkach (§7.2)
├─ Nagrywanie: folder (urządzenie / NAS), dokładne harmonogramy (uprawnienie), zapas przed/po
├─ Sieć i VPN: WireGuard (dostawca, kraj, konfiguracje, status), User-Agent globalny, udziały SMB/NAS
├─ Profile i kontrola rodzicielska: profile, profil startowy, PIN, blokady kategorii/pozycji, limit wieku
├─ Wygląd: motyw, Home (rzędy i kolejność), rozmiar tekstu, ogranicz animacje
├─ Pilot: klawisze kolorowe, tryb zgodności starszych boxów, diagnostyka klawiszy
├─ Usługi zewnętrzne: TMDB (token, automatycznie), OpenSubtitles (klucz, logowanie), atrybucje
├─ Dane i pamięć: profil wydajności, cache grafik (limit, wyczyść), backup/przywracanie,
│                 Importuj z BK Player
└─ O aplikacji: wersja, aktualizacje, licencje, status list,
                diagnostyka (biblioteka, wydajność, failover, eksport logów) — za „Trybem diagnostycznym”
```
Zasady:
- [ ] **Treść ≠ ustawienia** — z Ustawień nie otwiera się ekranów treści (EPG, nagrania, historia).
- [ ] Każda opcja w dokładnie jednym miejscu; skróty (np. z odtwarzacza) operują na tym samym `SettingKey`. Test: „każdy klucz występuje w drzewie dokładnie raz”.
- [ ] Wiersz = tytuł + opis (1 linia) + wartość po prawej („Włączone”, „SMART”, „25 s”); `Switch` dla bool; nigdy `true/false` w tekście.
- [ ] Opcje diagnostyczne ukryte za „Trybem diagnostycznym” (7× OK na numerze wersji).
- [ ] ZALECANE: wyszukiwarka ustawień na górze listy.

### 6.5 Menu kontekstowe (nowe)
- **Odtwarzacz (MENU / Niebieski):** Źródło (kandydaci z kaskady + stan), Audio, Napisy, Bufor (profil dla tego kanału), Proporcje/zoom, Wyłącznik czasowy, Info o strumieniu, Nagraj.
- **Kafel/kanał (długie OK / MENU):** Ulubione, Ukryj, Przenieś (kanały), Szczegóły, Nagraj, Przypomnij, Usuń z Kontynuuj, Oznacz jako obejrzane.

### 6.6 Wyświetlanie w Compose (TV)
- Szuflada: `NavigationDrawer` / `ModalNavigationDrawer` + `NavigationDrawerItem`; focus wejściowy na aktywnej pozycji (`focusRestorer`).
- Ustawienia: `Row { LazyColumn(kategorie); LazyColumn(opcje) }`; PRAWO wchodzi w opcje, LEWO wraca; 3. poziom otwiera się w prawym panelu (nie nowy pełny ekran) z breadcrumbem „Odtwarzanie › Buforowanie”.
- Komponenty: `ListItem` (headline / supporting / trailing = wartość lub `Switch`), `RadioButton` w panelu wyboru, `AlertDialog` z max 3 przyciskami (domyślny focus na bezpiecznej akcji).
- Formularze (logowanie dostawcy): IME „Dalej”, DÓŁ = następne pole, LEWO w polu nie zamyka formularza (zachowane z „formularze pod pilot”). ZALECANE: „Wpisz z telefonu” — QR do lokalnej strony WWW na boxie do wklejenia długich URL.
- Model: `SettingItem` sealed (`Toggle`, `Choice`, `Navigate`, `Action`, `Info`), deklaratywne drzewo w jednym `SettingsTree.kt` — łatwe testy i jedno źródło prawdy.

---

## 7. Nowe funkcje (wymagania implementacyjne)

### 7.1 Kaskadowy failover list rezerwowych
**Cel:** gdy źródło od jednego operatora pada, odtwarzacz automatycznie przełącza się na ten sam kanał / film / odcinek z **listy 2**, potem **3, 4…** — tyle, ile list dodał użytkownik — płynnie, bez wyrzucania z odtwarzacza.

#### 7.1.1 Model
```kotlin
data class ProviderPriority(val providerId: ProviderId, val order: Int, val enabledFor: Set<ContentKind>) // LIVE, VOD, EPISODE
data class SourceCandidate(
    val providerId: ProviderId, val playable: Playable,
    val matchQuality: MatchQuality,     // MANUAL, EPG_ID, NUMBER_AND_NAME, NAME
    val order: Int)
data class FailoverChain(val primary: SourceCandidate, val backups: List<SourceCandidate>)
```
- Tabele: `provider_priority` (kolejność globalna), opcjonalnie `group_priority_override` (np. „Sport: najpierw Dostawca C”), `channel_pin_source` (ręcznie przypięte źródło kanału).
- `SourceHealthEntity(providerId, contentKey, lastOkAt, lastFailAt, failCount, circuitOpenUntil)`.

#### 7.1.2 Dopasowanie (port + rozszerzenie `BkTvFailoverMatch` / `BkVodFailoverMatch`)
LIVE — od najsilniejszego:
1. **Ręczne powiązanie** (`MANUAL`).
2. **Zgodny zaufany EPG id** (`trustedEpgKey`) po obu stronach.
3. **Numer + znormalizowana nazwa** (`numberAnchor` + `channelKey`).
4. **Znormalizowana nazwa** (`channelKey`: fold polskich znaków, usunięcie dekoratorów `PL|`, `HD/FHD/4K/HEVC`, `[backup]` itp.) **bez sprzeczności stacji** (`contradictsNamedStation`: „TVN” ≠ „TVN 7”, „Polsat” ≠ „Polsat Sport”).
- VOD: znormalizowana nazwa + **rok** (oba znane i różne → brak dopasowania; sequele osobno), maks. 1 źródło na dostawcę, z pominięciem ukrytych/zablokowanych pozycji i wyłączonych dostawców; kandydaci z FTS (zamiast starego `LIKE` z limitem 900) + dokładna walidacja.
- Odcinki: nazwa serialu po obu stronach + zgodne S/E; nigdy ogólne „Odcinek 1”.
- **Prekomputacja:** `FailoverIndexWorker` po każdym odświeżeniu katalogu buduje `channel_equivalence(groupKey, providerId, itemId, quality)`. Łańcuch przy `play()` gotowy w < 50 ms (zapytanie do Room, bez skanowania katalogu).
- Ekran „Powiązania kanałów”: kanały z liczbą źródeł, ręczne łączenie/rozłączanie.

#### 7.1.3 Algorytm (`FailoverCoordinator`, moduł `:player:failover`)
```
play(request):
  chain = buildChain(content, priorities, health)   // primary = wybór użytkownika;
                                                    // backups = pozostali dostawcy wg 'order',
                                                    // z pominięciem otwartych circuitów (chyba że wszystkie otwarte)
  start(chain[0]); preResolve(chain[1])             // „ciepła rezerwa”: resolveStream z wyprzedzeniem

onIncident(kind, reason):                           // z watchdogów
  if !BkFailoverPolicy.enabled(...) -> zwykły recovery (StreamRecoveryPolicy)
  if count < incidentThreshold(sensitivity, reason) -> ponów to samo źródło (tv/vodRecoveryRetries)
  else:
    health.markFail(current); maybeOpenCircuit(provider) // ≥3 różne treści padły u dostawcy w 2 min -> pomiń go 5 min
    next = chain.nextAfter(current) ?: return exhausted()
    release(current)                                // limit połączeń u dostawcy!
    switchSource(next, keepPosition = kind != LIVE)
    toast("Przełączono na Lista {n} · {nazwa}")
    preResolve(chain.nextAfter(next))

exhausted():
  panel błędu W ODTWARZACZU (bez wychodzenia): „Żadne z N źródeł nie odpowiada”
  [Spróbuj ponownie od Listy 1] [Wybierz źródło] [Wróć]
  automatyczny nowy cykl po cooldownMs(sensitivity), maks. 2 cykle
```
- **Watchdogi** (coroutines z timeoutami; porty z `Main`): `buffer-watchdog` (LIVE bez postępu bufora ≥ `tvSwitchDelaySeconds`), `vod-startup` (25 s bez pierwszego READY), `vod-progress` (20 s BUFFERING bez wzrostu bufora i pozycji), `resolve-timeout` (20 s), `premature-end` (koniec ≥ 60 s przed znanym czasem), `frozen-frame` (READY, ale brak nowych klatek — `VideoFrameMetadataListener` / liczniki dekodera), HTTP 4xx/5xx/timeout = natychmiastowy incydent „twardy”.
- Smart buffer uczy się **tylko** z rebufferów ≥ 3 s, nie z twardych błędów dostawcy (jak w 1.8.0.24).
- **Płynność przełączenia:**
  - Ten sam `PlayerSurface`, ekran i overlay; ostatnia klatka zostaje na ekranie (`keepContentOnPlayerReset`), mały spinner + chip „Lista 2…”.
  - Inny profil bufora → nowa instancja ExoPlayera na tym samym surface (semantyka LoadControl); ten sam profil → `setMediaItem` + `prepare` na istniejącej instancji.
  - VOD/odcinek: `seekTo(lastPosition − 5 s)`; jeśli źródło nie wspiera seek → „Ta wersja startuje od początku — przewinąć do 34:12?”.
- **Powrót do Listy 1:** domyślnie *sticky* do końca sesji danego kanału (zero ping-ponga). Przy następnym włączeniu kanału najpierw primary, chyba że circuit otwarty. Opcja „Wracaj automatycznie do głównej listy” (sonda co 2 min) — domyślnie WYŁ., bo zajmuje slot połączenia u dostawców z limitem 1.
- **Konfiguracja** (Ustawienia → Listy i dostawcy → Kolejność i listy rezerwowe): lista dostawców z trybem „Przenieś” (GÓRA/DÓŁ + OK zapis + BACK anuluj — spójnie z przenoszeniem kanałów), per lista „Używaj jako rezerwy dla: TV / Filmy / Seriale”, liczba list nieograniczona (UI testowane do 10).
- **Diagnostyka** „Źródła tego materiału” (z odtwarzacza): kandydaci, jakość dopasowania, zdrowie, ostatni błąd, „Przełącz teraz”. Log zdarzeń failover (bez sekretów) do eksportu.
- **Testy (MUSI):** `FailoverCoordinator` z fałszywym `PlayerController` i wirtualnym czasem (`runTest`): kaskada 1→2→3→4, pominięcie otwartego circuitu, wyczerpanie + nowy cykl, brak ping-ponga, zachowanie pozycji VOD, „twardy błąd nie uczy bufora”; testy dopasowania na ≥ 200 parach nazw z realnych (zanonimizowanych) list z oczekiwanym wynikiem; test integracyjny z lokalnym serwerem HLS/TS (MockWebServer) symulującym zerwanie, 5xx, DROP i zamrożony strumień.

### 7.2 Powiadomienie o nowych odcinkach (zielone światełko + puls okładki)

#### 7.2.1 Które seriale śledzimy
Per profil serial jest **śledzony**, jeśli: jest w „Mojej liście”, LUB obejrzano ≥ 1 odcinek, LUB użytkownik włączył „Powiadamiaj o nowych odcinkach” w szczegółach. Nie śledzimy całego katalogu (tysiące seriali = koszt i szum).

#### 7.2.2 Wykrywanie
- `NewEpisodesWorker` (WorkManager, periodycznie co 6 h + przy starcie, jeśli ostatni przebieg > 3 h; constraint: sieć; backoff wykładniczy):
  - Xtream: `get_series_info` (odcinki: id, `season`, `episode_num`, `added`); `last_modified` serialu do szybkiego pominięcia niezmienionych.
  - Stalker: lista odcinków przez API portalu (paginacja).
  - M3U: porównanie zbioru wpisów rozpoznanych jako odcinki (`S\d+E\d+`) po odświeżeniu listy.
  - Agregacja **między dostawcami** (ten sam serial wg reguł z §7.1) — nowy odcinek u dowolnego aktywnego dostawcy się liczy.
- Stan (Room):
```kotlin
@Entity(primaryKeys = ["profileId", "seriesKey"])
data class SeriesWatchStateEntity(
    val profileId: String,
    val seriesKey: String,            // znormalizowany klucz serialu (cross-provider)
    val latestKnown: String,          // najwyższe SxxEyy
    val lastSeenEpisode: String?,     // ostatni obejrzany (≥ 90% lub „oznacz jako obejrzane”)
    val firstDetectedAt: Long?, val acknowledgedAt: Long?, val tracking: Boolean)
@Entity(primaryKeys = ["profileId", "seriesKey", "episodeKey"])
data class KnownEpisodeEntity(val profileId: String, val seriesKey: String, val episodeKey: String, // "S02E05"
    val firstSeenAt: Long, val seenInRuns: Int, val isNew: Boolean)
```
- Reguła: odcinek jest **nowy**, jeśli nie było go przy poprzednim przebiegu **i** jest późniejszy niż `lastSeenEpisode` (albo serial obejrzany do ostatniego znanego odcinka). **Pierwszy przebieg** dla nowo śledzonego serialu tylko zapisuje stan bazowy (nie oznacza wszystkiego jako nowe).
- Fałszywe alarmy: klucz po S/E, nie po ID dostawcy (zmiana ID ≠ nowy odcinek); odcinki znikające i wracające (niestabilne API) — wymagana obecność w 2 kolejnych przebiegach LUB `added` z ostatnich 14 dni.

#### 7.2.3 Prezentacja w Compose
- **Zielone światełko:** okrągły znacznik 14 dp w prawym górnym narożniku plakatu (offset 8 dp), kolor `NewEpisodeGreen` (≈ `#3DBE4B` z logo), ciemny obrys 2 dp dla kontrastu na jasnych plakatach, miękka poświata (`drawBehind` z gradientem radialnym). Przy > 1 nowych — pigułka z liczbą („3”).
- **Puls/miganie okładki (subtelne):** `rememberInfiniteTransition` animuje **alfę poświaty znacznika 0.5 → 1.0** oraz **zieloną ramkę plakatu (alpha 0 → 0.6)**, okres 1 600 ms, `FastOutSlowInEasing`, `RepeatMode.Reverse`. **Nie skalujemy całej okładki** (kosztowne, męczące). Częstotliwość < 3 błyski/s (bezpieczne fotosensytywnie).
- Wydajność: wartość animacji czytana w fazie draw (`drawWithContent` / `graphicsLayer`), bez rekompozycji co klatkę; animują się tylko elementy widoczne; puls gaśnie po 10 cyklach od pojawienia się na ekranie (potem statyczna kropka); profil wydajności „słaby” lub „Ogranicz animacje” → tylko statyczna kropka.
- Miejsca: siatka Seriale, Home (rząd **„Nowe odcinki”** wysoko, gdy niepusty), Moja lista, szczegóły serialu (etykieta „NOWY” na kaflu odcinka + kropka na chipie sezonu), kropka przy „Seriale” w szufladzie.
- Komponenty w `core:designsystem`: `PosterCard(…, newEpisodes: Int, pulse: Boolean)`, `NewEpisodeBadge`; `@Preview` stanów 0 / 1 / 5 + focus.
- ZALECANE: kanał **Watch Next** na Google TV (`androidx.tvprovider`, typ „new”) i opcjonalne powiadomienie systemowe (domyślnie WYŁ.).

#### 7.2.4 Reset
- Odcinek przestaje być nowy: obejrzany ≥ 90%, LUB „Oznacz jako obejrzane” (długie OK), LUB „Oznacz wszystkie jako obejrzane” na serialu.
- **Puls** gaśnie po wejściu w szczegóły serialu (`acknowledgedAt = now`); **kropka** zostaje, dopóki są nieobejrzane nowe odcinki.
- Ustawienia → Filmy i seriale → „Powiadomienia o nowych odcinkach”: WŁ. / Tylko kropka / WYŁ.; dodatkowo przełącznik per serial.
- **Testy:** reguła nowości (pierwszy przebieg, zmiana ID, niestabilne API, wielu dostawców), worker z fake providerem, screenshot badge, test że puls nie powoduje rekompozycji (licznik rekompozycji w teście).

---

## 8. Bezpieczeństwo

### 8.1 Ruch sieciowy (MUSI)
`res/xml/network_security_config.xml`:
```xml
<network-security-config>
  <!-- Strumienie IPTV: dowolne hosty dodane przez użytkownika, często HTTP — cleartext dozwolony tylko w base-config -->
  <base-config cleartextTrafficPermitted="true">
    <trust-anchors><certificates src="system"/></trust-anchors>
  </base-config>
  <!-- Usługi metadanych i EPG: wyłącznie HTTPS -->
  <domain-config cleartextTrafficPermitted="false">
    <domain includeSubdomains="true">themoviedb.org</domain>
    <domain includeSubdomains="true">tmdb.org</domain>
    <domain includeSubdomains="true">opensubtitles.com</domain>
    <domain includeSubdomains="true">opensubtitles.org</domain>
    <domain includeSubdomains="true">epg.ovh</domain>
    <domain includeSubdomains="true">teleman.pl</domain>
    <domain includeSubdomains="true">youtube.com</domain>
  </domain-config>
</network-security-config>
```
- Usunąć globalne `android:usesCleartextTraffic="true"`; podpiąć `android:networkSecurityConfig`.
- `MetadataHttpClient`: `connectionSpecs = listOf(ConnectionSpec.MODERN_TLS)` (bez `CLEARTEXT`), bazowe URL na sztywno `https://`. `StreamHttpClient` (TLS + CLEARTEXT) wyłącznie dla strumieni i API dostawców IPTV.
- Zakaz `TrustManager`/`HostnameVerifier` akceptujących wszystko (własna reguła Detekt/Lint). Ewentualne zaufanie certyfikatowi konkretnego dostawcy — tylko pinning po jawnej zgodzie użytkownika.
- Przy dodawaniu dostawcy po HTTP ostrzeżenie: „Ten dostawca używa HTTP — login i hasło są przesyłane jawnie”.

### 8.2 Sekrety (MUSI)
- `SecretStore`: Tink AEAD (AES-256-GCM), keyset szyfrowany kluczem z **Android Keystore**. Przechowuje: hasła Xtream, MAC i tokeny Stalkera, tokeny TMDB/OpenSubtitles, hasła SMB, klucze prywatne WireGuard.
- Room i DataStore trzymają tylko `secretRef` / nie-sekretne dane.
- PIN: PBKDF2-HMAC-SHA256 z solą, porównanie w stałym czasie, limit 5 prób + rosnąca blokada.
- Utrata klucza Keystore (`KeyPermanentlyInvalidatedException`, `AEADBadTagException` — zdarza się na tanich boxach po resecie) → komunikat „Zaloguj się ponownie do dostawców”, nigdy crash.
- `android:allowBackup="false"` + `dataExtractionRules` wykluczające dane aplikacji.

### 8.3 Repo i build (MUSI)
- **Zero kluczy w repo.** Nie kopiować `signing/bk-next-test.keystore`. `.gitignore`: `*.jks`, `*.keystore`, `keystore.properties`, `local.properties`.
- Podpis release z env/CI secrets (`KETCHUP_KEYSTORE_PATH`, `KETCHUP_KEYSTORE_PASSWORD`, `KETCHUP_KEY_ALIAS`, `KETCHUP_KEY_PASSWORD`) lub `~/.gradle/gradle.properties`. Brak → czytelny błąd builda release (żadnego cichego fallbacku na klucz debug).
- `release`: `isMinifyEnabled = true`, `isShrinkResources = true`, R8 full mode; reguły keep dla jcifs-ng/BouncyCastle, WireGuard (JNI), Cast `OptionsProvider`, kotlinx.serialization. **Smoke-testy na buildzie release** (podpisanym testowym kluczem z CI secrets).
- `debug`: `applicationIdSuffix = ".debug"` (instaluje się obok release).
- CI: `gitleaks`, Renovate/Dependabot, Lint z `checkReleaseBuilds`.

### 8.4 Logi i diagnostyka (MUSI)
- `RedactingTree` + `RedactingInterceptor` maskują: `password=`, `username=`, `token=`, `mac=`, ścieżki Xtream `/live|movie|series/<user>/<pass>/`, nagłówki `Authorization` / `Cookie`, adresy MAC (`([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}`). Test jednostkowy z listą wzorców.
- Release: brak logowania URL-i strumieni na INFO/DEBUG; eksport logu tylko na żądanie użytkownika, po redakcji.

### 8.5 Uprawnienia
- `SCHEDULE_EXACT_ALARM` tylko dla harmonogramu nagrań, z ekranem wyjaśnienia; fallback na inexact + WorkManager.
- `POST_NOTIFICATIONS` pytane w kontekście (pierwsze nagranie / włączenie powiadomień), nie na starcie.
- Typy foreground service zgodne z targetSdk (nagrywanie; VPN wg wymagań `VpnService` i biblioteki WireGuard).

---

## 9. Migracja danych i ustawień użytkownika ze starej wersji

### 9.1 Kluczowe ograniczenie
Nowa aplikacja może czytać starą bazę `databases/bk-next.db` **tylko** przy tym samym `applicationId` **i** tym samym kluczu podpisu (sandbox). Stara apka jest podpisana testowym kluczem debug z repo — tego klucza **nie wolno** używać dla nowego release. Dlatego:
- **Ścieżka główna (MUSI): migracja przez plik kopii zapasowej.** W starej aplikacji: „Pełny backup” lub „Backup ustawień i list” → w nowej: Onboarding lub Dane i pamięć → **„Importuj z BK Player”** → wybór pliku (SAF, USB, SMB/NAS, URL) + hasło.
- **Ścieżka opcjonalna:** jeśli właściciel 🔒 zdecyduje inaczej (ten sam `applicationId` i klucz — niezalecane), `LegacyDbImporter` przy pierwszym starcie czyta `bk-next.db` bezpośrednio. Kod importera jest wspólny (backup to i tak snapshot SQLite).
- Rekomendacja: nowy `applicationId` → obie aplikacje działają równolegle w okresie przejściowym.

### 9.2 `LegacyImporter` (`core:database` + `core:security`)
- [ ] `LegacyBackupReader`: port deszyfrowania z `Backup.java` (magic `BKP1–5`/`BKL1–5`, sól, IV, `PBKDF2WithHmacSHA256` 120 000 iteracji / 256 bit, `AES/GCM/NoPadding`, tag 128) → tymczasowy SQLite → `PRAGMA quick_check` → odczyt; obsługa manifestów v5 i v7.
- [ ] Mapowanie:

| Stare | Nowe |
|---|---|
| `config` `provider:*` (JSON) | `ProviderEntity` + **sekrety → `SecretStore`** (nigdy do Room) |
| `config` `profile:*` | `ProfileEntity` |
| `config` `epg:*` | `EpgSourceEntity` |
| `config` `settings` (JSON) | DataStore (Załącznik B); PIN hash + `pin_salt` 1:1 (ten sam algorytm) |
| `config` `task:*` | `ScheduleEntity` (pominąć przeterminowane) |
| `items` (`tv`/`vod`/`series`/`episode`) | **katalogu nie importujemy** — odświeżany od dostawcy; tylko mapa starych ID → nowe klucze dla flag i wznowień |
| `flags` (fav/hidden/locked/rank/position) | `FlagEntity` (mapowanie po `providerId + streamId`, fallback po znormalizowanej nazwie) |
| `membership` (grp, id) | kolejność/grupy użytkownika, `GroupVisibilityEntity` (`visibility:tv:provider:group`) |
| `aliases` | `ChannelAliasEntity` |
| `guide` | pominąć (EPG pobierane od nowa) |
| historia / punkty wznowienia | `HistoryEntity`, `ResumeEntity` per profil |

- [ ] Raport po imporcie (ekran, wzorem `RestoreReport`): liczba dostawców, profili, ulubionych, wznowień + czego nie dopasowano i dlaczego.
- [ ] Idempotentny, transakcyjny (błąd = rollback), plik źródłowy nienaruszony.
- [ ] Testy na **prawdziwych, zanonimizowanych** zrzutach: mała baza (1 M3U), duża (3 dostawców, 50k pozycji), z profilami i PIN, backupy v5 i v7.
- [ ] Flagi migracyjne starej apki (np. `bk_smart_buffer_18024_migrated`) interpretować (→ profil SMART aktywny), nie kopiować bezmyślnie.

### 9.3 Migracje nowej aplikacji
- Room: `exportSchema`, `AutoMigration` / ręczne `Migration`, test `MigrationTestHelper` dla każdej wersji od 1.0.
- DataStore: wersjonowany schemat + `DataMigration`.

---

## 10. Przenoszenie logiki ze starej aplikacji

### 10.1 Procedura portu `*Policy` (MUSI)
> **Zmiana 2026-10-31 (ADR `docs/adr/legacy-policy-port.md`):** moduł `:legacy:policy-java` i katalog `legacy/` w repo **nie powstają** (§14 ma pierwszeństwo); golden master generowany poza repo, do repo trafiają tylko fixture'y i testy. Pkt 2 poniżej czytać z tym zastrzeżeniem.
1. **Inwentaryzacja** w `docs/PORTING.md`: wszystkie ~70 klas `*Policy` / `*Match` / `*Plan` / `*Framing` / `*Palette` z kategorią **(L)** logika domenowa → port 1:1, **(U)** heurystyka starego UI → zastąpić/odrzucić, **(M)** mieszane → wydzielić logikę.
2. **Port (L) pod osłoną testów:** utwórz tymczasowy moduł `:legacy:policy-java` z kopią klas Java. Z 238 testów wybierz te, które wywołują polityki (nie czytają źródeł), przetłumacz na Kotlin, uruchom na kopii Java → zielone → przetłumacz klasę do `:core:policy` → te same testy zielone na Kotlinie → usuń kopię Java.
3. Polityki bez testów zachowania: najpierw **testy charakteryzujące** (golden master: tablica wejść/wyjść z wartościami granicznymi) na kopii Java.
4. Stałe liczbowe zachować **dokładnie** (np. `BkFailoverPolicy`: `minimumStallMs` fast/standard/cautious = 1500/2500/4500 ms; `cooldownMs` = 12/20/30 s; `incidentThreshold` rebuffer 1/2/3, inne 2/3/4; `tvRecoveryRetries` 0/1/2; `vodRecoveryRetries` 1/2/3; `tvSwitchDelaySeconds` ∈ {5, 10, 15, 25}, domyślnie 25). Nazwy mogą być czytelniejsze, ale KDoc wskazuje źródło: „port z `BkFailoverPolicy.minimumStallMs` (1.8.0.34)”.
5. **Testy „tekstowe”** (~146, czytające `Main.java`): **nie portować**. Dla każdego ustal, jakie zachowanie chronił, i zapisz je jako wymaganie w `docs/KNOWN-EDGE-CASES.md` + test zachowania/UI w nowym kodzie.

### 10.2 Wstępna klasyfikacja (zweryfikuj)
- **(L) port 1:1:** `BkFailoverPolicy`, `BkTvFailoverMatch`, `BkVodFailoverMatch`, `BkSmartBufferPolicy`, `StreamRecoveryPolicy`, `TvBufferSessionPolicy`, `PlaybackStabilityPolicy`, `NetworkRoutePolicy`, `StreamUserAgent`, `CinemaResumePolicy`, `SeriesEpisodePolicy`, `SeriesEpisodePicker` (logika), `CatalogOrder`, `CategoryMetadata`, `RadarGuidePolicy`, `RadarTodayFilmPolicy`, `RadarTelemanFetchPolicy`, `RadarTelemanMetadataPolicy`, `RadarMetadataQueuePolicy`, `RadarWorkerBudgetPolicy`, `TvPremiumContentPolicy` (klasyfikacja audycji), `TvCarouselPolicy`, `BkLiveHomePolicy`, `MemoryStabilityPolicy`, `HomeSmartMemoryPolicy`, `DiskCacheSafety`, `ArtLoadPolicy` (priorytety), `BkRemoteScrollPolicy`, parsery z `Importers`, parser `Teleman`, `Captions` / `CaptionFile`.
- **(U) przeprojektować** (zastąpić Compose i tokenami): `HomeFrameBudgetPolicy`, `BkUiFrameCostPolicy`, `HomeFrameMonitor`, `BkSharpTextPolicy`, `BkUiMotionPolicy`, wszystkie `*Drawable`, `*ScrollView`, `HomeCarouselWindowPolicy`, `TvPremiumShelfAlignment`, `TvPremiumPolishPolicy`, `TvGuidePremiumPolicy` (wygląd), `HomeHeroFraming`, `CinemaBackdropFraming`, `CatalogHeroTextFit`, `MenuLayoutPolicy`, `Theme`. Przeczytaj je jednak — wartości kadrowania i palet są dobrym punktem startu makiet.
- **(M) mieszane:** `HomeCarouselPolicy`, `HomeFastStartPolicy` (kolejność ładowania Home — zachować), `TvArtworkPreloadPlan` (do 12 grafik, 2 równolegle — zachować jako konfigurację), `CinemaFocusNav` (reguły nawigacji → wymagania §5), `LifecycleStabilityPolicy` (zasady tak, mechanika nie).

### 10.3 Strefy FROZEN — co zachować 1:1
Decyzja właściciela: **wygląd** stref FROZEN (TV od 1.7.16.42, VOD/Seriale od 1.7.16.43, Radar/EPG od 1.8.6.1) zostaje odmrożony na potrzeby nowego UI. **Zachowanie** jest referencyjne i MUSI zostać zachowane:
- **TV:** klasyfikacja rzędów „Teraz warto / Za chwilę / Sport i inne”; postęp i pozostały czas; mapowanie EPG kanał ↔ audycja (aliasy, `tvg-id`); prefetch EPG przy przewijaniu.
- **Radar/EPG:** TOP DNIA / Teraz / Za chwilę / Wieczorem; dziś = Teleman, kolejne dni = OVH; godziny, kolejność, klasyfikacja, filtry; metadane (opisy, IMDb/Filmweb, gatunki) z trwałym cache; limity RAM (800 kanałów, etapy 0–6 / 6–12 / 28 h, brak indeksu 28 h w RAM przy wejściu); single-flight; odpowiedź „bez zmian” nie przebudowuje; błąd nie zostawia nieskończonego ładowania. **Zaakceptowane ograniczenie metadanych Teleman przy granicy dnia — NIE „naprawiać” bez polecenia.**
- **VOD/Seriale:** sortowanie (fallback dat), reguły wznowienia, dobór odcinka do kontynuacji, metadane dostawcy z fallbackiem TMDB.
- **Egzekwowanie:** testy golden-master w `:core:policy` / `:epg:radar` na zamrożonych fixture'ach (snapshot `pltv.xml.gz` + HTML Teleman z konkretnego dnia → oczekiwany JSON wyniku, wygenerowany przez stary kod w `:legacy`). Zmiana wyniku = czerwony test = wymagana zgoda właściciela (etykieta PR `frozen-approved`, CODEOWNERS na te katalogi).

### 10.4 Wiedza z dokumentacji
Przejrzyj `WERYFIKACJA-*.md`, `BK-UPDATE-*.md`, `BK-RELEASE-*.md`, `FEATURES.md`, `TEST-CHECKLIST.md`, `CHANGELOG.md`, `*-FROZEN-*.md` i wypisz do `docs/KNOWN-EDGE-CASES.md` każdy quirk (dostawca „365”, Fire OS 2 GB, firewall DROP przy `resolve`, nazwy typu `|PL| Oppenheimer 2023 FHD`, Cast z nagłówkami/VPN, przechwytywanie D-pad w dialogu URL itd.). Każdy wpis: ID + opis + test, który go pokrywa (lub „manualny”).

---

## 11. Rekomendacje eksperta — o czym użytkownik mógł nie pomyśleć

Wymagania wynikające z praktyki profesjonalnych odtwarzaczy IPTV (TiviMate, Sparkle, IPTV Smarters, Kodi PVR) i wytycznych Android TV. Oznaczone MUSI / ZALECANE.

### 11.1 Profile i kontrola rodzicielska (MUSI)
- Profile z avatarem (wybór przy starcie, jeśli > 1 i włączone „pytaj”); osobne per profil: historia, Kontynuuj, ulubione, Moja lista, nowe odcinki, układ Home.
- Profil **dziecięcy**: whitelista kategorii/kanałów, limit wieku z TMDB (certyfikacja PL), brak wejścia do Ustawień bez PIN.
- Blokady PIN: kategorie, kanały, pozycje; **automatyczne wykrywanie grup „XXX / Adult / 18+”** po nazwie (domyślnie zablokowane i ukryte); opcjonalnie blokada Ustawień i dostawców. Odblokowanie czasowe 10 min (jak dotąd).
- Odzyskanie PIN: hasłem backupu albo reset usuwający blokady (z ostrzeżeniem).

### 11.2 Wydajność list 10k–100k pozycji (MUSI)
- Import strumieniowy (parser na `Flow`, bez całej listy w RAM), batch insert w transakcjach, postęp w UI.
- UI przez **Paging 3** (`PagingSource` z Room) dla VOD/seriali i dużych grup kanałów; `Lazy*` z `key = stableId` i `contentType`.
- Stabilne klasy stanu (`@Immutable`, `kotlinx.collections.immutable`), Compose compiler metrics w CI.
- **Baseline Profiles + Startup Profiles** — kluczowe dla Compose na słabych boxach.
- Wyszukiwanie FTS z normalizacją polskich znaków (kolumna `normName`).
- Budżety: Home ≤ 150 MB PSS na urządzeniu 2 GB; test w CI na syntetycznej liście 50k pozycji.

### 11.3 Cache okładek (MUSI)
- Coil 3: dysk 64–512 MB (z ustawień), RAM wg profilu wydajności (`MemoryStabilityPolicy`), downsampling do rozmiaru kafla, crossfade tylko na mocnych urządzeniach.
- Loga kanałów: długi TTL (30 dni); brak logo → monogram z nazwy na kolorowym tle (nigdy pusty kafel).
- Prefetch sąsiednich rzędów z niskim priorytetem, anulowanie przy szybkim przewijaniu; `onTrimMemory` czyści RAM cache.

### 11.4 Błędy sieci i offline (MUSI)
- `ConnectivityManager.NetworkCallback` → globalny stan sieci (pasek „Brak połączenia”), automatyczne wznowienie odtwarzania po powrocie sieci.
- Katalog, EPG i Radar działają z cache offline z oznaczeniem wieku danych („EPG z 21:00”).
- Retry z backoffem wykładniczym + jitter. Rozróżnienie: 401/403 (złe dane / konto wygasło → „Edytuj dostawcę”), 404/5xx, timeout, DNS, SSL — przyjazny komunikat po polsku + kod techniczny w „Szczegółach”.
- Banner o **wygasającym koncie** Xtream (`exp_date`) 7 dni wcześniej.
- Timeouty: connect 10 s, read 20 s (EPG gz: 60 s), handshake Stalkera 15 s.

### 11.5 Wznawianie, ulubione, ostatnio oglądane (MUSI)
- Zapis pozycji co 10 s oraz przy pauzie / wyjściu / `onStop`; „obejrzane” przy ≥ 90% lub < 3 min do końca; wznowienie od −5 s.
- Ostatnie 20 kanałów, „poprzedni kanał” jednym klawiszem; ulubione kanały z własną kolejnością; Moja lista filmów i seriali.
- ZALECANE: **Watch Next** Google TV (systemowy rząd „Kontynuuj oglądanie”) — `:integration:tvprovider`.

### 11.6 Wyszukiwanie globalne (MUSI)
- Jedno pole: kanały, audycje EPG (teraz i przyszłe), filmy, seriale, odcinki; wyniki w rzędach per typ; debounce 300 ms, od 2 znaków, odporne na polskie znaki; ZALECANE tolerowanie literówek (trigramy).
- Głos (`RecognizerIntent`, jeśli dostępny), klawiatura ekranowa przyjazna pilotowi, historia wyszukiwań.
- ZALECANE po MVP: integracja z globalnym wyszukiwaniem Google TV.

### 11.7 Picture-in-Picture i podgląd (MUSI)
- PiP z odtwarzacza (przycisk; automatyczny przy HOME, gdzie wspierane), akcje PiP: play/pause, CH±. Sprawdzaj `FEATURE_PICTURE_IN_PICTURE` (wiele TV go nie ma) i ukrywaj opcję.
- Podgląd na żywo w Na żywo / Przewodniku — zawsze **jeden** aktywny player (limit połączeń dostawcy).
- ZALECANE później: multi-view 2–4 kanały, tylko gdy dostawca pozwala na wiele połączeń.

### 11.8 Dostępność / a11y (MUSI)
- `contentDescription` dla ikon i kafli ze stanem („Serial X, 2 nowe odcinki”), role semantyczne; TalkBack działa z D-pad.
- Systemowa skala czcionki do 1.3× bez łamania layoutu + opcja „Duży tekst”.
- Kontrast ≥ 4.5:1 (test automatyczny tokenów), focus nie tylko kolorem, „Ogranicz animacje” (również respektowanie `ANIMATOR_DURATION_SCALE = 0`).
- Napisy: rozmiar/tło/kolor (systemowy `CaptioningManager` + nadpisanie w aplikacji).

### 11.9 i18n (MUSI)
- Wszystkie teksty w `strings.xml` (zero literałów w composable — reguła Lint/Detekt); **PL domyślnie**, EN drugi.
- `plurals` z polską odmianą (1 odcinek / 2 odcinki / 5 odcinków).
- Daty i godziny wg `Locale` i strefy; EPG w UTC → wyświetlanie lokalne; poprawna siatka przy zmianie czasu letni/zimowy (testy na dni zmiany czasu).
- `start/end` zamiast `left/right` (nie blokować RTL).

### 11.10 Telemetria i diagnostyka (MUSI, prywatność przede wszystkim)
- Crash reporting **opt-in** (ACRA self-hosted lub Crashlytics), bez URL-i strumieni i danych dostawców.
- Ekran „Diagnostyka”: wersja, urządzenie, RAM, dekodery (`MediaCodecList`: HEVC / AV1 / AC3 / E-AC3 / DTS), bieżący strumień (kodek, rozdzielczość, bitrate, bufor, zgubione klatki), log failover, stan EPG/Radaru, eksport logu (zredagowanego) do pliku / SMB / QR.
- Lokalne metryki (czas startu, zapu, liczba rebufferów) — widoczne w diagnostyce i używane w testach regresji.
- Debug: StrictMode (crash przy IO na main), LeakCanary.

### 11.11 Aktualizacje (MUSI zdecydować 🔒; ZALECANE wariant 2)
1. Sklep (Google Play In-App Updates / Amazon Appstore) — uwaga na polityki sklepów wobec odtwarzaczy IPTV.
2. **Własny OTA:** `update.json` po HTTPS (wersja, URL APK, SHA-256, changelog, minSdk), podpisany Ed25519 → `DownloadManager` → weryfikacja sumy i podpisu → `PackageInstaller` (wymaga `REQUEST_INSTALL_PACKAGES` i zgody użytkownika). Kanały stabilny/beta. Ten sam plik może zdalnie przełączać feature flagi (np. Teleman).
3. Ekran „Co nowego” raz po aktualizacji.

### 11.12 Zgodność z TV Boxami i pilotami (MUSI)
- Matryca: Android TV 9/10/11/12/14, Google TV (Chromecast HD/4K), Fire OS 7/8 (Fire TV Stick Lite/4K/Max), Nvidia Shield, Xiaomi Mi Box/TV Stick, boxy AOSP (Amlogic S905x, Rockchip RK3318) — **często bez Google Play Services**: Cast i usługi Google opcjonalne, sprawdzane w runtime, aplikacja w pełni działa bez nich.
- ABI: `armeabi-v7a` **obowiązkowo** (tanie boxy i część Fire TV mają 32-bitowy userspace) + `arm64-v8a`; `x86_64` dla emulatora; WireGuard JNI dla wszystkich.
- Dekodery: sprzętowy / programowy / auto, globalnie i per dostawca (port); tunneling domyślnie WYŁ. z opcją; ZALECANE rozszerzenie FFmpeg (Media3) dla AC3/E-AC3/DTS na boxach bez passthrough.
- AFR: `Surface.setFrameRate` / `Display.Mode` (API 30+) z fallbackiem; wykrywanie HDR (`HdrCapabilities`).
- Manifest: oba `LAUNCHER` i `LEANBACK_LAUNCHER`, banner, `touchscreen` i `leanback` jako `required="false"` (jak w starej apce).
- Piloty: IR (rzadki repeat), BT (Fire TV), CEC z telewizora (często tylko D-pad + OK + BACK + Play/Pause) — patrz §5.3, zestaw minimalny.

### 11.13 Łatwe do przeoczenia
- **Wyłącznik czasowy** (15/30/60/90 min, „po końcu audycji”) — MUSI.
- **Catch-up z przewodnika** (OK na przeszłej audycji kanału z archiwum) — MUSI.
- **Przypomnienia o audycji** (komunikat w aplikacji + „Przełącz”) — MUSI.
- **Limit połączeń dostawcy:** ostrzeżenie przy próbie podglądu + nagrywania jednocześnie, gdy dostawca pozwala na 1 połączenie — MUSI.
- **Przesunięcie czasu EPG** per źródło (±h) dla źle oznaczonych stref — MUSI.
- **Wybór jakości HLS/DASH** (auto / maks. / oszczędnie) — MUSI.
- **Wpisywanie z telefonu** (QR → lokalna strona WWW na boxie do wklejenia URL list, loginów) — ZALECANE, ogromna oszczędność czasu przy pilocie.
- Wygaszanie UI menu po bezczynności (nie w odtwarzaczu) — ZALECANE.
- **Licencje i atrybucje:** TMDB („This product uses the TMDB API but is not endorsed or certified by TMDB”), LGPL jcifs-ng, NOTICE Apache — MUSI, w „O aplikacji”.

---

## 12. Plan pracy w fazach dla Cloud AI

Każda faza kończy się **PR-em do `main`** z raportem (Załącznik C), zielonym CI i artefaktami (APK debug + release podpisany kluczem CI, screenshoty, nagrania). Nie zaczynaj fazy zależnej, zanim DoD poprzedniej nie jest spełnione. 🔒 = wymagana akceptacja właściciela (zbierz pytania w raporcie, nie blokuj prac równoległych).

### Faza 0 — Fundament repo (≈ 1 tydz.)
- Nowe repo `ketchup-player`; stare `bk-player-next` tylko jako odniesienie (`reference/`, poza buildem; nie modyfikować).
- Gradle KTS, version catalog, `build-logic`, szkielety modułów (§3.2), Hilt, Detekt / ktlint / Lint, CI (GitHub Actions): `assembleDebug`, `assembleRelease`, `test`, `lint`, `detekt`, Roborazzi verify, gitleaks.
- `network_security_config`, `RedactingTree`, szkielet `SecretStore` z testami.
- **DoD:** pusta aplikacja „Ketchup Player” startuje na emulatorze Android TV (API 34) i telefonu; release z R8 działa; CI zielone; złamanie reguły „feature nie zależy od feature” łamie build (opisany test negatywny).

### Faza B — Branding i design system (≈ 1 tydz.) 🔒
- Skrypt generujący assety z `ketchup-player-logo.jpg` (§4.0) — **bez projektowania nowego logo**; adaptive + monochrome icon, banner 320×180 i 640×360, splash, wersja pozioma.
- `core:designsystem`: tokeny z palety logo, typografia TV, `FocusCard`, `PosterCard`, `ChannelRow`, `NewEpisodeBadge`, przyciski, `SettingsListItem`, stany loading/empty/error; galeria `@Preview` + screenshoty.
- **DoD:** screenshoty ikony i banera na launcherze Google TV i Fire TV (emulator), napis na banerze czytelny; przezroczysty wariant bez halo (screenshot na jasnym i ciemnym tle); galeria w `docs/mockups/components/`; akceptacja właściciela.

### Faza P — Port logiki do `:core:policy` (≈ 2 tyg.)
- `docs/PORTING.md`, `:legacy:policy-java`, testy charakteryzujące, port na Kotlin, usunięcie kopii; `docs/KNOWN-EDGE-CASES.md`.
- **DoD:** 100% klas (L) przeniesionych z testami; pokrycie linii `:core:policy` ≥ 90%; log z raportu pokazuje te same testy zielone na Javie i Kotlinie; brak `android.*` w module.

### Faza D — Dane i dostawcy (≈ 3 tyg.)
- Room (§3.5), DataStore, repozytoria, `IptvProvider` + M3U / Xtream / Stalker, testy kontraktowe, `StalkerSessionManager`, import TXT, WorkManager odświeżania katalogu, FTS.
- **DoD:** testy kontraktowe zielone dla 3 providerów; import syntetycznej listy 50k kanałów < 30 s i < 250 MB na emulatorze; test, że w pliku bazy nie ma hasła z fixture'a (przeszukanie bajtów).

### Faza E — EPG i Radar (≈ 2 tyg.)
- XMLTV strumieniowo (gz), single-flight, atomowa podmiana, aliasy, przesunięcie czasu; `:epg:teleman` za flagą; `:epg:radar` (port); cache trwały.
- **DoD:** golden-master Radaru = wynik starego kodu na tych samych fixture'ach; import realnego `pltv.xml.gz` bez OOM na emulatorze 2 GB; test zmiany czasu.

### Faza X — Odtwarzacz, smart buffer, kaskadowy failover (≈ 3 tyg.)
- `PlaybackService`, `PlayerController`, `LoadControlFactory`, watchdogi, frozen-frame, `FailoverCoordinator` (§7.1), indeks równoważności, diagnostyka źródeł, Cast.
- **DoD:** testy scenariuszy kaskady na wirtualnym czasie zielone; test integracyjny z lokalnym serwerem HLS/TS: zerwanie, 5xx, DROP, zamrożenie → przełączenie Lista 1 → 2 → 3 → 4 bez wyjścia z ekranu odtwarzacza (Compose UI test + nagranie `adb screenrecord` w artefaktach); zmierzony czas przełączenia w raporcie.

### Faza U — Makiety ekranów 🔒 (≈ 1–2 tyg., równolegle z D/E/X)
- Wszystkie makiety z §4.2 + nowa hierarchia menu z §6 na danych fake; screenshoty 1920×1080, 1280×720 i telefon.
- **DoD:** komplet PNG w `docs/mockups/` + opis przepływów pilota per ekran; akceptacja właściciela; poprawki naniesione przed Fazą F.

### Faza F — Ekrany funkcjonalne (≈ 4–5 tyg.)
Kolejność: Onboarding + Dostawcy → Na żywo → UI odtwarzacza → Przewodnik (Siatka / Radar) → Home → Filmy → Seriale + nowe odcinki (§7.2) → Szczegóły → Moja lista → Szukaj → Ustawienia (§6.4) → Profile/PIN → Nagrywanie → VPN → NAS.
- **DoD per ekran:** ViewModel z testami (Turbine); pełna obsługa pilotem i przywracanie focusu (Compose UI testy z §5.4); screenshot zgodny z zaakceptowaną makietą; brak literałów tekstu; kompletne `contentDescription`; stany loading/empty/error/offline.

### Faza M — Migracja ze starej aplikacji (≈ 1 tydz.)
- `LegacyBackupReader`, `LegacyDbImporter`, ekran importu i raport.
- **DoD:** import ≥ 4 realnych, zanonimizowanych backupów (v5 i v7) z pełnym raportem; dostawcy, profile, PIN, ulubione, wznowienia przeniesione; sekrety wyłącznie w `SecretStore`; ponowny import idempotentny.

### Faza Q — Jakość, wydajność, release candidate (≈ 2 tyg.)
- Baseline Profiles, Macrobenchmark (start, przewijanie Home/EPG/VOD, zap), test monkey + pilot 2 h, przegląd a11y, EN, OTA (jeśli wybrane), `TEST-CHECKLIST-NEW.md` dla testera.
- **DoD:** kryteria §1.4 spełnione na emulatorze TV + raport z ≥ 2 fizycznych urządzeń (od właściciela/testera wg checklisty); 0 ostrzeżeń Lint/Detekt; release podpisany kluczem z CI; `docs/PARITY.md` 100% „OK” lub uzasadnione „Odroczone”.

**Szacunek:** ~5–6 miesięcy pracy przy krótkich iteracjach; B i U równolegle z D / E / X.

### Weryfikacja w każdej fazie
- `./gradlew check` (unit + lint + detekt) + `verifyRoborazziDebug` + `assembleRelease`.
- Nightly: emulator Android TV — testy instrumentacyjne + Macrobenchmark.
- Artefakty PR: screenshoty i nagrania ekranów; porównanie czasu startu i zapu z poprzednią fazą.
- Raport (Załącznik C) z listą odstępstw (ADR) i pytań do właściciela.

---

## 13. Ograniczenia i zasady

### 13.1 Kod (MUSI)
- **Zakaz pustych `catch`** (Detekt: `EmptyCatchBlock`, `SwallowedException`, `TooGenericExceptionCaught` z wyjątkami tylko na granicach warstw). Każdy złapany wyjątek jest obsłużony (Result / `AppError` do UI) **lub** zalogowany `Timber.w(e, "kontekst")` przez `RedactingTree`. `CancellationException` zawsze propagowany (`runCatching` w coroutines tylko z ponownym rzuceniem anulowania).
- Zakaz `GlobalScope`, `runBlocking` w kodzie produkcyjnym, `!!`, `Thread.sleep`.
- Zero IO / DB / sieci na wątku głównym (StrictMode w debug).
- Każdy scope ma właściciela i cykl życia (`viewModelScope`, scope serwisu, `@ApplicationScope`).
- Pliki ~≤ 400 linii, composable ~≤ 80 linii — sygnał do podziału. **Żadnego nowego „god object”.**
- KDoc dla nietrywialnych decyzji, szczególnie portów polityk (skąd liczby).
- Commity konwencjonalne (`feat(player): …`), małe PR-y, generowany `CHANGELOG.md`. Raporty w `docs/reports/`, nie setki plików w katalogu głównym.

### 13.2 Logowanie i prywatność (MUSI)
- Redakcja sekretów wszędzie (§8.4): żadnych URL-i z danymi logowania, MAC, tokenów, haseł SMB/WireGuard w logach, crashach, diagnostyce ani screenshotach testowych.
- Fixture'y zanonimizowane (hosty `example.invalid`, fałszywe loginy); gitleaks w CI.

### 13.3 Teleman.pl — zgodność prawna (MUSI)
- Scraping teleman.pl niesie ryzyko prawne (regulamin serwisu, ochrona baz danych w UE — dyrektywa 96/9/WE i polska ustawa o ochronie baz danych) i techniczne (zmiany HTML).
- Moduł `:epg:teleman` **izolowany i wyłączalny feature flagą** (w Ustawieniach i zdalnie przez `update.json`). Domyślny stan 🔒 decyduje właściciel — ZALECANE: WYŁ. w dystrybucji publicznej, WŁ. w buildach prywatnych.
- Kultura pobierania: identyfikujący User-Agent, respektowanie `robots.txt`, limit (≤ 1 żądanie / 2 s), tylko dane potrzebne (dziś), agresywny cache, **brak redystrybucji** (dane tylko w lokalnym cache urządzenia), atrybucja źródła w UI.
- `docs/adr/teleman.md`: status prawny do potwierdzenia przez właściciela. **Aplikacja MUSI działać w pełni z wyłączonym Telemanem** (Radar uproszczony: tylko XMLTV, bez ocen IMDb/Filmweb).
- Fixture HTML + nightly test na żywej stronie (tylko prywatne CI) ostrzegający, gdy parser przestaje działać.
- Analogicznie TMDB i OpenSubtitles: klucze podaje użytkownik, atrybucja, limity zapytań API.

### 13.4 Treści i dystrybucja
- Aplikacja jest wyłącznie odtwarzaczem — **nie dostarcza list ani treści**; nie dołączać przykładowych list z nielegalnych źródeł; tryb demo tylko na legalnych, publicznych strumieniach testowych.
- Opisy i screenshoty sklepowe bez treści chronionych.

### 13.5 Współpraca z właścicielem
- Bramki 🔒: branding (B), makiety (U), każda zmiana zachowania z §10.3, domyślny stan Telemana, `applicationId`, kanał aktualizacji.
- Nie modyfikuj starego repo `bk-player-next`. **Nie projektuj logo od nowa** — używaj dostarczonego pliku.
- Każde odstępstwo od konspektu = ADR w `docs/adr/NNN-tytul.md` (kontekst, decyzja, konsekwencje).

---

## Załącznik A — mapa „stara klasa → nowy moduł”
| Stare (`pl.bkplayer.next`) | Nowe |
|---|---|
| `Main.java` (~8 330 l.) | rozbite na `feature:*`, `player:*`, `:app` (nawigacja) |
| `Api`, `ServiceApi`, `Net`, `ProviderDetails`, `ProviderStatus` | `provider:xtream`, `provider:stalker`, `core:network` |
| `Importers` | `provider:m3u`, `epg:xmltv`, import TXT w `feature:providers` |
| `Db`, `Model`, `Profiles` | `core:database`, `core:model`, `core:data` |
| `PlaybackEngine`, `StreamDiagnostics`, `StreamUserAgent` | `player:engine` |
| `BkFailoverPolicy`, `BkTvFailoverMatch`, `BkVodFailoverMatch`, `BkSmartBufferPolicy`, `StreamRecoveryPolicy`, `TvBufferSessionPolicy`, `PlaybackStabilityPolicy` | `core:policy` (port) + `player:failover` (koordynacja) |
| `GuideView`, `GuideJob` | `feature:guide` (Compose) + `EpgRefreshWorker` |
| `Teleman`, `Radar*`, `TvTelemanCache` | `epg:teleman`, `epg:radar`, zakładka Radar w `feature:guide` |
| `Metadata`, `OpenSubs`, `Captions`, `CaptionFile`, `LibraryExtras` | `core:data` (Metadata / Subtitles), `feature:details`, `feature:player-ui` |
| `Recorder`, `Schedule` | `feature:recording` (+ serwis, receiver) |
| `CastController`, `CastOptionsProvider` | `integration:cast` (media3-cast) |
| `VpnBridge`, `VpnProviders` | `integration:vpn` |
| `Nas` | `integration:nas` |
| `Backup` | `core:security` (`LegacyBackupCrypto`) + nowy backup w `core:data` |
| `Art`, `ArtLoadPolicy`, `ImageBackgroundPolicy` | konfiguracja Coil w `core:ui` + priorytety w `core:policy` |
| `Home*`, `Tv*Premium*`, `Cinema*`, `Catalog*`, `Theme`, `MenuLayoutPolicy`, `BkUi*` | nowe UI (`feature:*`, `core:designsystem`); wartości tylko referencyjnie |

## Załącznik B — klucze ustawień do migracji (z `config.settings`; lista niepełna)
`last`, `afr`, `decoder_video`, `overlay`, `autoplay_next`, `fullscreen_clock`, `premium_tv`, `alphabetic`, `compact_epg`, `collapse_library`, `guide_video`, `tmdb_token` (→ SecretStore), `tmdb_auto`, `pin` + `pin_salt`, `active_profile`, `performance_profile`, `art_cache_mb`, `vpn_service`, `vpn_country`, klucz trybu zgodności pilota (`remote_legacy_…`), klucz User-Agent (`StreamUserAgent.SETTING_KEY`), `bk_failover_enabled`, `bk_failover_tv`, `bk_failover_vod`, `bk_failover_series`, `bk_failover_sensitivity`, `bk_failover_tv_delay_sec`, `bk_failover_notify`, `bk_failover_announce`, `bk_failover_continue`, `bk_smart_buffer_18024_migrated`, flagi diagnostyczne `bk_force_okhttp` / `bk_no_reuse_player` / `bk_no_resolve_cache` (→ „Tryb diagnostyczny” lub pominąć), profile bufora i dekodera per dostawca, mapowanie klawiszy kolorowych.
> Pełną listę wygeneruj: `rg -o 'opt(String|Boolean|Int|Long)\("[a-z_0-9]+"' reference/app/src/main/java -r | sort -u` i dołącz tabelę mapowania do `docs/MIGRATION.md`.

## Załącznik C — szablon raportu z etapu
```markdown
# Raport fazy <X> — <data>
## Zrobione (linki do PR)
## DoD — checklista (✅/❌ + dowód: test, screenshot, log)
## Metryki (start, zap, pamięć, czas importu) vs poprzednia faza
## Odstępstwa od konspektu (ADR)
## Ryzyka / znane problemy
## Pytania do właściciela (blokujące 🔒 / nieblokujące)
## Plan następnej fazy
```


---


## Rekomendowane serwery MCP, skille i workflow dla Claude

Projekt: **KETCHUP PLAYER**, odtwarzacz IPTV na Android TV (Kotlin, Jetpack Compose, `androidx.tv:tv-material`). Wszystkie linki sprawdziłem 31.10.2026 i każdy odpowiada HTTP 200.

### (A) Najwyżej oceniane / must-have
- **mobile-mcp (mobile-next)** — https://github.com/mobile-next/mobile-mcp — MCP do sterowania emulatorem lub urządzeniem Android przez adb. Daje zrzuty ekranu, drzewo dostępności, wciskanie klawiszy, tap i swipe. — **wysoka**: Claude może sam wciskać klawisze D-pad na emulatorze TV i oglądać efekt na zrzutach.
- **android-mcp-server (minhalvp)** — https://github.com/minhalvp/android-mcp-server — lekki MCP oparty na adb: komendy shell, screenshot, UI dump, instalacja APK. — **wysoka**: prosty zamiennik lub dodatek do mobile-mcp na prawdziwe TV Boxy po `adb connect IP`.
- **GitHub MCP Server (oficjalny)** — https://github.com/github/github-mcp-server — obsługuje repo, PR-y, issues i przebiegi GitHub Actions z logami i artefaktami. — **wysoka**: pozwala Claude czytać nieudane buildy CI, pobierać artefakty ze zrzutami i otwierać PR-y.
- **Context7 (Upstash)** — https://github.com/upstash/context7 — dostarcza aktualną dokumentację bibliotek do kontekstu. — **wysoka**: API Compose for TV, Media3/ExoPlayer i Focus często się zmienia, a z Context7 Claude nie wymyśla nieistniejących API.
- **Roborazzi** — https://github.com/takahirom/roborazzi — screenshot testing Compose na JVM przez Robolectric, bez emulatora. — **wysoka**: daje szybkie zrzuty ekranów TV (1920×1080) w CI i diff wizualny w PR.
- **android-emulator-runner (ReactiveCircus)** — https://github.com/ReactiveCircus/android-emulator-runner — GitHub Action, który uruchamia emulator, w tym obraz Android TV. — **wysoka**: standard do instrumentowanych testów UI i nawigacji D-pad w CI.
- **taste-skill (leonxlnx)** — https://www.skills.sh/leonxlnx/taste-skill/design-taste-frontend (także `redesign-existing-projects`, `high-end-visual-design`, `minimalist-ui`) — skille "dobrego gustu" w designie: hierarchia, typografia, spacing, unikanie generycznego wyglądu. — **wysoka**: najbliższe temu, czego szukamy ("taste"). Są pisane pod web, ale zasady przenoszą się na Compose. Skill `redesign-existing-projects` pasuje do przebudowy obecnego menu.
- **Superpowers (obra)** — https://github.com/obra/superpowers (skille m.in. `test-driven-development`, `requesting-code-review`) — zestaw workflow: brainstorm, plan, TDD, review, debugowanie. — **wysoka**: narzuca zdyscyplinowaną pętlę plan → test → kod → review przy każdej zmianie UI.

### (B) Warto rozważyć
- **Figma-Context-MCP (Framelink)** — https://github.com/GLips/Figma-Context-MCP — przekazuje Claude layout i style z Figmy. — **średnia**: przydatny, jeśli makiety menu powstaną w Figmie. Bez Figmy nie ma zastosowania.
- **anthropics/skills: frontend-design** — https://www.skills.sh/anthropics/skills/frontend-design — oficjalny skill Anthropic do wyrazistego, nieszablonowego UI. — **średnia**: dobre zasady estetyki, ale pisany pod HTML/React.
- **design-mobile-apps (designed-by-ai)** — https://www.skills.sh/designed-by-ai/skills/design-mobile-apps — skill projektowania aplikacji mobilnych. — **średnia**: dotyczy wzorców mobile, więc dla TV trzeba go połączyć z wytycznymi 10-foot UI.
- **emilkowalski/skills: review-animations, emil-design-eng** — https://www.skills.sh/emilkowalski/skills/review-animations — przegląd animacji i detali interakcji. — **średnia**: przyda się do animacji fokusu (scale, glow) i przejść między ekranami.
- **mattpocock/skills: code-review, codebase-design** — https://www.skills.sh/mattpocock/skills/code-review — skille do przeglądu kodu i projektowania architektury. — **średnia**: ogólne, ale porządkują review PR-ów.
- **android/tv-samples** — https://github.com/android/tv-samples — oficjalne przykłady Google, m.in. JetStream w Compose for TV. — **średnia** (jako kontekst referencyjny raczej wysoka): wzorzec układu menu, fokusu i nawigacji, który warto dać Claude do naśladowania.
- **Paparazzi (Cash App)** — https://github.com/cashapp/paparazzi — screenshot testy bez emulatora, oparte na layoutlib. — **średnia**: alternatywa dla Roborazzi. Wybrać jedno z dwóch.

### (C) Opcjonalne
- **modelcontextprotocol/servers (git, filesystem, fetch)** — https://github.com/modelcontextprotocol/servers — referencyjne serwery MCP. — **niska**: Claude Code ma już natywny dostęp do plików i gita.
- **Playwright MCP** — https://github.com/microsoft/playwright-mcp — sterowanie przeglądarką. — **niska**: przydaje się tylko do panelu web, EPG lub dokumentacji, nie do samej aplikacji TV.
- **Chrome DevTools MCP** — https://github.com/ChromeDevTools/chrome-devtools-mcp — debugowanie stron w Chrome. — **niska**: nie dotyczy natywnej aplikacji.
- **Android-MCP (CursorTouch)** — https://github.com/CursorTouch/Android-MCP — alternatywny MCP do Androida. — **niska/średnia**: zapas na wypadek problemów z mobile-mcp.
- **claude-in-mobile (AlexGladkov)** — https://github.com/AlexGladkov/claude-in-mobile — kolejna integracja Claude z urządzeniami mobilnymi. — **niska**: do sprawdzenia, ale dubluje pozycje z grupy A.
- **awesome-mcp-servers (punkpeye)** — https://github.com/punkpeye/awesome-mcp-servers — katalog serwerów MCP. — **niska** jako narzędzie, przydatny do szukania kolejnych.

---

### Instalacja i konfiguracja (Claude Code), krok po kroku

**0. Wymagania:** Node 18+ (npx), Python 3.11+ z `uv`, Android SDK z `adb` w PATH, emulator z obrazem Android TV (np. `system-images;android-34;android-tv;x86`) albo TV Box z włączonym debugowaniem ADB.

```bash
# Emulator Android TV lokalnie
sdkmanager "system-images;android-34;android-tv;x86" "emulator" "platform-tools"
avdmanager create avd -n tv34 -k "system-images;android-34;android-tv;x86" -d tv_1080p
emulator -avd tv34 &
# albo prawdziwy TV Box:
adb connect 192.168.1.50:5555 && adb devices
```

**1. Serwery MCP (CLI `claude mcp add`):**
```bash
claude mcp add mobile -- npx -y @mobilenext/mobile-mcp@latest
claude mcp add context7 -- npx -y @upstash/context7-mcp
claude mcp add --transport http github https://api.githubcopilot.com/mcp/ \
  -H "Authorization: Bearer $GITHUB_PAT"
# opcjonalnie Figma:
claude mcp add figma -- npx -y figma-developer-mcp --figma-api-key=$FIGMA_API_KEY --stdio
# opcjonalnie android-mcp-server:
git clone https://github.com/minhalvp/android-mcp-server ~/mcp/android-mcp-server
cd ~/mcp/android-mcp-server && uv sync
claude mcp add android -- uv --directory ~/mcp/android-mcp-server run server.py
claude mcp list   # weryfikacja
```

**Alternatywnie plik `.mcp.json` w katalogu repo** (współdzielony z zespołem, bez sekretów w repo):
```json
{
  "mcpServers": {
    "mobile":   { "command": "npx", "args": ["-y", "@mobilenext/mobile-mcp@latest"] },
    "context7": { "command": "npx", "args": ["-y", "@upstash/context7-mcp"] },
    "github":   { "type": "http", "url": "https://api.githubcopilot.com/mcp/",
                  "headers": { "Authorization": "Bearer ${GITHUB_PAT}" } },
    "figma":    { "command": "npx", "args": ["-y", "figma-developer-mcp", "--stdio"],
                  "env": { "FIGMA_API_KEY": "${FIGMA_API_KEY}" } }
  }
}
```
Claude Desktop korzysta z tego samego bloku `mcpServers` w pliku `claude_desktop_config.json`. Wpisy typu `http` łatwiej tam dodać jako connector.

**Zmienne i klucze:**
- `GITHUB_PAT`: fine-grained token z uprawnieniami Contents, Pull requests, Issues i Actions: read/write.
- `FIGMA_API_KEY`: Figma → Settings → Personal access tokens.
- Context7 działa bez klucza. Opcjonalny `CONTEXT7_API_KEY` podnosi limity.
- Klucze trzymaj w `~/.zshrc`/`.env` (plik w `.gitignore`), nigdy w repo.

**2. Skille:**
```bash
npx skills add leonxlnx/taste-skill        # katalog skills.sh → .claude/skills/
npx skills add emilkowalski/skills
npx skills add mattpocock/skills
```
W Claude Code:
```
/plugin marketplace add obra/superpowers-marketplace
/plugin install superpowers@superpowers-marketplace
/plugin marketplace add anthropics/skills
```
Dodaj też własny skill `.claude/skills/android-tv-ui/SKILL.md` z zasadami 10-foot UI:
- fokus zawsze widoczny (scale ok. 1.1 plus obramowanie);
- overscan i bezpieczne marginesy ok. 48 dp po bokach i 27 dp góra/dół;
- tekst min. 16 sp;
- `Modifier.focusRequester`/`focusProperties` i brak "pułapek fokusu";
- przycisk Back zawsze cofa o jeden poziom;
- komponenty z `androidx.tv.material3`.

**3. CLAUDE.md w repo (sterowanie pętlą):**
```markdown
- Build: ./gradlew :app:assembleDebug ; testy: ./gradlew testDebugUnitTest verifyRoborazziDebug
- Przed zmianą UI: użyj skilla redesign-existing-projects + android-tv-ui; API sprawdzaj przez context7.
- Po zmianie: zainstaluj APK (adb install -r), przejdź D-padem (mobile MCP: press DPAD_*), zrób screenshot, oceń.
- Nowy ekran = nowy test Roborazzi + test nawigacji fokusu (compose-ui-test, performKeyInput).
```

**4. Gradle (Roborazzi):** w `build.gradle.kts` modułu app dodaj plugin `io.github.takahirom.roborazzi` oraz zależności `roborazzi`, `roborazzi-compose` i `robolectric`. W testach ustaw `@Config(qualifiers = "w1920dp-h1080dp-xhdpi-television")`, żeby zrzuty miały format TV.

**5. GitHub Actions (`.github/workflows/android-ci.yml`):**
```yaml
name: android-ci
on: [push, pull_request]
jobs:
  build-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 17 }
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew assembleDebug testDebugUnitTest verifyRoborazziDebug
      - uses: actions/upload-artifact@v4
        if: always()
        with: { name: screenshots, path: app/build/outputs/roborazzi }
      - uses: actions/upload-artifact@v4
        with: { name: apk, path: app/build/outputs/apk/debug/*.apk }
  tv-ui-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 17 }
      - name: Enable KVM
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm.rules
          sudo udevadm control --reload-rules && sudo udevadm trigger --name-match=kvm
      - uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 34
          target: android-tv
          arch: x86
          profile: tv_1080p
          script: |
            ./gradlew connectedDebugAndroidTest
            adb exec-out screencap -p > tv-home.png
      - uses: actions/upload-artifact@v4
        if: always()
        with: { name: tv-ui-results, path: "**/build/reports/androidTests/ tv-home.png" }
```

**6. Workflow pracy (pętla dla każdej zmiany menu/UI):**
1. **Plan** (Superpowers: brainstorm, potem plan). Claude opisuje docelowy układ menu: kolumny, rzędy, kolejność fokusu. Opcjonalnie pobiera makietę z Figma MCP.
2. **Taste review**: skill `redesign-existing-projects` lub `design-taste-frontend` plus `android-tv-ui` ocenia obecny zrzut ekranu i proponuje poprawki hierarchii, spacingu i kontrastu.
3. **Test najpierw** (TDD): test Roborazzi dla ekranu oraz test fokusu D-pad (`performKeyInput { pressKey(Key.DirectionRight) }`, asercja `assertIsFocused`).
4. **Implementacja**. API Compose for TV i Media3 Claude sprawdza w Context7.
5. **Weryfikacja na żywo**: `adb install -r`. Przez mobile MCP Claude wciska DPAD_UP/DOWN/LEFT/RIGHT/CENTER i BACK, robi zrzuty i poprawia błędy fokusu.
6. **PR i CI**: przez GitHub MCP Claude otwiera PR, czyta wynik Actions, pobiera artefakty `screenshots` i `tv-ui-results`, a przy porażce czyta log i poprawia kod.
7. **Review**: `requesting-code-review` lub `code-review`, potem `emil-design-eng`/`review-animations` dla animacji fokusu. Na końcu merge.

---

## Wzorzec jakości: TiViMate, ale lepiej

KETCHUP PLAYER ma mieć co najmniej to, co ma TiViMate (uznawany za wzorcową aplikację IPTV na Android TV), i przewyższyć go stabilnością, buforem i szybkością.

### 1. Co przejmujemy z TiViMate (parytet funkcji)

- **Przewodnik EPG**: siatka czas × kanał, przewijanie D-padem bez lagów, skok „teraz”, ±1 dzień PgUp/PgDn (CH+/CH−), mini-podgląd wideo w rogu, szczegóły programu po OK.
- **Szybkie przełączanie kanałów**: CH+/CH−, cyfry z pilota (wpisany numer + timeout 1,5 s), „ostatni kanał” (przycisk BACK/LAST), lista kanałów jako overlay na odtwarzaniu.
- **Wielolistowość**: wiele playlist M3U/M3U8 i Xtream Codes, każda z własnym EPG (XMLTV, `.xml.gz`), automatyczne odświeżanie w tle (WorkManager, np. co 12 h + przy starcie, jeśli dane mają >24 h).
- **Grupy i ulubione**: grupy z `group-title`, ukrywanie i sortowanie grup, własne listy ulubionych, ręczna kolejność, numeracja kanałów.
- **Catch-up / archiwum**: `catchup`, `catchup-source`, `catchup-days` (typy `default`, `append`, `shift`, `flussonic`, `xc`); odtwarzanie przeszłych programów z EPG.
- **Nagrywanie** (jeśli dostawca na to pozwala): zaplanowane nagrania z EPG do pamięci lokalnej lub USB, z użyciem foreground service.
- **Czysty UI pod pilota**: wszystko osiągalne D-padem, przewidywalny fokus, brak elementów wymagających dotyku.
- **Stabilność**: automatyczny reconnect po zerwaniu strumienia, aplikacja nie crashuje na złej playliście lub złym EPG (parsowanie defensywne, błędy per wpis).

### 2. Lepiej niż TiViMate

#### 2.1 Większy, konfigurowalny bufor (Media3 `DefaultLoadControl`)

Domyślne wartości Media3 to min 50 s, max 50 s, `bufferForPlaybackMs` 2,5 s i after-rebuffer 5 s. Dla live IPTV ustawiamy własne profile, które użytkownik wybiera w Ustawieniach → Odtwarzanie → Bufor:

| Profil | minBufferMs | maxBufferMs | bufferForPlaybackMs | bufferForPlaybackAfterRebufferMs | backBufferMs | targetBufferBytes |
|---|---|---|---|---|---|---|
| Szybki (słaby box, ≤2 GB RAM) | 15 000 | 30 000 | 1 000 | 3 000 | 0 | 16 MB |
| **Zrównoważony (domyślny)** | 30 000 | 60 000 | 1 500 | 4 000 | 10 000 | 48 MB |
| Maks. stabilność | 60 000 | 120 000 | 2 500 | 8 000 | 30 000 | 96 MB |
| Własny | suwaki 15–120 s / 0,5–10 s | | | | | |

```kotlin
val loadControl = DefaultLoadControl.Builder()
    .setAllocator(DefaultAllocator(/* trimOnReset = */ true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
    .setBufferDurationsMs(p.minMs, p.maxMs, p.playbackMs, p.afterRebufferMs)
    .setTargetBufferBytes(p.targetBytes)
    .setPrioritizeTimeOverSizeThresholds(true) // live: liczy się czas, nie bajty
    .setBackBuffer(p.backBufferMs, /* retainBackBufferFromKeyframe = */ true)
    .build()
```

- **Trade-off**: większy bufor daje odporność na skoki sieci i serwera, ale kosztuje RAM (stream 1080p ~8 Mb/s × 60 s ≈ 60 MB) i większe opóźnienie względem „na żywo”. Start i zmiana kanału zależą głównie od `bufferForPlaybackMs`, a nie od max, więc trzymamy go nisko (1–1,5 s) i pozwalamy buforowi rosnąć już w trakcie grania.
- **Adaptacja do sprzętu**: przy pierwszym starcie `DeviceProfiler` czyta `ActivityManager.memoryClass`, `isLowRamDevice`, `MemoryInfo.totalMem`, liczbę rdzeni i obsługiwane kodeki (`MediaCodecList`). Klasy: LOW (<2 GB lub lowRam) → Szybki, MID (2–3 GB) → Zrównoważony, HIGH (≥4 GB) → Zrównoważony z możliwością Maks. W `onTrimMemory(TRIM_MEMORY_RUNNING_LOW)` profil spada o jeden poziom.
- **Live offset**: `MediaItem.LiveConfiguration` z `targetOffsetMs` 8–15 s (zależnie od profilu), `minPlaybackSpeed` 0,97 i `maxPlaybackSpeed` 1,03, żeby płynnie dogonić live bez skoków.
- **Rebuffer telemetry**: liczymy rebuffery i czas startu przez `AnalyticsListener` i pokazujemy je w panelu debug. Po 3 rebufferach w 5 minut sugerujemy wyższy profil.

#### 2.2 Stos sieci i odtwarzania

- **DataSource**: `OkHttpDataSource.Factory` na współdzielonym `OkHttpClient`:
  - `ConnectionPool(10, 5, MINUTES)` (keep-alive) i HTTP/2;
  - `connectTimeout` 8 s, `readTimeout` 15 s (oba konfigurowalne);
  - `retryOnConnectionFailure(true)`, `followRedirects(true)` (także http↔https, ważne przy IPTV);
  - własny User-Agent per playlista oraz nagłówki `http-referrer` / `http-user-agent` z `#EXTVLCOPT`.
- **Reconnect**: `DefaultLoadErrorHandlingPolicy` z własnym `getMinimumLoadableRetryCount` = 6 i backoffem 0,5 → 1 → 2 → 4 s. Przy `BehindLiveWindowException` robimy `seekToDefaultPosition()` + `prepare()`. Przy błędzie fatalnym następuje automatyczne ponowne `setMediaItem` do 3 razy, a potem komunikat z przyciskiem „Spróbuj ponownie”.
- **Formaty**: `HlsMediaSource` (z `setAllowChunklessPreparation(true)` dla szybkiego startu), `DashMediaSource`, `ProgressiveMediaSource` z `DefaultExtractorsFactory().setTsExtractorFlags(FLAG_ALLOW_NON_IDR_KEYFRAMES or FLAG_DETECT_ACCESS_UNITS)` dla MPEG-TS po HTTP. Typ wykrywamy po rozszerzeniu lub `Content-Type`, a fallback to progressive.
- **Szybki start i ABR**: `DefaultTrackSelector` z `setForceHighestSupportedBitrate(false)` i `DefaultBandwidthMeter.Builder().setInitialBitrateEstimate(5_000_000)`. Ostatnią zmierzoną przepustowość zapamiętujemy w DataStore i używamy jako estymaty przy kolejnym starcie.
- **Preload sąsiednich kanałów**: Media3 `DefaultPreloadManager` (1.4+). Przygotowujemy kanał N−1 i N+1 (tylko manifest/pierwszy segment, ~2 s) na kanałach z ulubionych lub aktualnej grupy. Wyłączone na profilu LOW. Alternatywa na starszych wersjach Media3: druga, wyciszona instancja `ExoPlayer` bez surface. Cel: zmiana kanału <700 ms na HLS w sieci LAN/Wi-Fi 5 GHz.
- **Jedna instancja playera**: przy zmianie kanału robimy `setMediaItem` + `prepare` na tej samej instancji ExoPlayer, bez release i ponownego tworzenia, żeby nie ładować kodeków na nowo.
- **Dekodery**: `DefaultRenderersFactory.setEnableDecoderFallback(true)` i `EXTENSION_RENDERER_MODE_PREFER` dla audio (FFmpeg extension: AC-3, E-AC-3, DTS, MP2 na boxach bez licencji). Wideo idzie domyślnie sprzętowo, a w ustawieniach jest przełącznik HW/SW per kanał na wypadek wadliwych boxów (np. Amlogic z problemami MPEG-2 i interlace).
- **Tunneling**: `trackSelector.setParameters(buildUpon().setTunnelingEnabled(true))` tylko gdy `CodecCapabilities.FEATURE_TunneledPlayback` jest dostępne. Domyślnie ON na Android TV z whitelistą, z przełącznikiem w ustawieniach, bo na części SoC tunneling psuje napisy i A/V sync.
- **Surface**: `SurfaceView` (nie `TextureView`) dla HDR, tunnelingu i niższego zużycia GPU. Frame-rate matching przez `Surface.setFrameRate` / `setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS)`, z opcją „zawsze” (zmiana trybu wyświetlacza 50/60 Hz).
- **Ograniczenie przeładowań**: brak `prepare()` przy zmianie overlay/UI, player żyje w `PlaybackService` (Media3 `MediaSessionService`) niezależnie od rekompozycji, a `MediaItem` jest cache'owany per kanał.

#### 2.3 Wydajny EPG i listy (10k+ kanałów, 500k+ programów)

- **Storage**: Room + FTS4 dla wyszukiwania. Indeksy `(channelId, startUtc)` i `(endUtc)`. Programy starsze niż `now − catchupDays` są czyszczone przez Worker.
- **Import**: strumieniowy parser XMLTV (`XmlPullParser` na `GZIPInputStream`, bez DOM), insert batchami po 2 000 w jednej transakcji. Cel: 100 MB XML w <60 s na boxie MID. Playlistę M3U parsujemy liniowo przez okio `BufferedSource`.
- **Lista kanałów**: `TvLazyColumn` / `LazyColumn` (wg wersji tv-foundation) + Paging 3 (`PagingSource` z Room, `pageSize` 50, `prefetchDistance` 100). Stabilne `key = channel.id` i `contentType`, klasy `@Immutable`.
- **Siatka EPG**: okno czasowe ±3 h wokół fokusu, dociągane przy przewijaniu. Wiersz to `LazyRow` z programami, a szerokość wynika z `durationMs × dpPerMinute`. Wiersze są renderowane tylko w obrębie viewportu + 5. Oś czasu jest współdzielona przez `ScrollState`.
- **Cache**: logo przez Coil 3 (memory cache 15% heap na LOW / 25% na HIGH, disk cache 250 MB, `size` dopasowany do slotu, `allowHardware(true)`); bieżący i następny program w pamięci (`LruCache<channelId, NowNext>`).
- **Brak janku**: baseline profiles (`androidx.baselineprofile`) dla startu, EPG i listy; Macrobenchmark w CI z progami: cold start <1,5 s, jank w EPG <1% klatek (`FrameTimingMetric`, P95 <16 ms). Strict mode w debug, wszystkie I/O na `Dispatchers.IO`, a w kompozycji nie ma sortowania ani filtrowania (robią to `derivedStateOf` / ViewModel).

## Kompatybilność i wymagania platformy

**Twarde wymaganie: aplikacja MUSI działać na każdym Android TV / TV boxie / telewizorze / Fire TV z Androidem 9 (API 28) lub nowszym.** Próg 9 jest świadomy: łapie Fire TV Stick (Fire OS 7 = Android 9) i starsze Fire OS 8 = Android 11, Chromecasty, telewizory Google/Android TV oraz większość tanich boxów Amlogic/Rockchip, które jadą na AOSP 9–11. Android 12+ odciąłby Fire Sticki i sporą część rynku, więc go nie wymagamy.

- **SDK**: `minSdk = 28` (Android 9), `targetSdk = 35` (zawsze aktualne wymaganie Google Play), `compileSdk = 35`. Każdy kod wymagający nowszego API chronimy przez `Build.VERSION.SDK_INT` i adnotację `@RequiresApi`, z gracefulnym fallbackiem na API 28 (np. SplashScreen przez `core-splashscreen`, skróty klawiszowe, PiP od API 28). Lint `NewApi` = error, żeby nic nie wywaliło aplikacji na Androidzie 9.
- **Compose for TV**: `androidx.tv:tv-material` + `androidx.compose.foundation` (minSdk bibliotek ≤ 21, więc API 28 jest bezpieczne). Wersje przypinamy przez Compose BOM, a każda aktualizacja BOM wymaga zielonego CI na najniższym wspieranym API (28).
- **ABI**: `abiFilters += listOf("armeabi-v7a", "arm64-v8a")` (Fire Sticki i wiele tanich boxów mają 32-bitowy userspace mimo SoC ARM64 — `armeabi-v7a` jest tu obowiązkowe), do tego `x86_64` tylko dla emulatora i debug. Natywne biblioteki (FFmpeg extension) budujemy dla obu ARM-ów. Dla sideloadu publikujemy split APK per ABI plus universal APK, a do Play AAB.
- **Bez Google Play Services**: zero zależności od GMS (żadnego Firebase Crashlytics, FCM ani Play Integrity jako wymogu). Crashe zbiera ACRA lub Sentry (self-hosted), aktualizacje idą przez własny check wersji lub Play, jeśli jest dostępny (`GoogleApiAvailability` tylko opcjonalnie przez refleksję lub osobny flavor `play`). Flavory `play` i `sideload`.
- **Rozdzielczości i DPI**: 720p (tvdpi/213), 1080p (xhdpi/320), 4K (xxxhdpi/640, UI renderowane w 1080p). Layout w `dp` z bazą 960×540 dp. Overscan: marginesy 48 dp poziomo i 27 dp pionowo (5%). Testy screenshotów Roborazzi dla `tv_720p`, `tv_1080p` i `tv_4k`.
- **Manifest (leanback)**:
  ```xml
  <uses-feature android:name="android.software.leanback" android:required="false"/>
  <uses-feature android:name="android.hardware.touchscreen" android:required="false"/>
  <uses-feature android:name="android.hardware.faketouch" android:required="false"/>
  <application android:banner="@drawable/banner_320x180" ...>
    <activity ...>
      <intent-filter>
        <action android:name="android.intent.action.MAIN"/>
        <category android:name="android.intent.category.LEANBACK_LAUNCHER"/>
        <category android:name="android.intent.category.LAUNCHER"/>
      </intent-filter>
  ```
  `leanback required=false` pozwala instalować aplikację też na boxach z „telefonowym” Androidem (AOSP bez certyfikacji ATV). Kategoria `LAUNCHER` jest dodana z tego samego powodu.
- **Piloty**: obsługujemy `KEYCODE_DPAD_*`, `DPAD_CENTER`/`ENTER`/`NUMPAD_ENTER`, `BACK`/`ESCAPE`, `CHANNEL_UP/DOWN`, `PAGE_UP/DOWN` (fallback zmiany kanału), `0–9` i `NUMPAD_0–9`, `MEDIA_PLAY_PAUSE`/`PLAY`/`PAUSE`/`STOP`/`FAST_FORWARD`/`REWIND`, `GUIDE` (→ EPG), `INFO`, `MENU`, `PROG_RED/GREEN/YELLOW/BLUE`, `LAST_CHANNEL`. Obsługa long-press przez `KeyEvent.isLongPress` / `repeatCount`. Działają też mysz typu air-mouse i klawiatura BT. Mapowanie klawiszy jest konfigurowalne w Ustawieniach.
- **Testy na urządzeniach**: smoke test na realnym sprzęcie co release, z naciskiem na dolny próg: Fire TV Stick (Fire OS 7/Android 9, 32-bit, bez GMS) i Fire TV Stick 4K Max (Fire OS 8/Android 11), Chromecast z Google TV, Xiaomi Mi Box S, tani box Amlogic S905W2/S905X4 z AOSP (32-bit, bez GMS), Nvidia Shield, telewizor Sony/TCL z Google TV.

### Kryterium CI (twarde, blokuje merge)

```yaml
instrumented-tv:
  runs-on: ubuntu-latest
  strategy:
    fail-fast: false
    matrix:
      api-level: [28, 30, 31, 34]   # 28 = dolny próg (Android 9/Fire TV), 34 = góra
      target: [android-tv]
      arch: [x86]        # obrazy ATV; API 34 może wymagać x86_64 — dopasować wg dostępności
  steps:
    - uses: reactivecircus/android-emulator-runner@v2
      with:
        api-level: ${{ matrix.api-level }}
        target: ${{ matrix.target }}
        arch: ${{ matrix.arch }}
        profile: tv_1080p
        disable-animations: true
        script: ./gradlew connectedDebugAndroidTest
```

- Job `instrumented-tv` dla **API 28 jest required status check** w branch protection. Bez zielonego API 28 (dolny próg, Android 9/Fire TV) nie ma merge'a.
- Do tego `./gradlew lint` z `NewApi` jako error oraz `checkAarMetadata`, które wyłapuje biblioteki z minSdk > 28.
- Test startowy na każdym API: uruchomienie z `LEANBACK_LAUNCHER`, załadowanie testowej playlisty M3U (lokalny serwer MockWebServer z HLS), odtworzenie 10 s bez błędu (`Player.STATE_READY`, 0 `PlaybackException`), zmiana kanału CH+ i nawigacja D-padem po EPG.

---

## User-Agent: lista do wyboru + własny

Wielu dostawców IPTV filtruje albo wręcz wymaga konkretnego nagłówka `User-Agent` (niektóre strumienie działają tylko z UA znanego playera, inne blokują domyślny UA ExoPlayera). Dlatego KETCHUP PLAYER musi dać:

- **Globalny UA** w Ustawieniach → Sieć (lista rozwijana z gotowymi wartościami + pole „Własny…”).
- **Nadpisanie per playlista** oraz **per kanał** (dziedziczenie: kanał → playlista → globalny → domyślny aplikacji).
- Odczyt UA z samej playlisty M3U: `#EXTVLCOPT:http-user-agent=…` oraz `|User-Agent=…` w URL strumienia mają pierwszeństwo.
- Pełny podgląd realnie wysyłanego nagłówka (jak na zrzutach z „Żądanie”), żeby użytkownik mógł zweryfikować, co leci do serwera.

### Gotowe wartości do wyboru (presety)

| Nazwa w UI | Wartość `User-Agent` | Uwagi |
|---|---|---|
| Domyślny (Ketchup) | `KetchupPlayer/<wersja> (Android <SDK>; <model>)` | domyślny, własny |
| ott-play | `ott-play/2.01` | popularny PL/CIS player (zrzut) |
| Wink | `Wink/1.31.1` | operatorski player (zrzut) |
| VLC (Android) | `VLC/3.5.4 LibVLC/3.5.4` | bardzo często akceptowany |
| VLC (desktop) | `VLC/3.0.20 LibVLC/3.0.20` | wariant desktopowy |
| Kodi | `Kodi/20.2 (Linux; Android 11) ...` | duża zgodność |
| TiviMate | `TiviMate/4.7.0 (Android)` | wzorcowy player |
| OTT Navigator | `OTT Navigator/1.7` | popularny |
| IPTV Smarters | `IPTVSmartersPlayer` | szeroko wspierany |
| Lavf / FFmpeg | `Lavf/60.16.100` | silnik FFmpeg, często wymagany |
| ExoPlayer | `ExoPlayerLib/2.19.1` | gdy serwer oczekuje ExoPlayera |
| Dalvik | `Dalvik/2.1.0 (Linux; U; Android 9; ...)` | „systemowy” UA |
| Mozilla (przeglądarka) | `Mozilla/5.0 (Linux; Android 9; ...) AppleWebKit/537.36 ...` | obejście prostych filtrów |
| GStreamer | `GStreamer souphttpsrc` | niektóre strumienie |
| smarters / XCIPTV | `XCIPTV` / `smarters` | panele Xtream |
| Własny… | pole tekstowe | dowolna wartość, zapis per zakres |

> Wartości wersji to przykłady — w kodzie trzymać je jako edytowalną, aktualizowalną listę (np. w `assets/user_agents.json`, możliwa aktualizacja przez OTA), a nie na sztywno. Nie promować UA służących do omijania zabezpieczeń cudzych serwerów; lista ma pomagać w legalnym odtwarzaniu własnych/legalnych playlist, które wymuszają konkretny nagłówek.

### Implementacja
- UA wstrzykiwany w `DataSource.Factory` (OkHttp interceptor), nie globalnie w systemie.
- Zmiana UA bez restartu aplikacji; przy aktywnym odtwarzaniu — reinit strumienia.
- Zapis w `DataStore`/Room razem z definicją playlisty i kanału.
- Diagnostyka: ekran „Podgląd żądania” pokazujący metodę, URL (z zamaskowanym `secret_token`/hasłem), nagłówki i kod odpowiedzi — do szybkiego debugowania „czemu kanał nie działa”.

---

## Motywy kolorystyczne (decyzja)

Aplikacja ma 3 stonowane, ciemne motywy w stylu TiViMate (jeden akcent na motyw, zieleń zarezerwowana wyłącznie dla wskaźnika nowych odcinków, kontrasty WCAG AA):
- **Turkus — motyw DOMYŚLNY** (decyzja użytkownika).
- **Szary.**
- **Bursztyn** (stłumione złoto z logo).

Użytkownik przełącza motyw w Ustawieniach → Wygląd. Motywy realizować jako `Theme`/`ColorScheme` w Compose for TV (tokeny kolorów, bez hardkodów), gotowe na dodanie kolejnych. Makiety referencyjne (v2) są w folderze „Ketchup Player” na Dysku (podfoldery per motyw).

---

## Integracje serwisów (Trakt + metadane)

Aplikacja ma pozwalać na zalogowanie się do zewnętrznych serwisów i synchronizację, każdy wg jego **oficjalnej, aktualnej dokumentacji API** (sprawdzić wersje endpointów na etapie implementacji — poniżej stan orientacyjny):

- **Trakt.tv** (priorytet — użytkownik ma konto):
  - Logowanie: **OAuth 2.0 Device Code flow** (idealne dla TV/pilota) — aplikacja pokazuje kod + `trakt.tv/activate`, odpytuje `POST /oauth/device/token` do czasu autoryzacji; token odświeżany `refresh_token`. Nagłówki: `trakt-api-version: 2`, `trakt-api-key: <client_id>`, `Authorization: Bearer`.
  - Funkcje: scrobbling (start/pause/stop) filmów i odcinków, oznaczanie obejrzanych, synchronizacja postępu (`playback`), listy (watchlist, kolekcje, ulubione), „kontynuuj oglądanie”, rekomendacje.
  - Mapowanie: dopasowanie VOD/seriali z playlist do pozycji Trakt po tytule/roku/odcinku (+ ID z TMDB, gdy dostępne).
  - Klucze `client_id`/`client_secret` poza repo (BuildConfig z lokalnych properties / sekrety CI), token w `SecretStore` (Tink + Keystore).
- **TMDB** — metadane i okładki (już w konspekcie); logowanie opcjonalne (konto TMDB) dla własnych list.
- **Simkl** — alternatywa/uzupełnienie Trakta (device-code OAuth), opcjonalnie.
- **OpenSubtitles** — logowanie wg ich REST API (klucz API + login) dla napisów.

Architektura: wspólny interfejs `SyncService` (login/logout, markWatched, scrobble, getProgress, lists), po jednej implementacji na serwis, włączane niezależnie w Ustawieniach → Konta. Każda integracja całkowicie opcjonalna i wyłączalna; brak konta nie blokuje odtwarzania.

## Zasada dla repozytorium GitHub „ketchup-player” (MUSI)

- Repo zawiera **wyłącznie nowy kod** pisany od zera (Kotlin + Compose). 
- **Zakaz** wrzucania do repo starych plików źródłowych z BK-Player-Next (`Main.java`, klasy `*Policy`, stary układ zasobów itd.) oraz **zakaz** korzystania z nich przez lokalny serwer/katalog na komputerze użytkownika w trakcie pracy.
- Stary kod jest tylko **materiałem referencyjnym offline** (opisanym w tym konspekcie i w ANALIZA/PRZEBUDOWA) — Cloud przenosi z niego *zachowanie/logikę* (np. reguły failoveru, parsowanie), ale przepisuje je na nowo; nie kopiuje plików.
- Żadnego mieszania obu baz kodu w jednym repo; brak katalogu `legacy/` ze starymi źródłami. Jeśli potrzebny jest fragment logiki, opisać go w PR/commit message, a nie wklejać starego pliku.

## Decyzja: bez debrid i scraperów

> Decyzja właściciela z 2026-10-31: **Real-Debrid, TB7, scrapery w stylu FanFilm (Kodi) oraz URL resolver są poza zakresem** — ewentualnie w przyszłości, po osobnej decyzji. **Nie implementować** (żadnych modułów, interfejsów, stubów ani ustawień z nimi związanych).
>
> Rdzeń aplikacji = dostawcy **M3U / Xtream / Stalker** + metadane **TMDB / Trakt** (oraz napisy OpenSubtitles) — zgodnie z sekcją „Integracje serwisów (Trakt + metadane)”.
