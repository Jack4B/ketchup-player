# Ketchup Player — konspekt inżynieryjny personalizacji behawioralnej

**Status:** projekt funkcji (nie implementacja).  
**Język docelowy UI:** polski.  
**Data dokumentu:** 8 listopada 2026 (Europe/Warsaw).  
**Adresat:** późniejsze mikro-PR dla Claude (lead coding) i niezależny review.  
**Miejsce w planie produktu:** funkcja przyjęta do backlogu produktu (decyzja właściciela, 8.11.2026) — realizacja **po pierwszym RC**, bez czekania na pełne 1.0 i bez rozszerzania zakresu bieżącego RC. Stan: **niezaimplementowana**; wpis w `docs/PLAN-FINALNY.md` (Krok 9). Funkcja nie blokuje bieżących prac (fokus D-pad, testy, PR #11 w draftcie z blokadą pobierania Gradle). Nie mieszać tej paczki z istniejącymi PR-ami ani nie zmieniać CI „żeby personalizacja działała”.

Ten plik jest materiałem inżynierskim do rozbicia na małe, weryfikowalne zadania. Nie jest obietnicą „AI, które zna użytkownika”, ani opisem sprawdzonego modelu uczenia maszynowego. Proponowany silnik to lokalne, interpretowalne ważenie cech z zanikiem czasu, kurczeniem (shrinkage) i osobnym torem sportowym.

Wszystkie parametry liczbowe w formułach są **propozycjami do strojenia**, nie wynikami pomiarów na danych użytkowników.

Nazwy interfejsów, tabel, klas i modułów oznaczone jako **PROJEKT** nie istnieją w kodzie (albo nie w sprawdzonym checkoutcie). Nie należy ich traktować jako stanu faktycznego repozytorium.

---

## 0. Źródło wiedzy o kodzie i zastrzeżenia

> **Migawka historyczna:** opis kodu poniżej pochodzi ze starego checkoutu `677cdde`, nie z aktualnego `dev`. Przed implementacją (P0) trzeba go zweryfikować na bieżącej gałęzi.

Sprawdzono wyłącznie do odczytu lokalny, **stary** checkout:

| Pole | Wartość |
| --- | --- |
| Ścieżka | `/workspace/ketchup-review/dev-pr` |
| SHA | `677cddebc997ab3ee93afd3bfee23b90420d3124` |
| Data commita (lokalna, Europe/Warsaw) | 7 listopada 2026, 08:50 +0100 |
| Temat commita | `docs: add MIT ub-mode skill with project-first guardrails` |
| Gałąź lokalna | `feat/v5.1-mockups-docs-from-dev` |
| Remote | `https://github.com/Jack4B/ketchup-player.git` |
| Fetch / zmiana repo | **nie wykonano** |

Checkout **nie jest aktualnym `origin/dev`**. Późniejsze prace (m.in. diagnostyka fokusu, testy D-pad, kolejne SHA wymieniane w rozmowie o CI) mogą nie być w tym drzewie. Przed implementacją trzeba ponownie odczytać aktualny `dev`.

Założenia, których nie potwierdzono w tym checkoutcie, są oznaczone jako **ZAŁOŻENIE**.

---

## 1. Cel produktu

Aplikacja ma z czasem uczyć się, *co użytkownik faktycznie ogląda na tym urządzeniu*, i pokazywać na Home użyteczne podpowiedzi:

1. **Sport (osobny tor):** jeśli ktoś regularnie ogląda mecze tej samej drużyny, Home pokazuje, *kiedy jest następne spotkanie* i *na jakim kanale z katalogu użytkownika da się je obejrzeć* — albo wyraźnie „kanał niepotwierdzony”, gdy pewność jest za niska.
2. **Filmy:** gatunki, reżyserzy, obsada i inne cechy z wiarygodnych metadanych wpływają na rząd „Może Ci się spodobać” (lub równoważny), wyłącznie spośród pozycji już obecnych u dostawcy użytkownika.
3. **Seriale:** to samo plus ciągłość (następny odcinek), osobny limit wpływu binge-watchu i ostrożność ze spoilerami.

Uczenie ma być:

- **lokalne** (Room + WorkManager, bez LLM, bez chmury rekomendacji, bez opłat API w MVP);
- **opcjonalne** (opt-in / da się wyłączyć);
- **wyjaśnialne** („bo często oglądasz X”, „obserwujesz drużynę Y”);
- **edytowalne** (użytkownik może potwierdzić, poprawić lub usunąć wniosek);
- **bezpieczne** (zero sekretów list, tokenów i URL-i strumieni w telemetrii).

### 1.1. Przykład UX — syntetyczny, nie prawdziwy mecz

Scenariusz poglądowy (dane wymyślone, **nie** nadchodzący terminarz):

> Domowy profil „Salon” przez kilka sobót ogląda na żywo audycje EPG zatytułowane w stylu „Ekstraklasa: Wisła Nadmorska — Hutnik Leśny”. Po trzecim niezależnym terminie system **proponuje** obserwowanie „Wisła Nadmorska” (nie przypisuje kibicowania po jednym meczu i nie zgaduje strony derby). Po jawnym potwierdzeniu karta na Home mówi: „Najbliższy mecz Wisły Nadmorskiej: niedziela 15:00 (Europe/Warsaw) · kanał Sportowy Przykład HD”. Jeśli EPG nie pozwala potwierdzić kanału w katalogu użytkownika: „kanał niepotwierdzony”.

Równoległy przykład VOD (też syntetyczny):

> Użytkownik dokończył dwa filmy kryminalne tego samego reżysera i dodał jeden do Mojej listy. Home pokazuje rząd filmów z katalogu dostawcy o podobnym gatunku i reżyserze, z podpisem „Bo oglądałeś kryminały” — bez pozycji, których nie ma w bibliotece.

---

## 2. Zasady nadrzędne (nie negocjowalne w MVP)

1. **Local-first.** Profil i historia zostają na urządzeniu. Brak serwera rekomendacji, brak wysyłki historii oglądania.
2. **Bez LLM i bez kosztów chmury w MVP.** Opcjonalne wzbogacanie metadanych (TMDB itd.) już istnieje w aplikacji jako *osobna* funkcja kluczy użytkownika — personalizacja z niego korzysta, ale nie wymaga go do startu.
3. **Tylko zasoby z inwentarza.** Kandydat musi istnieć w katalogu aktywnych dostawców użytkownika (kanał, film, odcinek). Nie rekomendujemy treści „które powinny być w TV”, jeśli nie ma ich na liście.
4. **Nie wymyślamy metadanych.** Brak reżysera / obsady / gatunku = cecha nieznana, nie domysł z tytułu. Niskiej pewności dopasowania tytułu nie używamy do scoringu cech.
5. **Nie wymyślamy kanału emisji.** Licencjonowany nadawca w źródle terminarza ≠ kanał u dostawcy użytkownika.
6. **Jawne > niejawne.** Polubienia, obserwowanie drużyny, „to nie ja / nie pokazuj” ważą więcej niż czas oglądania.
7. **Krótkie wyjścia i awarie są neutralne**, nie są oceną „nie lubię”.
8. **Sport i VOD to osobne pasy (lanes)** z osobnymi kandydatami, rankingu i kartami Home.
9. **Nie blokujemy RC ani 1.0.** Implementacja po pierwszym RC i poza bieżącymi PR-ami jakości.
10. **Proste ważenie to nie „sprawdzony ML”.** Dokument opisuje baseline inżynierski. Większy model ma sens dopiero po metrykach z punktu 21.

---

## 3. Stan faktyczny w sprawdzonym checkoutcie

### 3.1. Stos

Kotlin, Jetpack Compose for TV, Hilt, Room (`KetchupDatabase` **wersja 7**, `exportSchema = true`, AutoMigration 1→7), DataStore, Media3 ExoPlayer (`:player:engine`), dostawcy `:provider/{api,m3u,xtream}` (szkielet Stalker), EPG `:epg/{xmltv,radar}` (szkielet Teleman), funkcje `:feature/{home,guide,vod,details,player-ui,search,mylist,…}`.

Z `CLAUDE.md` (w tym checkoutcie): puste szkielety m.in. `:feature:profiles`. Profile z PIN-em są planowane na **1.1**, nie są wymagane do personalizacji MVP.

### 3.2. Co już jest i da się wykorzystać

| Element | Stan | Wniosek dla personalizacji |
| --- | --- | --- |
| `ChannelFlagEntity.lastWatchedMs` + `favorite` | jest | Znacznik ostatniego kanału, nie historia sesji ani aktywny czas |
| `VodStateEntity` (`myList`, `positionMs`, `durationMs`, `updatedMs`) | jest | Postęp i lista; brak rozróżnienia sesji, pauzy, buforowania, seek |
| `ResumeRules` | jest | Progi wznowienia / ukończenia filmu (97%) i „w trakcie” (30 s … 95%) — **wzorzec do spójności**, nie zastępstwo historii |
| `PlaybackStats` / `PlaybackStatsCollector` | jest | Liczy `playedMs`, `rebuffers`, `stallMs` w pamięci sesji odtwarzacza (panel Info). **Nie zapisuje** ich jako historii preferencji |
| `InterestsRepository` (DataStore, max 30 słów) | jest | Jawne słowa kluczowe w Przewodniku |
| `InterestMatcher` | jest | Podciągi w tytule/opisie EPG po `Channel.normalizeName` (diakrytyki PL) |
| `RadarEngine` / `RadarPolicy` | jest | Ogólny ranking audycji + dedup tej samej audycji na wielu kanałach + bonus ulubionego kanału. **To nie jest profil użytkownika** |
| `VodItem` / `VodItemEntity` | `genre`, `cast`, brak osobnego `director` | Reżyser to luka metadanych; TMDB `credits` są pobierane, ale parser zapisuje obsadę, nie reżysera (`TmdbSource`) |
| `VodRepository.similar()` | jest | Proste nakładanie kategorii + tokenów gatunku. Baseline, nie profil |
| `HomeUiState` | rzędy: recent, liveNow, featured/resume, continueVod, myList, newEpisodes, timed | Brak rzędu sportowego i rzędu „dla Ciebie” |
| `EpgProgramme` | `title`, `description`, `category`, czasy w **ms UTC** | Brak struktury drużyn/lig; okno przyszłości zależy od źródła XMLTV |
| `Channel.epgId` | jest | Naturalny klucz dopasowania kanału (tvg-id) |
| `Channel.normalizeName` | jest | Normalizacja PL (`ł→l`, NFD, usuwanie diakrytyków) — punkt startu aliasów |
| `VodMetaEntity` | oceny TMDB/IMDb/RT/Metacritic/Trakt + `found` | Provenance ocen; nie cech do profilu |
| Moduł metadanych | TMDB, OMDb, MDBList, Trakt, Wikipedia, indeks Wikidata | Opcjonalne wzbogacanie; klucze użytkownika / sekrety CI, nigdy w repo |
| `:feature:profiles` | pusty szkielet | Personalizacja MVP działa na profilu **anonimowym gospodarstwa** |

### 3.3. Czego nie ma (luki)

- trwałej historii sesji oglądania z aktywnym czasem,
- deduplikacji wznowień do jednej sesji,
- profilu wag cech (gatunek / reżyser / aktor / drużyna / liga),
- generatora kandydatów opartego o profil,
- rankingu z różnorodnością i eksploracją pod personalizację,
- encji drużyn, aliasów, obserwowania, wydarzeń meczowych i dowodu dostępności kanału,
- ustawienia opt-in personalizacji,
- cache’owanego modelu Home pod personalizację,
- WorkManagera pod przeliczanie profilu (w planie 1.1 WorkManager pojawia się przy przypomnieniach Radaru — **to inna funkcja**).

### 3.4. Powiązane dokumenty projektu

- `docs/PLAN-1.0.md`, `docs/PLAN-FINALNY.md`: 1.0 = jakość codziennego użytku; profile/PIN i scoring między sesjami (w sensie *stabilności odtwarzania*) są przesunięte. Niniejsza personalizacja jest **nową** funkcją, nie punktem 1.0.
- `docs/adr/teleman.md`: scraping Telemana za flagą; ryzyko prawne. Personalizacja **nie** opiera sportu na scrapowaniu nielegalnych strumieni ani na obchodzeniu regulaminów.
- `CLAUDE.md`: zakaz sekretów w repo; fixture’y tylko publiczne (np. iptv-org) i dane syntetyczne.

---

## 4. Zakres, poza zakresem, relacja do 1.0

### 4.1. W zakresie (etapami)

1. Zgoda i ustawienia retencji.
2. Zdarzenia odtwarzania → sesje → historia.
3. Cechy z istniejących metadanych + ostrożne tytuły EPG (sport).
4. Profil preferencji z zanikiem i kurczeniem.
5. Kandydaci wyłącznie z katalogu użytkownika.
6. Ranking + różnorodność + cache Home.
7. Tor sportowy: wnioskowanie → potwierdzenie → karta najbliższego meczu.
8. Feedback jawny i „dlaczego”.
9. Eksport / czyszczenie danych personalizacji.
10. Testy jednostkowe, właściwościowe, Room, DST, śmierć procesu.

### 4.2. Poza zakresem (non-goals)

- LLM, chmura, konta „w chmurze Ketchupu”, synchronizacja między urządzeniami w MVP.
- Rekomendowanie treści spoza katalogu dostawcy.
- Scrapowanie nielegalnych strumieni, resolverów, Debrid (i tak poza projektem).
- Obiecana „celność” w procentach, A/B online, deep learning.
- Wymaganie pełnych profili z PIN-em (to 1.1). MVP używa profilu gospodarstwa `household` i opcjonalnego przełącznika incognito.
- Profilowanie wrażliwe (dzieci bez zgody, zdrowie, poglądy, dane z innych aplikacji na urządzeniu).
- Zmiana silnika Media3, palety, wzorców Roborazzi, CI, odtwarzania failover — poza niezbędnym, wąskim *obserwatorem* zdarzeń.
- Masowe odtwarzanie katalogu „żeby nauczyć model”.
- Wymuszanie płatnego źródła terminarza. Opcjonalne licencjonowane API meczów = osobna decyzja właściciela i osobne warunki.

### 4.3. Relacja do istniejących mechanizmów

| Mechanizm | Zostaje | Personalizacja |
| --- | --- | --- |
| Moja lista / ulubione | źródło **jawnego** sygnału | wyższy ciężar niż czas oglądania |
| Kontynuuj oglądanie | rząd osobny | nie zastępować go „dla Ciebie” |
| Radar | ogólne „co ciekawego w EPG” | nie mieszać scoringu Radaru z profilem |
| Zainteresowania (słowa kluczowe) | jawne | można *proponować* słowo/drużynę, nie nadpisywać bez zgody |
| `similar()` | zostaje jako fallback zimnego startu | later można podmienić wywołanie, nie w pierwszym mikro-PR |

---

## 5. Architektura przepływu

```
Media3 (obserwacja, bez zmiany zachowania)
        │  PlaybackEvent (PROJEKT)
        ▼
SessionAggregator          — dedup wznowień, pauza/bufor/seek/surf
        │  WatchSession
        ▼
WatchHistory (Room)        — trwałe, bez URL i sekretów
        │
        ├─────────────┐
        ▼             ▼
FeatureExtractor    SportsInference   (osobny pipeline)
        │             │
        ▼             ▼
PreferenceProfile   FollowedTeam / InferredTeam
        │             │
        ▼             ▼
CandidateGenerator  FixtureMatcher + ChannelAvailability
        │             │
        └──────┬──────┘
               ▼
        Ranker + Diversity (osobne lanes: VOD / SPORT / LIVE_HINT)
               ▼
        HomeSnapshot cache
               ▼
        Home Compose (karty) ──► Feedback (like / hide / follow / not-me)
```

**Inwariant:** UI nigdy nie przelicza profilu na wątku głównym. Home czyta ostatni `HomeSnapshot`. Przeliczenie: WorkManager + inkrementalna aktualizacja po zamknięciu sesji.

### 5.1. Proponowane miejsca w kodzie (PROJEKT)

Nie tworzyć „drugiego frameworka”. Czysta logika JVM (jak `ResumeRules`, `RadarEngine`, `InterestMatcher`), I/O w `core/data`, UI w `feature/home` i `feature/settings`.

Propozycja pakietów:

- `pl.ketchupplayer.tv.core.model.personalization` — typy zdarzeń, sesji, profilu (PROJEKT)
- `pl.ketchupplayer.tv.core.policy.personalization` — progi i formuły czyste (PROJEKT)
- `pl.ketchupplayer.tv.core.database` — nowe encje + migracja Room (np. 7→8 **albo kolejna wolna wersja aktualnego dev**)
- `pl.ketchupplayer.tv.core.data.personalization` — repozytoria, WorkManager, redakcja (PROJEKT)
- `pl.ketchupplayer.tv.player.engine` — tylko cienki most zdarzeń z już istniejącego `AnalyticsListener` / `Player.Listener` (**bez** zmiany bufora, failover, decoderów)
- `pl.ketchupplayer.tv.feature.home` — nowe rzędy za flagą funkcji
- `pl.ketchupplayer.tv.feature.settings` — zgoda, retencja, obserwowane drużyny, wyczyszczenie

Moduł Gradle `:core:personalization` jest opcjonalny. Domyślna propozycja: **nie** dodawać nowego modułu w pierwszym PR; trzymać logikę w `:core:policy` + `:core:data`, analogicznie do Radaru i zainteresowań. Decyzja o module = mikro-PR „szkielet”, nie warunek startu.

### 5.2. Interfejsy Kotlin (PROJEKT)

```kotlin
/** Zdarzenie z odtwarzacza. Żadnych URL, nagłówków, tokenów, User-Agent. */
data class PlaybackEvent(
    val profileId: String,          // "household" albo później id profilu
    val itemRef: CatalogRef,        // stabilne id kanału / vod / odcinka
    val kind: ContentKind,          // LIVE, MOVIE, EPISODE
    val type: PlaybackEventType,
    val positionMs: Long,
    val durationMs: Long?,          // null gdy nieznany (typowy live)
    val wallClockMs: Long,          // zegar urządzenia, epoch UTC
    val playWhenReady: Boolean,
    val playerState: Int,           // wartości Media3 Player.STATE_*
    val discontinuity: DiscontinuityReason?,
    val programmeKey: ProgrammeKey?,// dla LIVE: kanał + start/tytuł EPG jeśli znane
    val source: EventSource,        // USER, AUTOPLAY_PREVIEW, RESUME
)

enum class PlaybackEventType {
    STARTED, STATE, PLAY_WHEN_READY, DISCONTINUITY, ENDED, RELEASED, APP_BACKGROUND
}

data class CatalogRef(
    val providerId: String,
    val itemId: String,
    val contentType: ContentKind,
)

interface PlaybackEventSink {
    fun onEvent(event: PlaybackEvent)
}

interface SessionAggregator {
    fun onEvent(event: PlaybackEvent): SessionDelta
}

data class SessionDelta(
    val upsert: WatchSession?,
    val closed: WatchSession?,
)

interface WatchHistoryRepository {
    suspend fun upsert(session: WatchSession)
    suspend fun close(session: WatchSession)
    fun observeRecent(profileId: String, limit: Int): Flow<List<WatchSession>>
    suspend fun deleteAll(profileId: String)
    suspend fun export(profileId: String): PersonalizationExport
}

interface PreferenceEngine {
    suspend fun applyClosedSession(session: WatchSession)
    suspend fun rebuild(profileId: String) // rzadko; nie z UI
}

interface CandidateGenerator {
    suspend fun vodCandidates(profileId: String, limit: Int): List<RankedCandidate>
    suspend fun sportCandidates(profileId: String, nowMs: Long, limit: Int): List<RankedCandidate>
}

interface HomePersonalizationRepository {
    fun snapshot(profileId: String): Flow<HomeSnapshot>
    suspend fun refresh(profileId: String, reason: RefreshReason)
}
```

---

## 6. Model danych Room (PROJEKT)

Numer migracji poniżej zakłada bazę v7 z checkoutu. Na aktualnym `dev` może być wyższa — implementacja ma użyć **następnej wolnej wersji** i `exportSchema = true`. AutoMigration tam, gdzie tylko nowe tabele/kolumny nullowalne; w razie potrzeby ręczny `Migration`.

### 6.1. Encje

```kotlin
@Entity(
    tableName = "watch_sessions",
    indices = [
        Index("profileId", "itemId"),
        Index("profileId", "startedAtMs"),
        Index("profileId", "status"),
        Index(value = ["profileId", "dedupKey"], unique = true),
    ],
)
data class WatchSessionEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val providerId: String,
    val itemId: String,
    val contentKind: String,          // LIVE / MOVIE / EPISODE
    val seriesId: String?,            // tylko odcinki
    val programmeStartMs: Long?,      // LIVE + EPG
    val programmeTitleNorm: String?,
    val startedAtMs: Long,
    val lastEventAtMs: Long,
    val endedAtMs: Long?,
    val status: String,               // OPEN / CLOSED
    val activeWatchMs: Long,
    val wallWatchMs: Long,            // czas wall-clock z playWhenReady, do diagnostyki
    val pauseMs: Long,
    val bufferMs: Long,
    val seekCount: Int,
    val rebufferCount: Int,
    val maxPositionMs: Long,
    val durationMs: Long?,
    val startReason: String,          // USER / RESUME / AUTOPLAY_PREVIEW
    val endReason: String?,           // USER_STOP / COMPLETED / ERROR / SURF / PROCESS_DEATH / BACKGROUND
    val evidenceClass: String,        // POSITIVE / NEUTRAL / UNKNOWN_FAILURE
    val sourceVersion: Int,           // wersja ekstraktora zdarzeń
    val dedupKey: String,
)

@Entity(
    tableName = "preference_features",
    primaryKeys = ["profileId", "namespace", "featureKey"],
    indices = [Index("profileId", "namespace")],
)
data class PreferenceFeatureEntity(
    val profileId: String,
    val namespace: String,            // GENRE / DIRECTOR / CAST / TEAM / COMPETITION / CHANNEL
    val featureKey: String,           // stabilne id, nie surowy napis UI
    val displayLabel: String,
    val rawWeight: Double,
    val decayedWeight: Double,
    val evidence: Double,             // suma skurczonych wkładów
    val confidence: Double,           // 0..1
    val lastUpdatedMs: Long,
    val provenance: String,           // PROVIDER / TMDB / EPG_TITLE / USER
)

@Entity(
    tableName = "explicit_feedback",
    primaryKeys = ["profileId", "targetType", "targetId", "feedback"],
    indices = [Index("profileId", "updatedAtMs")],
)
data class ExplicitFeedbackEntity(
    val profileId: String,
    val targetType: String,           // ITEM / FEATURE / TEAM
    val targetId: String,
    val feedback: String,             // LIKE / DISLIKE / HIDE / FOLLOW / UNFOLLOW / NOT_ME
    val updatedAtMs: Long,
)

@Entity(
    tableName = "team_catalog",
    indices = [Index("normName"), Index("competitionKey")],
)
data class TeamCatalogEntity(
    @PrimaryKey val teamId: String,   // stabilne id wewnętrzne
    val displayName: String,
    val normName: String,
    val competitionKey: String?,
    val country: String?,
    val source: String,               // BUNDLED / USER / FIXTURE_API
    val sourceVersion: Int,
)

@Entity(
    tableName = "team_aliases",
    primaryKeys = ["teamId", "aliasNorm"],
    foreignKeys = [ForeignKey(
        entity = TeamCatalogEntity::class,
        parentColumns = ["teamId"],
        childColumns = ["teamId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("aliasNorm")],
)
data class TeamAliasEntity(
    val teamId: String,
    val aliasNorm: String,
    val language: String,             // pl / en / ...
    val ambiguousGroup: String?,      // derby / skrót kolidujący
)

@Entity(
    tableName = "team_follows",
    primaryKeys = ["profileId", "teamId"],
)
data class TeamFollowEntity(
    val profileId: String,
    val teamId: String,
    val state: String,                // SUGGESTED / FOLLOWED / REJECTED
    val evidenceCount: Int,
    val lastEvidenceMs: Long,
    val reasonSummary: String,        // krótki, bez sekretów
    val updatedAtMs: Long,
)

@Entity(
    tableName = "sport_events",
    indices = [
        Index("kickoffUtcMs"),
        Index("homeTeamId"),
        Index("awayTeamId"),
        Index(value = ["dedupKey"], unique = true),
    ],
)
data class SportEventEntity(
    @PrimaryKey val eventId: String,
    val competitionKey: String?,
    val homeTeamId: String?,
    val awayTeamId: String?,
    val titleRaw: String,
    val kickoffUtcMs: Long,
    val endUtcMs: Long?,
    val status: String,               // SCHEDULED / LIVE / POSTPONED / CANCELED / FINISHED / RETRACTED
    val isReplay: Boolean,
    val sourceKind: String,           // EPG / FIXTURE_API
    val sourceVersion: Int,
    val scheduleUpdatedAtMs: Long,
    val dedupKey: String,
)

@Entity(
    tableName = "event_availability",
    primaryKeys = ["eventId", "channelId"],
    indices = [Index("eventId"), Index("channelId")],
)
data class EventAvailabilityEntity(
    val eventId: String,
    val channelId: String,            // id z tabeli channels (inwentarz użytkownika)
    val providerId: String,
    val matchMethod: String,          // EPG_ID / NORM_NAME / PROGRAMME_LINK
    val confidence: Double,
    val evidenceAtMs: Long,
    val programmeStartMs: Long?,
    val programmeEndMs: Long?,
    val abstain: Boolean,             // true → UI: „kanał niepotwierdzony”
)

@Entity(tableName = "home_snapshots", primaryKeys = ["profileId", "lane"])
data class HomeSnapshotEntity(
    val profileId: String,
    val lane: String,                 // VOD_BECAUSE / SPORT_UPCOMING / ...
    val payloadJson: String,          // wyłącznie id pozycji i etykiety, bez URL strumieni
    val builtAtMs: Long,
    val profileVersion: Int,
    val catalogVersion: String,
)

@Entity(tableName = "personalization_meta", primaryKeys = ["profileId", "key"])
data class PersonalizationMetaEntity(
    val profileId: String,
    val key: String,
    val value: String,
    val updatedAtMs: Long,
)
```

`payloadJson` snapshotu zawiera wyłącznie: id katalogowe, tytuły już obecne w UI, etykiety wyjaśnień, pewność, flagę `abstain`. **Zakaz:** URL list, query `username`/`password`/`token`, adresy strumieni.

### 6.2. Relacje i retencja

- `watch_sessions.itemId` nie ma twardego FK na `channels`/`vod_items`: odświeżenie listy nie może kasować historii. Przy braku pozycji kandydat wypada z rankingu (zasób nieobecny).
- Historia starsza niż `RETENTION_DAYS` jest usuwana zadaniem okresowym (propozycja: 180 dni; konfigurowalne).
- Wagi profilu są agregatem — po usunięciu historii pozostają do ręcznego „Wyczyść personalizację”, chyba że użytkownik wybierze czyszczenie pełne.
- `team_catalog` może być wbudowany (mały, PL + popularne ligi) i uzupełniany wyłącznie aliasami zatwierdzonymi; nie zaciągać prywatnych nazw z listy użytkownika do katalogu współdzielonego w repo.

### 6.3. Indeksy i skalowanie

Cel: katalog rzędu **dziesiątek tysięcy pozycji** i **setek grup** (zgodnie z już istniejącymi importami), historia rzędu tysięcy sesji, nie milionów.

- Zapis sesji: O(1) upsert po `dedupKey`.
- Aktualizacja profilu po zamknięciu sesji: O(cech sesji), zwykle < 20 (gatunki + reżyser + top N obsady + ewentualnie 2 drużyny).
- Kandydaci VOD: nie skanować całego `vod_items` przy każdym klatkowaniu UI. Strategia:
  1. wziąć top K cech profilu (np. 15),
  2. odpytać pozycje po znormalizowanych gatunkach / znanych kluczach (osobna tabela mostka `vod_feature_index` — PROJEKT, budowana wsadowo po imporcie i po wzbogaceniu metadanych),
  3. ograniczyć zbiór do N (propozycja 400),
  4. rankować w pamięci.
- Snapshot Home: jeden wiersz na pas, odczyt w ViewModel bez joinów analitycznych.

Propozycja tabeli mostka:

```kotlin
@Entity(
    tableName = "vod_feature_index",
    primaryKeys = ["itemId", "namespace", "featureKey"],
    indices = [Index("namespace", "featureKey"), Index("itemId")],
)
data class VodFeatureIndexEntity(
    val itemId: String,
    val namespace: String,
    val featureKey: String,
    val weightOnItem: Double,         // np. 1/sqrt(n) dla obsady
    val provenance: String,
    val confidence: Double,
    val sourceVersion: Int,
)
```

---

## 7. Telemetria z Media3: co jest „oglądaniem”

Źródło prawdy o czasie aktywnym: stany `Player` + `playWhenReady` + nieciągłości pozycji (`AnalyticsListener.onPositionDiscontinuity` / `Player.Listener`), plus już liczone `playedMs`/`stallMs` w `PlaybackStatsCollector`.

Nie wolno:

- używać samego `positionMs` jako czasu oglądania (seek do 80% nie znaczy „obejrzano 80%”),
- używać wzrostu `bufferedPosition`,
- traktować podglądu Na żywo (wyciszony preview z planu 1.0) jako oglądania — oznaczać `source = AUTOPLAY_PREVIEW`,
- zapisywać URL ze `StreamSpec`.

### 7.1. Klasyfikacja czasu

Niech w każdej chwili będzie znane:

- `isReady` = `state == STATE_READY`
- `isPlaying` = `isReady && playWhenReady`
- `isBuffering` = `state == STATE_BUFFERING`
- `isPaused` = `isReady && !playWhenReady`

Definicje przyrostów (zegar ścienny `nowMs`, monotonicznie w ramach procesu):

```
Δwall = nowMs - lastTickMs
Δactive = Δwall   jeśli isPlaying
        = 0       w przeciwnym razie
Δpause  = Δwall   jeśli isPaused
Δbuffer = Δwall   jeśli isBuffering && alreadyStartedPlaying
```

`PlaybackStatsCollector` już nie liczy pierwszego buforowania po starcie i po seeku jako rebuffera — **personalizacja powinna zachować tę samą semantykę** (`bufferMs` diagnostyczne ≠ kara „nie lubię”).

### 7.2. Czego nie wliczamy do preferencji

| Sytuacja | `evidenceClass` | Wpływ na wagi |
| --- | --- | --- |
| Pauza | NEUTRAL | 0 |
| Buforowanie / stall | NEUTRAL | 0 (to nie ocena treści) |
| Seek w przód/tył | NEUTRAL dla przeskoku | liczy się tylko nowy aktywny czas |
| Autoplay / żywy podgląd | NEUTRAL | 0, chyba że użytkownik potwierdzi OK i ogląda ≥ `T_min_preview_promote` |
| Channel surfing (LIVE < `T_surf`) | NEUTRAL | 0 |
| Wyjście < `T_short_exit` (VOD) | NEUTRAL | 0, nie dislike |
| Błąd odtwarzania, failover, brak sieci | UNKNOWN_FAILURE | 0 |
| Ukończenie / długi aktywny czas | POSITIVE | tak |
| Jawny dislike / hide | — | ujemny, poza tą tabelą |

Propozycje progów (do strojenia):

| Symbol | Propozycja | Sens |
| --- | --- | --- |
| `T_surf` | 45 s | przełączanie kanałów |
| `T_short_exit` | 90 s | „nie to” vs przypadek / zły strumień — i tak neutralne bez jawnnego dislike |
| `T_min_movie` | 180 s | minimalny wkład filmu |
| `T_min_episode` | 120 s | minimalny wkład odcinka |
| `T_session_gap` | 30 min | wznowienie tego samego tytułu = ta sama sesja |
| `T_preview_promote` | 20 s po OK | preview przestał być preview |
| `CREDITS_RATIO` | 0.97 | spójnie z `ResumeRules.completed` |
| `EPISODE_CAP_MS` | 45 min | sufit wkładu jednego odcinka |
| `SERIES_CAP_MS_DAY` | 150 min | sufit dzienny jednej serii |
| `CAST_TOP_N` | 5 | ilu aktorów z pozycji wchodzi do profilu |
| `λ_day` | ln(2) / 60 | okres połowicznego zaniku ~60 dni |
| `k_shrink` | 8.0 | siła kurczenia do prioru |
| `w_explicit_like` | 3.0 | mnożnik jawnego like względem implicit 1.0 |
| `w_explicit_follow` | 5.0 | obserwowana drużyna |
| `w_explicit_dislike` | −4.0 | stały ujemny zastrzyk (nie mnożnik czasu) |
| `ε_explore` | 0.08 | udział eksploracji w pasie VOD |
| `MMR_λ` | 0.7 | relevancja vs różnorodność |

### 7.3. Live bez pełnego `duration`

Dla LIVE `durationMs` pozycji zwykle nie istnieje.

- Jeśli jest EPG: `programmeDuration = endMs - startMs` (gdy `endMs > startMs`, w przeciwnym razie nieznany).
- `completion` liczymy tylko gdy `programmeDuration` znany i sesja pokrywa ten program (po `programmeKey`).
- Jeśli EPG brak: istnieje tylko `activeWatchMs`. Wkład jest funkcją czasu, **bez** wskaźnika ukończenia. Nie wolno zakładać „mecz trwa 90 minut”, jeśli EPG tego nie mówi (dogrywka, skrót, studio).

### 7.4. Dedup sesji i śmierć procesu

`dedupKey` (PROJEKT):

```
LIVE:     profileId + channelId + programmeStartMs?.orAlign(startedAtMs, window=2h)
MOVIE:    profileId + itemId + bucketStart
EPISODE:  profileId + itemId + bucketStart
```

`bucketStart` = `startedAtMs` pierwszej otwartej sesji; kolejny start tego samego `itemId` w ciągu `T_session_gap` aktualizuje tę samą sesję (resume), zamiast dodawać drugi wkład.

Po śmierci procesu:

- sesja `OPEN` starsza niż np. 6 h zostaje domknięta jako `PROCESS_DEATH`;
- `activeWatchMs` pozostaje ostatnią zapisaną wartością (checkpoint co N sekund, propozycja 15 s, poza wątkiem UI);
- **zakaz** ponownego doliczenia całego postępu `positionMs` jako nowego oglądania.

Inwariant testowy: dwukrotne `saveProgress` + jedno resume ≠ dwa scoringi.

---

## 8. Od sesji do cech

### 8.1. Siła sesji

Dla sesji zamkniętej:

```
qualifying = evidenceClass == POSITIVE
             AND activeWatchMs >= T_min(kind)
             AND startReason != AUTOPLAY_PREVIEW
```

Gdy `duration*` jest znany i dodatni:

```
completion = clamp(activeWatchMs / duration*, 0, 1)
```

gdy nieznany:

```
completion = null
rawStrength = f_time(activeWatchMs)
```

Propozycja:

```
f_time(ms) = log1p(ms / 60_000)     // log(1 + minuty)
strength = 0                         jeśli !qualifying
         = f_time(activeWatchMs) * (0.5 + 0.5 * completion)
                                     jeśli completion != null
         = f_time(activeWatchMs) * 0.7
                                     jeśli completion == null (live bez EPG)
```

`log1p` zamiast surowych minut tłumi binge i wielogodzinny kanał informacyjny.

Sufity:

```
contribEpisode = min(activeWatchMs, EPISODE_CAP_MS)
contribSeriesDay = min(sum(contribEpisode, that series, that local day), SERIES_CAP_MS_DAY)
```

Do `strength` używamy `contribEpisode` / proporcji po `contribSeriesDay`, nie surowego czasu.

Nie dzielimy przez zero: gdy `duration* <= 0`, traktujemy czas trwania jako nieznany (`completion = null`), nigdy `x / 0`.

### 8.2. Normalizacja cech i prowieniencja

Każda cecha ma:

- `namespace`
- `featureKey` (stabilny)
- `displayLabel`
- `provenance`
- `confidence` źródła metadanych ∈ [0, 1]

Mapowanie (PROJEKT):

| Źródło | Kiedy użyć | Pewność (propozycja) |
| --- | --- | --- |
| Pole dostawcy `genre` / `cast` już w `VodItem` | zawsze gdy niepuste | 0.6 (brudne etykiety, różne języki) |
| TMDB po **znanym id** z `film_index` | id trafione | 0.9 |
| TMDB / OMDb po wyszukiwaniu tytułu+roku | tylko gdy matcher uzna hit za pewny | 0.5–0.75; poniżej progu **nie używać** |
| Tytuł EPG (sport) | parser drużyn | wg punktu 10 |
| Wymyślony gatunek z tytułu filmu | **zakaz** | — |

Reżyser: dziś `TmdbSource` nie zapisuje crew. W zadaniu metadanych (osobny mikro-PR, może wyprzedzać ranking) dodać np. `director` z `credits.crew` gdzie `job == "Director"` **albo** zostawić cechę pustą. **Nie zgadywać reżysera z opisu.**

Normalizacja etykiet gatunku (PROJEKT, słownik w assetach):

- `Akcja`, `Action`, `akcji` → klucz `genre:action`
- nie łączyć „Dramat” z „Melodramat” bez słownika
- wiele gatunków: wkład sesji dzielony:

```
share_g = 1 / sqrt(n_genres)     // n_genres >= 1
```

Obsada nie może zdominować profilu:

```
share_a = 1 / sqrt(n_cast_used)  // n_cast_used = min(n, CAST_TOP_N)
w_cast_global = 0.6              // względny ciężar namespace CAST vs GENRE=1.0, DIRECTOR=1.1
```

Propozycja ciężarów namespace (do strojenia):

```
w_ns(GENRE) = 1.00
w_ns(DIRECTOR) = 1.10
w_ns(CAST) = 0.60
w_ns(TEAM) = 1.20    // tylko tor sport; nie mieszać do rankingu filmu
w_ns(COMPETITION) = 0.70
w_ns(CHANNEL) = 0.40 // ostrożnie: „ogląda ten kanał” ≠ „lubi każdą audycję”
```

### 8.3. Zanik, kurczenie, pewność profilu

Po każdej kwalifikującej sesji, dla każdej cechy `i` z wkładem `x_i = strength * share * w_ns`:

```
ageDays = (nowMs - lastUpdatedMs_i) / 86_400_000
decayed_prev = decayedWeight_i * exp(-λ_day * ageDays)
decayedWeight_i' = decayed_prev + x_i
evidence_i' = evidence_i * exp(-λ_day * ageDays) + 1
```

Kurczenie do prioru `μ` (zwykle 0):

```
score_i = (evidence_i' / (evidence_i' + k_shrink)) * decayedWeight_i'
         + (k_shrink / (evidence_i' + k_shrink)) * μ
```

Pewność cechy:

```
confidence_i = clamp(evidence_i' / (evidence_i' + k_shrink), 0, 1)
               * sourceConfidence
```

Jawny LIKE na filmie dodaje `w_explicit_like * f_time(T_min_movie)` do cech tego filmu (jeden raz na zmianę stanu, nie przy każdym wejściu na szczegóły).  
Jawny DISLIKE ustawia `explicit_feedback` i odejmuje stałą `|w_explicit_dislike|` (nie proporcjonalnie do czasu — użytkownik może odrzucić coś, czego nie oglądał).

### 8.4. Preferencja kontra popularność ekspozycji

Jeśli użytkownik ogląda głównie to, co Home i tak pokazuje na górze, wagi będą echo interfejsu.

Korekta (propozycja, licznik lokalny):

```
exposure_i = ile razy cecha była widoczna w snapshotach Home / siatkach
            (zliczane bez PII, tylko featureKey)
pref_i = score_i / log1p(exposure_i)
```

Albo prostszy wariant MVP: dzielić przez bazową częstość cechy w *katalogu użytkownika*:

```
base_i = share pozycji z cechą i w inwentarzu
pref_i = score_i / max(base_i, ε_base)     // ε_base = 0.01
```

To nie jest debiasing naukowy; to zabezpieczenie przed „wszyscy mają gatunek Dramat w metadanych”. Testy kontrfaktyczne (pkt 16) sprawdzają, że rzadki reżyser oglądany w całości wyprzedza częsty gatunek oglądany przypadkowo.

---

## 9. Generowanie kandydatów i ranking (VOD / seriale)

### 9.1. Zbiór kandydatów

Dozwolony kandydat:

1. istnieje w `vod_items` (lub `channels` dla wskazówek live) aktywnego dostawcy,
2. nie jest ukryty,
3. nie ma `HIDE` / `DISLIKE` na itemie,
4. ma przynajmniej jedną cechę o `provenance` i `confidence` ≥ progu **albo** jest kandydatem ciągłości serialu (następny odcinek — osobna reguła, bez zgadywania gatunku),
5. nie jest duplikatem już pokazanym w innym rzędzie tej samej strony (Kontynuuj / Moja lista wygrywają).

Zakaz: „bo podobny tytuł z internetu, którego nie ma na liście”.

### 9.2. Scoring kandydata

Dla kandydata `c` z cechami `F(c)`:

```
rel(c) = Σ_{i in F(c)} pref_i * weightOnItem(c,i) * confidence_i
```

Dodatki / kary (propozycja):

```
+ cont(c)     // następny odcinek serialu już oglądanego: duża stała, osobny pod-rząd lepiej niż mieszanie
+ mylist_boost jeśli jawne LIKE / My List na pokrewnym tytule tej samej serii
- seen_penalty jeśli completion>=0.97 tego filmu i ustawienie „nie proponuj ponownie” (domyślnie wł. dla filmów)
- spoiler_penalty jeśli sezon/odcinek > najdalej obejrzanego + 1
+ novelty(c)  // mało ekspozycji, niezerowa rel
```

Eksploracja:

```
isExplore = hash(profileId + dayBucket + c.id) % 1000 < 1000 * ε_explore
```

albo przy składaniu listy: (1−ε) najlepszych + ε z puli „pewność niska, cecha obecna, nie oglądane”.

To nie jest Thompson sampling ani sieć neuronowa.

### 9.3. Różnorodność (MMR)

Dla już wybranej listy `S`, kolejny element:

```
mmr(c) = MMR_λ * rel'(c) - (1 - MMR_λ) * max_{s in S} sim(c,s)
```

`sim`: udział wspólnych kluczy gatunku/reżysera (Jaccard).  
Ograniczenia twarde (propozycja): max 2 pozycje tego samego reżysera z rzędu, max 3 z tym samym top-gatunkiem w rzędzie długości 12.

### 9.4. Seriale: ciągłość, spoilery, rewatch

- Rząd „Kontynuuj” zostaje właścicielem *następnego odcinka* (istniejąca logika `vod_state` + `ResumeRules.inProgress`).
- Pas personalizacji nie powinien reklamować S03E01, gdy użytkownik jest na S01E04 — `spoiler_penalty` albo całkowite odfiltrowanie (`SPOILER_POLICY = HIDE`, propozycja domyślna).
- Rewatch serialu: ustawienie „Pozwalaj proponować od początku” (domyślnie wył.).
- Napisy końcowe / intro: nie modelujemy osobno w MVP; korzystamy z aktywnego czasu, nie z faktu dociągnięcia `position` do 97% po seeku na koniec.

### 9.5. Wyjaśnienia

Każda karta VOD ma `why: List<WhyChip>` np. „Gatunek: kryminał”, „Reżyser jak w filmie X”, „Obsada: …”. Chipy tylko z cech o `confidence >= 0.4` i wyłącznie takich, które naprawdę weszły do `rel(c)`. Zakaz zmyślonych uzasadnień.

---


## 10. Tor sportowy (oddzielny od VOD)

Sport **nie** wchodzi do tych samych wag co gatunki filmowe. Oglądanie niedzielnego meczu nie powinno podbijać „rekomendacji filmów sportowych” bez osobnej, świadomej reguły (w MVP: brak takiej reguły).

Pas Home: `SPORT_UPCOMING` — karty wydarzeń, nie plakaty VOD.

### 10.1. Wykrywanie drużyn z EPG

Wejście: `EpgProgramme.title`, opcjonalnie `description` i `category`, po `Channel.normalizeName`.

Propozycja parsera (warstwy, pierwsza pewna wygrywa):

1. Szablony z separatorami: `liga: druzyna a - druzyna b`, `druzyna a vs druzyna b`, `druzyna a – druzyna b`.
2. Słownik aliasów (`team_aliases.aliasNorm`) — najdłuższe dopasowanie tokenów (żeby „legia warszawa” wygrało z gołym „legia”, jeśli obie są w słowniku).
3. Kategorie zawierające `sport`, `pilka`, `mecz`, `liga`, `tenis` itd. (jak trop w `RadarEngine.genre`) — tylko jako filtr „to w ogóle sport?”, nie jako identyfikator drużyny.

Wyjście:

```kotlin
data class TeamParse(
    val teams: List<ResolvedTeam>,     // 0, 1 lub 2
    val competitionKey: String?,
    val pattern: String,
    val ambiguous: Boolean,
    val confidence: Double,
)
```

Zasady:

- **Diakrytyki PL:** porównanie wyłącznie po normalizacji (`ł`/`ń`/`ś` …). Alias „śląsk” i „slask” to ten sam klucz, jeśli tak jest w słowniku.
- **Ambiguous derby / skróty:** jeśli tytuł pasuje do więcej niż jednej pary powyżej progu albo do grupy `ambiguousGroup`, wynik jest `ambiguous = true` i **nie** zwiększa licznika kibicowania żadnej strony. Można pokazać jednorazową kartę „Którą drużynę masz na myśli?” — nie zgadywać.
- **Pojedynczy mecz ≠ kibicowanie jednej ze stron.** Nawet przy pewnym `TeamParse` z dwiema drużynami sesja LIVE dodaje *słaby* dowód obu stronom albo — lepiej w MVP — dowód parze `{A,B}` + osobno lidze, a wniosek „obserwuj A” powstaje dopiero z **asymetrii w czasie**.
- **Powtarzalność:** sugestia FOLLOW wymaga N niezależnych dat (propozycja `N_dates = 3`) w oknie (propozycja 60 dni), nie N przełączeń tego samego popołudnia.
- Daty niezależne: różne `localDate(Europe/Warsaw)` albo różny `programmeStartMs` odległy o ≥ 12 h.

Asymetria (propozycja):

```
evidence(team A) = Σ strength(session) * sideShare(A, session)
sideShare(A) = 0.5                      gdy dwie drużyny i brak innej wskazówki
             = 0                        gdy ambiguous
             = 1                        gdy użytkownik już FOLLOW A (tylko do rankingu kart, nie do samego wnioskowania kołowego)
```

Sugestia, gdy:

```
dates(A) >= N_dates
AND evidence(A) >= α * evidence(B)      // α = 1.8 propozycja, gdy B najczęściej przeciwna
AND nie REJECTED
```

Gdy użytkownik ogląda zawsze tę samą parę (np. zawsze derby), nie sugerować jednostronnie — zaproponować obserwowanie **obu** albo rozgrywek.

Preferencja **drużyna vs rozgrywki**: jeśli ktoś ogląda wiele meczów tej samej ligi z różnymi drużynami, rośnie `COMPETITION`, nie jedna drużyna. Sugestia: „Obserwować Ekstraklasę Przykładową?” osobno od „Obserwować Wisłę Nadmorską?”.

### 10.2. Inferred vs followed

| Stan | Skąd | Home |
| --- | --- | --- |
| `SUGGESTED` | algorytm | karta pytania, nie cichy follow |
| `FOLLOWED` | użytkownik potwierdził albo dodał ręcznie | karty najbliższych meczów |
| `REJECTED` | „nie pokazuj” / „to nie moja drużyna” | milczenie |

Wyjaśnienie edytowalne: „Propozycja, bo oglądałeś transmisje 12.03, 19.03 i 02.04” (daty syntetyczne w testach). Użytkownik może: potwierdzić, odrzucić, poprawić drużynę, dodać alias („to jest ta sama drużyna co …”).

### 10.3. Terminarz vs EPG vs dostępność u dostawcy

Trzy warstwy, których nie wolno spłaszczać:

1. **Wydarzenie sportowe** (kto vs kto, kickoff UTC, status).
2. **Planowana emisja** w EPG (tytuł na kanale w przedziale czasu).
3. **Dostępność w katalogu użytkownika** (ten kanał istnieje u dostawcy *teraz*).

Źródła wydarzeń:

- **MVP:** wyłącznie programy EPG, które parser uznał za mecze obserwowanej/sugerowanej drużyny.
- **Później, opcjonalnie:** licencjonowane API terminarza. Wymaga osobnej decyzji właściciela, akceptacji regulaminu, atrybucji i oceny pokrycia. **Nie podajemy tu cen ani licencji** — trzeba je sprawdzić w momencie decyzji. Brak zgody = pozostajemy przy EPG.

Ograniczenia EPG:

- okno przyszłości bywa krótkie albo puste po imporcie,
- powtórki i skróty wyglądają jak mecze,
- ten sam mecz na wielu kanałach,
- zmiana godziny / odwołanie / opóźnienie,
- kanał zmienia `tvg-id` albo nazwę po odświeżeniu listy.

### 10.4. Premiera na żywo vs powtórka

Heurystyki (propozycja, do testów właściwościowych):

- słowa w tytule/opisie po normalizacji: `powtorka`, `skrot`, `skroty`, `replay`, `studio`, `magazyn`, `skrocony` → `isReplay = true`;
- emisja > `T_replay_after` po `kickoff` znanym z innego źródła → powtórka;
- gdy kickoff znany tylko z tej audycji: audycja *jest* wydarzeniem (EPG-native), `isReplay` nieznany = `false` tylko gdy kategoria/tytuł wskazują transmisję; w razie wątpliwości etykieta „audycja EPG”, nie „mecz na żywo”.

Karty Home preferują najbliższe `isReplay == false` ze statusem `SCHEDULED`/`LIVE`. Powtórkę można pokazać niżej z etykietą „powtórka”.

### 10.5. Dopasowanie kanału

Kolejność metod:

1. `programme.channelId` już zmapowany do `channels.id` przez istniejący import EPG — najwyższa pewność (0.95), jeśli tytuł parsera zgadza się z wydarzeniem.
2. `Channel.epgId` (tvg-id) zgodny ze źródłem wydarzenia.
3. `normName` kanału — tylko przy unikalnym trafieniu; kolizja → abstain.

```
if (confidence < T_ch) {  // propozycja T_ch = 0.75
    abstain = true
    // UI: „kanał niepotwierdzony”
    // zakaz wskazywania „pewnie to Canal+” albo jakiegokolwiek kanału spoza listy
}
```

Licencjonowany nadawca z API terminarza (gdyby kiedyś był) służy wyłącznie jako *wskazówka wyszukiwania* w katalogu użytkownika. Jeśli użytkownik nie ma tego kanału: abstain, nie „wymyśl stream”.

### 10.6. Czas: zapis UTC, render Europe/Warsaw, DST

- W bazie: `kickoffUtcMs`, `endUtcMs`, `programmeStartMs` jako epoch UTC (spójnie z `EpgProgramme`).
- W UI: `Europe/Warsaw` z uwzględnieniem DST (`ZoneId.of("Europe/Warsaw")`).
- Testy z datami syntetycznymi: noc z 28/29 marca 2026 i 25/26 października 2026 (zmiana czasu w UE w 2026 — **ZAŁOŻENIE do potwierdzenia w teście strefy**, nie twierdzenie produktowe). Inwariant: kickoff 14:00 UTC nie „przesuwa się o godzinę” w renderze poza rzeczywistą zmianą offsetu.

### 10.7. Inwalidacja, dedup, retrakcja

- Nowe EPG o tym samym `dedupKey` (propozycja: `homeTeam + awayTeam + localDate(Warsaw)` albo znormalizowany tytuł + dzień, gdy drużyny niepewne) aktualizuje godzinę; stary slot nie zostaje jako drugi mecz.
- Status `POSTPONED` / `CANCELED` / `RETRACTED`: karta znika lub pokazuje „termin zmieniony / odwołany” na podstawie najnowszego `scheduleUpdatedAtMs`.
- Ten sam mecz na N kanałach: jedna karta, lista dostępnych kanałów posortowana po `confidence`, z abstain gdy żadna pozycja nie przekracza progu.
- Dowód dostępności ma `evidenceAtMs`. UI może dodać „wg EPG z godz. …”. Po odświeżeniu listy kanałów: przeliczyć dostępność; nie trzymać `channelId`, którego już nie ma.

### 10.8. Czego sport nigdy nie robi

- Nie scrapuje nielegalnych transmisji.
- Nie zapisuje ani nie loguje prywatnych URL-i listy użytkownika.
- Nie używa prawdziwych danych dostępu (nawet jeśli użytkownik wkleił je w innym wątku do testów IPTV).
- Nie twierdzi „mecz o 18:00 na kanale X” bez dowodu z EPG/inwentarza.

---

## 11. Home, zimny start, wielu użytkowników

### 11.1. Pasy na Home (PROJEKT)

Istniejące rzędy zostają. Nowe, za flagą i zgodą:

| Pas | Treść | Priorytet miejsca |
| --- | --- | --- |
| `SPORT_UPCOMING` | 1–3 karty najbliższych meczów obserwowanych drużyn | wysoko, gdy wydarzenie < 48 h |
| `SPORT_SUGGEST_FOLLOW` | najwyżej 1 karta pytania | nie częściej niż raz na kilka dni |
| `VOD_BECAUSE` | 8–12 pozycji z wyjaśnieniami | pod Kontynuuj / Moja lista |
| (opcjonalnie później) `LIVE_BECAUSE` | „teraz na antenie” wg zainteresowań — to bliżej Radaru/Zainteresowań; nie dublować w MVP | — |

Fokus D-pad: pierwsze ognisko Home **nie** może zostać zepsute nowym rzędem. Nowy rząd uczestniczy w audycie układu (`LayoutAudit`) i testach pilota jak pozostałe rzędy Home.

### 11.2. Cache snapshotu

`HomeViewModel` subskrybuje `HomeSnapshot` + istniejące flow (`continueWatching`, `myList`, …).

Odświeżenie snapshotu:

- po zamknięciu sesji (debounce, propozycja 5 s),
- po zmianie FOLLOW / LIKE / HIDE,
- po imporcie EPG / katalogu,
- okresowo (WorkManager, np. co 3 h oraz przy `timezone`/północy Warsaw — żeby karty „dziś” nie zostawały na wczoraj).

Jeśli przeliczenie się nie udało: pokaż ostatni snapshot z `builtAtMs` albo ukryj pas (nie pokazuj pustych „magicznych” szkieletów jako sukcesu).

### 11.3. Zimny start

Brak sesji kwalifikujących:

- nie pokazywać „dla Ciebie” (lepsze nic niż losowa „inteligencja”),
- można pokazać istniejące `similar()` dopiero po pierwszym ukończonym filmie — albo wcale w MVP,
- sport: zero sugestii do `N_dates`.

Minimum do pasu VOD (propozycja): 2 kwalifikujące sesje film/serial **lub** 1 jawny LIKE.

### 11.4. Gospodarstwo, profile, incognito, dzieci

MVP:

- `profileId = "household"` (stała).
- Przełącznik „Nie ucz się z tej sesji / Incognito”: zdarzenia dostają `evidenceClass = NEUTRAL` i nie zamykają się jako POSITIVE. Nie kasuje starego profilu.
- Nie czytamy innych aplikacji, kontaktów, konta Google, mikrofonu.

Gdy powstanie `:feature:profiles` (plan 1.1):

- historia i wagi dostają prawdziwy `profileId`,
- migracja: dotychczasowy `household` zostaje przypisany do pierwszego/domyślnego profilu albo pytamy użytkownika (**decyzja później, nie blokuje MVP**),
- profil dziecięcy: personalizacja albo wyłączona, albo twardo ograniczona do treści już dopuszczonych polityką profilu (której jeszcze nie ma — nie projektować tu cenzury ad hoc).
- Zakaz wrażliwego profilowania (nie wnioskujemy wieku, płci, pochodzenia z obsady i gatunków).

Współdzielony TV: wnioski sportowe mają być łatwe do odrzucenia („to nie moja drużyna”), bo oglądać może kilka osób.

---

## 12. Prywatność i bezpieczeństwo

### 12.1. Zgoda

Ustawienie: **„Personalizacja na tym urządzeniu”**

- Domyślna propozycja: **wyłączona**, włączana świadomie (opt-in). Alternatywa: włączona po krótkim wyjaśnieniu przy pierwszym Home — **decyzja właściciela, pkt 19**. Implementacja ma łatwo obsłużyć oba warianty flagą.
- Wyłączenie: natychmiast przestaje zapisywać nowe wkłady; istniejące dane zostają do czasu „Wyczyść”.
- Osobno: okres retencji, eksport, czyszczenie.

### 12.2. Czego telemetria nie zawiera

Zakaz w `PlaybackEvent`, `watch_sessions`, snapshotach, logach, raportach testowych:

- URL listy M3U / Xtream, query `username`, `password`, `token`, `mac`,
- `StreamSpec.url` i nagłówki,
- sekrety `SecretStore`,
- surowe dumpy UI formularza dostawcy.

Znana luka Redactora (zgłaszana przy innych pracach) oznacza: **nie logować** nawet „na chwilę” adresów strumieni w ścieżce personalizacji. Testy redakcji wyłącznie na **fikcyjnych** sekretach.

### 12.3. Eksport i usuwanie

Eksport (JSON): sesje bez sekretów, wagi cech, follows, feedback. Plik lokalny, użytkownik decyduje gdzie go podać.

Czyszczenie: usuwa tabele z pkt 6 dla danego `profileId` i unieważnia snapshot. Nie rusza katalogu, Mojej listy ani postępu `vod_state` (to nie są dane „uczenia” — chyba że użytkownik wybierze szersze „reset aplikacji”, które już należy do innego flow).

### 12.4. Inne aplikacje i wrażliwe wnioski

Nie czytamy historii Chrome, YouTube, innych odtwarzaczy.  
Nie budujemy profilu politycznego, religijnego ani zdrowotnego z kategorii EPG. Słowa kluczowe użytkownika w Zainteresowaniach zostają *jego* listą, nie cechą ukrytą.

---

## 13. Wydajność: RAM, CPU, baza

Ograniczenia (propozycje, do pomiaru na boxie — nie obiecywać FPS):

| Zasób | Limit roboczy |
| --- | --- |
| Sesje w DB | twardy sufit np. 5 000 najnowszych + retencja czasowa |
| Cechy profilu | top 200 per namespace po zaniku; reszta prune |
| Przeliczenie VOD | max 400 kandydatów, < 150 ms na tle na typowym boxie (cel, nie gwarancja) |
| Indeks cech | wsadowo po imporcie / wzbogaceniu, nie przy scrollu |
| EPG sport parser | po imporcie EPG, nie per klatka Radaru |
| Pamięć snapshotu | pojedyncze kilobajty JSON |

UI: zero pełnego skanu `vod_items` na głównej.  
WorkManager: `ExistingWorkPolicy.REPLACE` dla unikalnej pracy `personalization-refresh`, constraints: nie przy low storage; sieć **nie** jest wymagana do przeliczenia profilu (local-first). Sieć tylko gdy użytkownik i tak wzbogaca metadane własnymi kluczami — to istniejący `MetadataService`, nie nowy crawler.

Aktualizacja inkrementalna: zamknięta sesja = dodać `x_i` do cech sesji, nie przebudowywać wszystkiego. `rebuild()` tylko po zmianie λ, migracji schematu albo „napraw profil”.

---

## 14. WorkManager i cykl życia (PROJEKT)

| Praca | Wyzwalacz | Zadanie |
| --- | --- | --- |
| `session-checkpoint` | co 15 s w trakcie OPEN (może być coroutine w procesie, nie WM) | zapis `activeWatchMs` |
| `session-close` | END / background / death | klasa evidence + `PreferenceEngine.applyClosedSession` |
| `personalization-refresh` | debounce po close / follow / EPG | snapshot Home |
| `personalization-daily` | 03:30 Europe/Warsaw (przybliżenie WM) | zanik, prune, retencja, odświeżenie kart „dziś” |
| `sport-reparse` | po zapisie `epg_programmes` | parser + availability |

Po `onStop` odtwarzacza: domknij sesję albo oznacz `BACKGROUND` i pozwól checkpointowi żyć krótko, jeśli playback w tle jest dozwolony istniejącą polityką pauzy. Nie sprzeczaj się z regułą „pauza po wyjściu z aplikacji” — jeśli player pauzuje, `playWhenReady=false` sam przestaje doliczać `activeWatchMs`.

---

## 15. Metadane i pochodzenie

Każda cecha i każde wydarzenie ma `provenance` + `sourceVersion`. Zmiana słownika aliasów albo parsera podbija wersję; stare wnioski niskiej pewności można przeliczyć.

Hierarchia zaufania VOD:

1. Id z wbudowanego `film_index.db` (Wikidata CC0) + TMDB po id.
2. Pola dostawcy, gdy niepuste.
3. Wyszukiwanie po tytule+roku tylko powyżej progu (np. dokładny `normTitle` i zgodny rok). Poniżej: **brak cechy**.

Tytułów nie „poprawiamy” na znany film, gdy pewność jest niska — lepiej pusty profil niż zły reżyser.

Opcjonalne źródła (już w kodzie, klucze użytkownika): TMDB, OMDb, MDBList, Trakt, Wikipedia. Warunki licencyjne i ewentualne opłaty **weryfikować w dniu wdrożenia** na stronach dostawców API; ten dokument ich nie cytuje jako faktów cenowych.

Opcjonalne źródło terminarza sportowego: tylko legalne, licencjonowane, po decyzji. Coverage bywa niekompletne — UI musi znosić brak przyszłych meczów („brak informacji o następnym spotkaniu”).

Oficjalne dokumenty implementacyjne (do czytania przy kodowaniu, nie przy tym konspekcie):

- Media3 `Player.Listener` / `AnalyticsListener` (stany, `playWhenReady`, nieciągłości),
- Room entities, indeksy, migracje, `exportSchema`,
- WorkManager unique/periodic work,
- Compose for TV — fokus w wierszach Home.

---

## 16. Testy

Zasada projektu: brak dowodu ≠ PASS. Niedostępne środowisko = `NOT_RUN` / `BLOCKED`, nigdy sukces.

### 16.1. Jednostkowe (czysta JVM)

- klasyfikacja czasu: play / pause / buffer / seek / preview / surf,
- `completion` nie liczona przy `durationMs <= 0`,
- dedup resume w `T_session_gap`,
- sufity odcinka i serialu,
- zanik i kurczenie (wartości sprecyzowane w teście),
- parser drużyn: diakrytyki, `vs`/`-`, ambiguous derby,
- pojedynczy mecz nie tworzy FOLLOW,
- trzy niezależne daty + asymetria → SUGGESTED,
- abstain kanału przy niskiej pewności,
- inwalidacja POSTPONED i dedup multi-kanał,
- render czasu Warsaw vs UTC,
- redakcja fikcyjnych sekretów w ścieżkach URL.

### 16.2. Właściwościowe / kontrfaktyczne (offline)

Fixture syntetyczne, **bez prywatnej playlisty**:

1. Dwa ukończone filmy reżysera R i jeden krótki exit filmu gatunku popularnego G → R wyżej w `pref` niż G.
2. Binge 8 odcinków serialu jednego dnia nie przeważa profilu obsady nad dwoma świadomie dokończonymi filmami innego reżysera (dzięki capom).
3. Seek na napisy końcowe bez aktywnego czasu ≠ ukończenie.
4. Ta sama sesja po śmierci procesu nie dubluje wagi.
5. Kandydat spoza inwentarza nigdy nie wchodzi na listę, nawet przy idealnym dopasowaniu cech.
6. Wyłączenie zgody → brak wzrostu wag mimo zdarzeń.

Nie obiecujemy mierzalnej „accuracy@K” na prawdziwych użytkownikach. Kontrfakty to testy regresji logiki, nie paper ML.

### 16.3. Integracja Room / Media3

- zapis i odczyt sesji, migracja schematu,
- proces death: otwarta sesja → checkpoint → close,
- fake `Player` emitujący stany (bez sieci) składa oczekiwaną sesję,
- po podmianie EPG karta meczu znika / zmienia godzinę.

### 16.4. UI / D-pad / dostępność

- nowy rząd Home: nawigacja LEWO/PRAWO/GÓRA/DÓŁ, OK, BACK,
- karta „kanał niepotwierdzony” nie jest martwym fokusem,
- kontrast i `LayoutAudit` jak pozostałe rzędy,
- ręczny test na TV-boxie po RC — nie deklarować go na podstawie emulatora Cloud.

### 16.5. Czas i DST

Deterministyczny `Clock` (już jest `pl.ketchupplayer.tv.core.common.Clock`). Testy z zamrożonym `nowMs` i ze strefą `Europe/Warsaw`.

---

## 17. Scenariusze end-to-end (wyraźnie syntetyczne)

Dane poniżej są wymyślone. Nie opisują prawdziwych kolejek ligowych ani kanałów użytkownika.

### S1. Film — nauka gatunku i reżysera

1. Katalog testowy zawiera filmy A, B (ten sam reżyser „Anna Testowa”, gatunek Kryminał) oraz C (Komedia, inny reżyser).
2. Użytkownik ogląda A przez 95 min z 100 min (`playWhenReady=true`, bez seeku na koniec). Sesja POSITIVE.
3. Ogląda B analogicznie.
4. Krótko wchodzi w C (40 s) i wychodzi — NEUTRAL.
5. Po odświeżeniu snapshotu pas `VOD_BECAUSE` zawiera inne kryminały z inwentarza, chip „Reżyser: Anna Testowa” jeśli taki kandydat istnieje; C nie jest top-1 tylko z powodu krótkiego wejścia.

### S2. Serial — cap i spoiler

1. Serial „Przykład z Portu”, odcinki S01E01–S01E08 w katalogu, S02E01 też jest.
2. Użytkownik binge’uje E01–E06 jednego dnia.
3. Wkład serii tego dnia nie przekracza `SERIES_CAP_MS_DAY`.
4. `VOD_BECAUSE` nie promuje S02E01 (`SPOILER_POLICY = HIDE`).
5. „Kontynuuj” nadal pokazuje E07 zgodnie z `vod_state`.

### S3. Sport — powtarzalność i kanał

1. EPG syntetyczne: trzy soboty, tytuły „Liga Nadmorska: Wisła Nadmorska — Hutnik Leśny” na kanale `ch_sport_1` (epgId `sport1.example`).
2. Użytkownik ogląda każdą transmisję > 20 min.
3. Po dwóch datach: brak cichego FOLLOW.
4. Po trzeciej: stan `SUGGESTED` dla drużyny, której dotyczy asymetria; jeśli obie strony równe — sugestia pary/ligi, nie zgadywanie kibica.
5. Użytkownik potwierdza „Wisła Nadmorska”.
6. Czwarte wydarzenie w EPG za 5 dni na tym samym `epgId` → karta z godziną w Europe/Warsaw i nazwą kanału z katalogu.
7. Jeśli czwarte wydarzenie jest tylko w abstrakcyjnym terminarzu bez wiersza EPG na kanale użytkownika → „kanał niepotwierdzony”.

### S4. Derby niejednoznaczne

Tytuł „Derby Trójmiasta” bez nazw drużyn, alias w grupie ambiguous → zero przyrostu `FOLLOW` żadnej strony.

### S5. Incognito i awaria

Włączone „Nie ucz się z tej sesji” albo `ERROR` po 3 s buforowania → wagi bez zmian.

### S6. Wielokanałowy dedup

Ten sam znormalizowany tytuł i kickoff na `ch_sport_1` oraz `ch_sport_2` → jedna karta, dwa kanały jeśli oba ≥ `T_ch`.

---

## 18. Ryzyka i bramki akceptacji

| ID | Ryzyko | Wpływ | Mitygacja | Bramka |
| --- | --- | --- | --- | --- |
| R1 | Zły reżyser/obsada z matcha tytułu | fałszywy profil | próg pewności, id z indeksu, abstain | test: niski score match nie tworzy cechy |
| R2 | Surfing i preview uczą kanałów | śmieciowe wagi | `T_surf`, flaga preview | test surf < 45 s = 0 |
| R3 | Seek na koniec = „obejrzane” | fałszywe ukończenie | tylko `activeWatchMs` | test seek 97% bez play = 0 |
| R4 | Jeden mecz → „ulubiona drużyna” | zły UX na wspólnym TV | N dat + SUGGESTED | test jednej daty |
| R5 | Wymyślony kanał emisji | utrata zaufania | abstain, tylko inwentarz | test braku kanału |
| R6 | Sekrety w logach / JSON | incydent | schemat zdarzeń bez URL, testy redakcji | code review + test fikcyjnych sekretów |
| R7 | Koszt CPU na wielkim katalogu | zacinki Home | indeks + snapshot + limit kandydatów | pomiar / test czasu na fixture 30k (syntetyczne) |
| R8 | DST psuje godzinę meczu | zła karta | UTC store, testy strefy | test DST |
| R9 | Mieszanie z PR jakości 1.0 | opóźnienie RC | osobna gałąź/PR po decyzji | nie startować przed OK właściciela |
| R10 | Traktowanie wag jako ML | fałszywe oczekiwania | ten dokument, metryki z punktu 21 | review tekstów UI: bez słowa „AI” |
| R11 | Źródło terminarza / scraping | prawo, ToS | EPG-only w MVP, brak scrapingu streamów | ADR przed jakimkolwiek API |
| R12 | Gospodarstwo kilku osób | profil-mieszanka | łatwy REJECT, późniejsze profile | karta „to nie moja drużyna” |

### Etapy i bramki

**Etap A — fundament (historia + zgoda + schemat)**  
DoD: migracja Room, opt-in, zapis sesji bez sekretów, testy dedup i klas evidence, zero zmian UI Home poza ukrytą flagą deweloperską.

**Etap B — VOD**  
DoD: profil cech, indeks, ranking, pas `VOD_BECAUSE` za zgodą, wyjaśnienia, testy kontrfaktyczne, D-pad.

**Etap C — sport z potwierdzeniem**  
DoD: parser, stany SUGGESTED/FOLLOWED/REJECTED, karta meczu, abstain, DST, dedup, inwalidacja.

**Etap D — wzmocnienia**  
Eksploracja MMR dopracowana, ekspozycja vs preferencja, ewentualne API terminarza (osobna decyzja), migracja do prawdziwych profili 1.1, ewentualny większy model.

---

## 19. Pytania i decyzje (nie blokują projektu dokumentu)

Domyślne propozycje w nawiasie — właściciel może zmienić później:

1. Czy personalizacja jest opt-in czy opt-out po krótkim wyjaśnieniu? **(opt-in)**
2. Czy pas VOD ma się nazywać „Dla Ciebie”, „Bo oglądasz”, czy inaczej? **(„Bo oglądasz” — uczciwsze)**
3. Czy jednym kliknięciem można obserwować ligę obok drużyny? **(tak, osobno)**
4. Czy odrzucenie sugestii drużyny milczy na zawsze, czy pyta ponownie po 90 dniach? **(ponownie po 90 dniach przy nowych dowodach)**
5. Czy eksport ma być w Ustawieniach od razu w etapie A? **(tak, tanie i buduje zaufanie)**
6. Czy reżyser z TMDB crew wchodzi w etapie B, czy czekamy na osobny PR metadanych? **(osobny mały PR metadanych tuż przed B)**
7. Domyślny `profileId` po wprowadzeniu PIN-ów: przenieść `household` automatycznie? **(zapytać użytkownika przy tworzeniu drugiego profilu)**

Żadna z tych decyzji nie jest potrzebna, żeby zacząć etap A po pierwszym RC.

---

## 20. Backlog mikro-PR (propozycja kolejności)

Każdy punkt = osobny PR do `dev`, bez merge’a bez właściciela, bez mieszania z PR #11.

| ID | Tytuł | Zależności | DoD skrót |
| --- | --- | --- | --- |
| P0 | Start po pierwszym RC + odczyt aktualnego `dev` | — | potwierdzona wersja bazy, brak kolizji schematu |
| P1 | Flaga DataStore: zgoda, retencja, incognito | P0 | testy ustawień, domyślnie off |
| P2 | Encje Room + migracja + DAO | P1 | `exportSchema`, test migracji |
| P3 | Most zdarzeń Media3 → `PlaybackEvent` (bez URL) | P2 | unit na fake player; silnik odtwarzania bez zmian zachowania |
| P4 | `SessionAggregator` + persist + checkpoint + process death | P3 | testy dedup/resume/surf/pause |
| P5 | Redakcja i zakaz sekretów w tej ścieżce | P4 | testy fikcyjnych sekretów |
| P6 | Ekstrakcja cech VOD + słownik gatunków + director z TMDB (wąsko) | P2 | brak wymyślonych cech; pewność |
| P7 | `vod_feature_index` wsadowo | P6 | test na syntetycznych 30k (jeśli CI uciągnie pamięć — inaczej 5k + NOT_RUN na 30k) |
| P8 | `PreferenceEngine` inkrementalny (formuły) | P4, P6 | testy zaniku, capów, shrink |
| P9 | `CandidateGenerator` + Ranker + MMR | P7, P8 | inwentarz-only, testy kontrfaktyczne |
| P10 | Snapshot Home + pas `VOD_BECAUSE` + why + D-pad | P9, P1 | LayoutAudit, Roborazzi, E2E syntetyczne |
| P11 | Feedback LIKE/HIDE na karcie | P10 | explicit > implicit |
| P12 | Katalog drużyn+aliasów (wbudowany, mały, PL) | P2 | testy normalizacji |
| P13 | Parser EPG + inferred/suggested | P12, P4 | N dat, derby, brak cichego follow |
| P14 | Wydarzenia + availability + abstain + DST | P13 | testy czasu i dedup |
| P15 | Pas `SPORT_UPCOMING` + potwierdzenie | P14, P10 | instrukcja pilota |
| P16 | WorkManager daily prune/refresh | P8, P14 | nie na main thread |
| P17 | Eksport / clear UI | P2 | nie rusza katalogu |
| P18 | Dokumentacja w HANDOFF tej funkcji + metryki diagnostyczne (liczba sesji, cech; bez PII) | P10, P15 | — |

P1–P5 można przygotowywać dopiero po decyzji właściciela; do tego czasu ten plik jest wyłącznie konspektem.

---

## 21. Jak mierzyć, czy „uczy się” (bez obietnic celności)

Lokalne, diagnostyczne, widoczne ewentualnie w ukrytym panelu debug:

- liczba sesji POSITIVE / NEUTRAL / UNKNOWN_FAILURE,
- odsetek sesji odrzuconych jako surf/preview,
- liczba cech z `confidence > 0.5`,
- odsetek kart `VOD_BECAUSE` klikniętych vs ukrytych (lokalnie),
- odsetek sugestii drużyn potwierdzonych vs odrzuconych,
- odsetek kart sportowych z abstain.

To są wskaźniki *zdrowia systemu*, nie reklamowa „accuracy”.

**Kiedy większy model ma sens:** gdy (a) powyższe pokazują, że użytkownicy potwierdzają sugestie i nie ukrywają masowo kart, (b) mamy dużo lokalnych sesji i czyste metadane, (c) proste wagi systematycznie przegrywają w ślepych testach offline na *własnych*, wyeksportowanych, zanonimizowanych logach właściciela, (d) większy model nadal działa w pełni offline i mieści się w budżecie boxa. Do tego czasu boosting / sieć / chmura są poza zakresem.

---

## 22. Pseudokod referencyjny (do implementacji)

Inwarianty obowiązujące w kodzie:

- nigdy `x / 0` ani `log(0)` bez ochrony,
- wszystkie czasy ≥ 0,
- wagi skończone (odrzuć NaN/Infinity),
- brak URL w obiektach persystowanych,
- `qualifying` wymaga zgody włączonej.

### 22.1. Agregacja zdarzeń

```kotlin
class DefaultSessionAggregator(
    private val clock: Clock,
    private val p: PersonalizationParams, // wszystkie progi z §7.2
) : SessionAggregator {

    private val open = mutableMapOf<String, WatchSession>() // key = dedupKey

    override fun onEvent(e: PlaybackEvent): SessionDelta {
        require(e.wallClockMs >= 0)
        if (e.source == EventSource.AUTOPLAY_PREVIEW && e.type != PlaybackEventType.ENDED) {
            // preview żyje osobno; awans dopiero po USER start
        }
        val key = dedupKey(e)
        val current = open[key]
        val session = current?.let { accumulate(it, e) } ?: openNew(e, key)
        open[key] = session
        val shouldClose = e.type == PlaybackEventType.ENDED ||
            e.type == PlaybackEventType.RELEASED ||
            (e.type == PlaybackEventType.APP_BACKGROUND && !staysInBackground(e))
        return if (shouldClose) {
            open.remove(key)
            SessionDelta(upsert = null, closed = classify(session.copy(endedAtMs = e.wallClockMs)))
        } else {
            SessionDelta(upsert = session, closed = null)
        }
    }

    private fun accumulate(s: WatchSession, e: PlaybackEvent): WatchSession {
        val dt = (e.wallClockMs - s.lastEventAtMs).coerceAtLeast(0L)
        val active = if (e.playerState.isReady() && e.playWhenReady) dt else 0L
        val pause = if (e.playerState.isReady() && !e.playWhenReady) dt else 0L
        val buffer = if (e.playerState.isBuffering() && s.activeWatchMs > 0) dt else 0L
        val seeks = s.seekCount + if (e.discontinuity.isSeek()) 1 else 0
        return s.copy(
            lastEventAtMs = e.wallClockMs,
            activeWatchMs = s.activeWatchMs + active,
            pauseMs = s.pauseMs + pause,
            bufferMs = s.bufferMs + buffer,
            seekCount = seeks,
            maxPositionMs = maxOf(s.maxPositionMs, e.positionMs.coerceAtLeast(0L)),
            durationMs = e.durationMs?.takeIf { it > 0 } ?: s.durationMs,
        )
    }

    private fun classify(s: WatchSession): WatchSession {
        val end = when {
            s.startReason == "AUTOPLAY_PREVIEW" && s.activeWatchMs < p.tPreviewPromote -> "PREVIEW"
            s.contentKind == "LIVE" && s.activeWatchMs < p.tSurf -> "SURF"
            s.endReason == "ERROR" -> "ERROR"
            else -> s.endReason ?: "USER_STOP"
        }
        val evidence = when {
            end == "ERROR" -> "UNKNOWN_FAILURE"
            end == "SURF" || end == "PREVIEW" -> "NEUTRAL"
            s.contentKind != "LIVE" && s.activeWatchMs < p.tShortExit -> "NEUTRAL"
            s.activeWatchMs <= 0L -> "NEUTRAL"
            else -> "POSITIVE"
        }
        return s.copy(endReason = end, evidenceClass = evidence, status = "CLOSED")
    }

    private fun dedupKey(e: PlaybackEvent): String {
        val livePart = e.programmeKey?.startMs?.toString() ?: "nolive"
        return listOf(e.profileId, e.itemRef.itemId, livePart).joinToString("|")
        // resume: wywołujący najpierw szuka OPEN o tym itemId z lastEvent w T_session_gap
    }
}
```

### 22.2. Siła sesji i update wag

```kotlin
fun sessionStrength(s: WatchSession, p: PersonalizationParams, seriesMsToday: Long): Double {
    if (s.evidenceClass != "POSITIVE") return 0.0
    val minT = when (s.contentKind) {
        "MOVIE" -> p.tMinMovie
        "EPISODE" -> p.tMinEpisode
        else -> p.tSurf // live kwalifikuje się powyżej surf
    }
    var ms = s.activeWatchMs.coerceAtLeast(0L)
    if (s.contentKind == "EPISODE") ms = minOf(ms, p.episodeCapMs)
    if (s.contentKind == "EPISODE") {
        val room = (p.seriesCapMsDay - seriesMsToday).coerceAtLeast(0L)
        ms = minOf(ms, room)
    }
    if (ms < minT) return 0.0
    val minutes = ms / 60_000.0
    val timePart = ln1p(minutes) // ln(1 + minutes), nigdy ln(0)
    val dur = s.durationMs
    val completion = if (dur != null && dur > 0L) {
        (ms.toDouble() / dur.toDouble()).coerceIn(0.0, 1.0)
    } else null
    val strength = if (completion == null) timePart * 0.7 else timePart * (0.5 + 0.5 * completion)
    return strength.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
}

fun decayedUpdate(
    prev: PreferenceFeatureEntity,
    x: Double,
    nowMs: Long,
    p: PersonalizationParams,
): PreferenceFeatureEntity {
    val ageDays = ((nowMs - prev.lastUpdatedMs).coerceAtLeast(0L)) / 86_400_000.0
    val decay = exp(-p.lambdaDay * ageDays)
    val decW = prev.decayedWeight * decay + x
    val ev = prev.evidence * decay + 1.0
    val denom = ev + p.kShrink
    val score = if (denom == 0.0) 0.0 else (ev / denom) * decW // denom >= kShrink > 0
    val conf = (ev / denom).coerceIn(0.0, 1.0)
    return prev.copy(
        rawWeight = prev.rawWeight + x,
        decayedWeight = decW,
        evidence = ev,
        confidence = conf,
        lastUpdatedMs = nowMs,
    )
}
```

### 22.3. Ranking i filtr inwentarza

```kotlin
fun rankVod(
    inventory: Set<String>,
    candidates: List<VodCandidate>,
    pref: Map<FeatureKey, Double>,
    hidden: Set<String>,
    p: PersonalizationParams,
): List<VodCandidate> {
    val scored = candidates.mapNotNull { c ->
        if (c.itemId !in inventory) return@mapNotNull null
        if (c.itemId in hidden) return@mapNotNull null
        var rel = 0.0
        for (f in c.features) {
            val w = pref[f.key] ?: 0.0
            rel += w * f.weightOnItem * f.confidence
        }
        if (!rel.isFinite()) return@mapNotNull null
        c.copy(relevance = rel)
    }.sortedByDescending { it.relevance }

    val selected = mutableListOf<VodCandidate>()
    for (c in scored) {
        if (selected.size >= p.rowSize) break
        val mmr = p.mmrLambda * c.relevance -
            (1 - p.mmrLambda) * selected.maxOfOrNull { similarity(c, it) }.orZero()
        // w praktyce liczymy mmr dla kilku najlepszych pozostałych; tu uproszczenie poglądowe
        if (mmr.isFinite()) selected += c
        if (selected.count { it.directorKey == c.directorKey && c.directorKey != null } > 2) {
            selected.removeLast()
        }
    }
    return selected
}

private fun Double?.orZero() = this ?: 0.0
```

### 22.4. Matcher sportowy

```kotlin
fun parseTeams(title: String, desc: String?, aliases: AliasIndex): TeamParse {
    val text = Channel.normalizeName(title + " " + (desc ?: ""))
    if (text.isEmpty()) return TeamParse(emptyList(), null, "empty", false, 0.0)
    val hits = aliases.findLongest(text) // lista drużyn, bez nachodzenia tokenów
    val ambiguous = aliases.isAmbiguous(hits)
    if (ambiguous) return TeamParse(hits, null, "ambiguous", true, 0.2)
    val pair = splitHeadToHead(text) // "a - b" / "a vs b"
    val teams = when {
        pair != null -> pair.mapNotNull { aliases.exact(it) }
        else -> hits
    }
    val conf = when {
        teams.size == 2 && pair != null -> 0.85
        teams.size == 1 -> 0.55
        else -> 0.3
    }
    return TeamParse(teams, detectCompetition(text), if (pair != null) "h2h" else "alias", false, conf)
}

fun shouldSuggestFollow(history: List<TeamEvidence>, teamId: String, p: PersonalizationParams): Boolean {
    val ofTeam = history.filter { it.teamId == teamId && it.conf >= 0.5 }
    val dates = ofTeam.map { it.localDateWarsaw }.toSet()
    if (dates.size < p.nDates) return false
    val evA = ofTeam.sumOf { it.strength }
    val rivals = history.filter { it.teamId != teamId }
        .groupBy { it.teamId }
        .mapValues { (_, v) -> v.sumOf { it.strength } }
    val evB = rivals.values.maxOrNull() ?: 0.0
    val rhs = evB * p.asymmetryAlpha
    return evA >= rhs && evA.isFinite()
}

fun channelFor(event: SportEvent, matches: List<EventAvailability>, tCh: Double): ChannelVerdict {
    val ok = matches.filter { !it.abstain && it.confidence >= tCh }
        .sortedByDescending { it.confidence }
    return if (ok.isEmpty()) ChannelVerdict.Abstain
    else ChannelVerdict.Known(ok)
}
```

---

## 23. DoD całego etapu MVP (A+B+C)

Funkcję można uznać za gotową do recenzji właściciela (nadal bez mergowania wbrew procesowi), gdy:

1. Zgoda działa; przy wyłączeniu brak nowych wkładów.
2. Sesje liczą wyłącznie aktywny czas; pauza, bufor, seek, preview, surf i błąd są pokryte testami.
3. Żaden test, log ani tabela nie zawiera URL-i strumieni ani danych dostępu.
4. Rekomendacje VOD pochodzą wyłącznie z inwentarza; wyjaśnienia są prawdziwe względem cech.
5. Brak metadanych ≠ wymyślony reżyser/obsada/gatunek.
6. Sport wymaga powtarzalności i potwierdzenia; jeden mecz nic nie „obserwuje”.
7. Kanał bez pewności = „kanał niepotwierdzony”, nigdy zgadywany.
8. Czasy meczów poprawne w Europe/Warsaw w testach DST.
9. Home nie przelicza rankingu na wątku UI; jest snapshot.
10. Istnieje czyszczenie i eksport.
11. Testy: unit + Room + kontrfakty + D-pad Home; braki środowiska oznaczone `NOT_RUN`.
12. Nie ruszono CI, silnika odtwarzania (poza obserwatorem), palety ani bieżących PR-ów 1.0.

---

## 24. Słownik skrótów

| Termin | Znaczenie |
| --- | --- |
| Aktywny czas | czas, gdy player jest READY i `playWhenReady == true` |
| Inwentarz | kanały i VOD aktualnie obecne u dostawców użytkownika |
| Lane / pas | osobny rząd i osobny ranking (VOD vs sport) |
| Shrinkage | kurczenie wagi do prioru przy małej liczbie obserwacji |
| Abstain | system odmawia wskazania kanału zamiast zgadywać |
| Provenance | skąd wzięła się cecha lub wydarzenie |
| Snapshot | zapisany, gotowy do UI wynik rankingu |

---

## 25. Historia dokumentu

| Data (Europe/Warsaw) | Autor role | Zmiana |
| --- | --- | --- |
| 8 listopada 2026 | konspekt na prośbę właściciela | pierwsza wersja; oparta o stary checkout `677cddeb` |

Koniec konspektu.
