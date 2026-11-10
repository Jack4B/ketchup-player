package pl.ketchupplayer.tv.feature.home

import pl.ketchupplayer.tv.core.data.vod.VodEntry
import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.EpgProgramme
import pl.ketchupplayer.tv.core.model.personalization.FollowSuggestion
import pl.ketchupplayer.tv.core.model.personalization.SportCard
import pl.ketchupplayer.tv.core.model.personalization.WhyChip

/** Kanał z audycją trwającą teraz (jeśli jest EPG). */
data class ChannelNow(
    val channel: Channel,
    val now: EpgProgramme?,
)

/** Pozycja pasa „Bo oglądasz”: film lub serial z katalogu z prawdziwymi powodami rekomendacji. */
data class BecauseEntry(
    val entry: VodEntry,
    val why: List<WhyChip>,
    val explore: Boolean = false,
    val liked: Boolean = false,
)

/** Akcje personalizacji na Home (oceny i decyzje o drużynach). Domyślnie puste: podglądy i testy innych rzędów. */
class PersonalActions(
    val onLike: (itemId: String, liked: Boolean) -> Unit = { _, _ -> },
    val onHide: (itemId: String) -> Unit = {},
    val onFollow: (teamIds: List<String>) -> Unit = {},
    val onDecline: (teamIds: List<String>) -> Unit = {},
)

data class HomeUiState(
    val loading: Boolean = true,
    val hasProviders: Boolean = false,
    /** Ostatnio oglądane kanały (najnowszy pierwszy). */
    val recent: List<ChannelNow> = emptyList(),
    /** Ulubione, a gdy ich brak: początek listy kanałów. */
    val liveNow: List<ChannelNow> = emptyList(),
    val nowMs: Long = 0,
    /** Hero: film do wznowienia, a gdy brak - najwyżej oceniana pozycja katalogu. */
    val featured: VodEntry? = null,
    val featuredResumes: Boolean = false,
    val continueVod: List<VodEntry> = emptyList(),
    val myList: List<VodEntry> = emptyList(),
    /** Seriale z nowymi odcinkami od ostatniej wizyty w szczegółach (zielona kropka). */
    val newEpisodes: List<VodEntry> = emptyList(),
    /** Najlepiej oceniane filmy ze znanym czasem trwania (źródło dla „Mam czas na…”). */
    val timed: List<VodEntry> = emptyList(),
    /** Pas „Bo oglądasz” (tylko przy włączonej personalizacji i po zebraniu dowodów; inaczej pusty). */
    val because: List<BecauseEntry> = emptyList(),
    /** Najbliższe mecze obserwowanych drużyn według EPG; pusty kanał = „kanał niepotwierdzony”. */
    val sport: List<SportCard> = emptyList(),
    /** Najbliższy mecz jest w ciągu 48 godzin: pas idzie wyżej. */
    val sportSoon: Boolean = false,
    /** Pytanie o obserwowanie drużyny (najwyżej jedno naraz); nigdy nie jest to cichy wybór. */
    val followSuggestion: FollowSuggestion? = null,
)
