package pl.ketchupplayer.tv.core.model.personalization

/** Profil MVP: jedno gospodarstwo na urządzenie (profile z PIN-em to osobny, późniejszy krok). */
const val HOUSEHOLD_PROFILE = "household"

enum class ContentKind { LIVE, MOVIE, EPISODE }

enum class SessionStatus { OPEN, CLOSED }

enum class StartReason { USER, RESUME, AUTOPLAY_PREVIEW }

enum class EndReason { USER_STOP, COMPLETED, ERROR, SURF, PREVIEW, PROCESS_DEATH, BACKGROUND, PROGRAMME_CHANGE }

enum class EvidenceClass { POSITIVE, NEUTRAL, UNKNOWN_FAILURE }

/**
 * Lokalna sesja oglądania. Nie zawiera adresów strumieni, nagłówków ani danych dostępu: tylko identyfikatory katalogu,
 * czasy i liczniki. [activeWatchMs] to czas, gdy odtwarzacz faktycznie grał (gotowy i `playWhenReady`), nie pozycja.
 */
data class WatchSession(
    val id: String,
    val profileId: String,
    val providerId: String,
    val itemId: String,
    val kind: ContentKind,
    val seriesId: String? = null,
    val programmeStartMs: Long? = null,
    val programmeEndMs: Long? = null,
    val programmeTitleNorm: String? = null,
    val startedAtMs: Long,
    val lastEventAtMs: Long,
    val endedAtMs: Long? = null,
    val status: SessionStatus = SessionStatus.OPEN,
    val activeWatchMs: Long = 0,
    val wallWatchMs: Long = 0,
    val pauseMs: Long = 0,
    val bufferMs: Long = 0,
    val seekCount: Int = 0,
    val rebufferCount: Int = 0,
    val maxPositionMs: Long = 0,
    val durationMs: Long? = null,
    val startReason: StartReason = StartReason.USER,
    val endReason: EndReason? = null,
    val evidence: EvidenceClass = EvidenceClass.NEUTRAL,
    val sourceVersion: Int = SESSION_SOURCE_VERSION,
    val dedupKey: String,
    /** Siła sesji już doliczona do profilu (wznowiona sesja dolicza tylko różnicę, nigdy całość drugi raz). */
    val appliedStrength: Double = 0.0,
    /** Sesja liczona w trybie incognito albo bez zgody: nigdy nie staje się dowodem. */
    val incognito: Boolean = false,
)

const val SESSION_SOURCE_VERSION = 1

/** Stan odtwarzacza w jednej chwili (z obserwatora); bez adresu strumienia. */
data class PlaybackSample(
    val wallClockMs: Long,
    val playing: Boolean,
    val paused: Boolean,
    val buffering: Boolean,
    val positionMs: Long,
    val durationMs: Long?,
    val error: Boolean = false,
)

enum class FeatureNamespace { GENRE, DIRECTOR, CAST, TEAM, COMPETITION, CHANNEL }

enum class Provenance { PROVIDER, TMDB, EPG_TITLE, USER }

/** Cecha pozycji katalogu (np. gatunek) z pochodzeniem i pewnością źródła. */
data class ItemFeature(
    val namespace: FeatureNamespace,
    val key: String,
    val label: String,
    val weightOnItem: Double,
    val provenance: Provenance,
    val confidence: Double,
)

/** Waga cechy w profilu preferencji (zanik w czasie i kurczenie przy małej liczbie obserwacji). */
data class FeatureWeight(
    val namespace: FeatureNamespace,
    val key: String,
    val label: String,
    val rawWeight: Double = 0.0,
    val decayedWeight: Double = 0.0,
    val evidence: Double = 0.0,
    val confidence: Double = 0.0,
    val lastUpdatedMs: Long = 0,
    val provenance: Provenance = Provenance.PROVIDER,
)

enum class FeedbackKind { LIKE, DISLIKE, HIDE, FOLLOW, UNFOLLOW, NOT_ME }

enum class FollowState { SUGGESTED, FOLLOWED, REJECTED }

enum class SportEventStatus { SCHEDULED, LIVE, POSTPONED, CANCELED, FINISHED, RETRACTED }

/** Powód na karcie rekomendacji; tylko cechy, które naprawdę weszły do wyniku. */
data class WhyChip(
    val namespace: FeatureNamespace,
    val label: String,
)

/** Karta pasa „Bo oglądasz”: wyłącznie id pozycji z katalogu i etykiety. */
data class BecauseCard(
    val itemId: String,
    val why: List<WhyChip>,
    val explore: Boolean = false,
)

/** Kanał z listy użytkownika, na którym EPG potwierdza transmisję. */
data class SportChannel(
    val channelId: String,
    val channelName: String,
    val confidence: Double,
)

/** Karta meczu obserwowanej drużyny. [channels] puste = „kanał niepotwierdzony” (nie zgadujemy). */
data class SportCard(
    val eventId: String,
    val title: String,
    val kickoffUtcMs: Long,
    val endUtcMs: Long?,
    val live: Boolean,
    val replay: Boolean,
    val statusNote: SportEventStatus,
    val channels: List<SportChannel>,
    val teamIds: List<String>,
    val evidenceAtMs: Long,
)

/** Drużyna z katalogu (wbudowanego albo dodana ręcznie). */
data class Team(
    val id: String,
    val name: String,
    val competitionKey: String? = null,
    val country: String? = null,
    val userDefined: Boolean = false,
)

/**
 * Alias drużyny po normalizacji. [weak] = słowo zbyt ogólne, by liczyć je poza parą „A - B” (np. nazwa miasta);
 * [ambiguousGroup] = alias wspólny dla kilku drużyn (derby, sama „Wisła”): parser nie wybiera strony.
 */
data class TeamAlias(
    val teamId: String,
    val aliasNorm: String,
    val weak: Boolean = false,
    val ambiguousGroup: String? = null,
)

/**
 * Pytanie „obserwować drużynę?” z datami transmisji, które do niego doprowadziły (nigdy cichy follow). Dwie drużyny =
 * użytkownik oglądał zawsze tę samą parę, więc nie zgadujemy, której kibicuje.
 */
data class FollowSuggestion(
    val teams: List<Team>,
    val watchedDatesUtcMs: List<Long>,
)

/** Gotowy do UI wynik personalizacji; Home tylko go czyta. */
data class HomeSnapshot(
    val because: List<BecauseCard> = emptyList(),
    val sport: List<SportCard> = emptyList(),
    val suggestion: FollowSuggestion? = null,
    val builtAtMs: Long = 0,
)
