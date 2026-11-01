package pl.ketchupplayer.tv.feature.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.ketchupplayer.tv.core.common.Clock
import pl.ketchupplayer.tv.core.data.catalog.CatalogRepository
import pl.ketchupplayer.tv.core.data.epg.EpgRepository
import pl.ketchupplayer.tv.core.model.EpgProgramme
import pl.ketchupplayer.tv.epg.radar.InterestMatcher
import pl.ketchupplayer.tv.epg.radar.RadarEngine
import pl.ketchupplayer.tv.epg.radar.RadarPolicy
import pl.ketchupplayer.tv.epg.radar.RadarSlot
import javax.inject.Inject

@HiltViewModel
class GuideViewModel
    @Inject
    constructor(
        private val catalog: CatalogRepository,
        private val epg: EpgRepository,
        private val interestsRepo: pl.ketchupplayer.tv.core.data.interests.InterestsRepository,
        private val groupsRepo: pl.ketchupplayer.tv.core.data.groups.GroupsRepository,
        private val playerFactory: pl.ketchupplayer.tv.player.engine.PlayerControllerFactory,
        private val prefs: pl.ketchupplayer.tv.core.data.playback.PlayerPrefsRepository,
        private val clock: Clock,
    ) : ViewModel() {
        /** Okno liczone raz na wejście do ekranu; przewijanie czasu w przód to kolejna iteracja. */
        private val windowStart = GuideGeometry.windowStart(clock.nowMs())

        private val epgRevision = MutableStateFlow(0)

        init {
            // Przewodnik może być pierwszym ekranem po starcie: dane EPG nie mogą zależeć od wizyty w „Na żywo”.
            // Po imporcie wiersze przeładowują audycje (inaczej zostałby napis „Brak danych przewodnika”).
            viewModelScope.launch {
                epg.refreshIfStale(STALE_MS)
                epgRevision.value++
            }
            // EPG potrafi dojść w tle (start aplikacji, dodanie listy): wiersze przeładowują wtedy audycje.
            // Wartość początkowa z chwili utworzenia (nie „pierwsza emisja”): import kończący się tuż przed
            // zasubskrybowaniem też przeładuje wiersze.
            val initialRevision = epg.revision.value
            viewModelScope.launch { epg.revision.collect { if (it > initialRevision) epgRevision.value++ } }
        }

        private var previewController: pl.ketchupplayer.tv.player.engine.PlayerController? = null
        private val previewRequests = MutableStateFlow<String?>(null)
        private val _preview = MutableStateFlow(PreviewUi())

        /** Podgląd wideo zaznaczonego kanału (odtwarzacz powstaje po chwili skupienia; zwalniany po wyjściu). */
        val preview: StateFlow<PreviewUi> = _preview

        init {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            viewModelScope.launch {
                previewRequests.debounce(PREVIEW_DELAY_MS).collectLatest { id ->
                    if (id != null && prefs.livePreview.first()) startPreview(id)
                }
            }
        }

        fun previewChannel(channelId: String) {
            previewRequests.value = channelId
        }

        private suspend fun startPreview(channelId: String) {
            val spec = catalog.resolveStream(channelId).getOrNull() ?: return
            val controller = previewController ?: playerFactory.create().also { previewController = it }
            controller.play(spec)
            controller.player?.volume = 0f // podgląd bez dźwięku: pełny dźwięk dopiero w pełnym ekranie
            _preview.value = PreviewUi(controller.player, channelId)
        }

        /** Zatrzymuje i zwalnia podgląd (wyjście z Na żywo, wejście w pełny ekran): jeden dekoder naraz. */
        fun releasePreview() {
            previewRequests.value = null
            previewController?.release()
            previewController = null
            _preview.value = PreviewUi()
        }

        override fun onCleared() {
            previewController?.release()
        }

        private val selection = MutableStateFlow<GuideGroup>(GuideGroup.All)

        val state: StateFlow<GuideUiState> =
            combine(
                combine(
                    catalog.liveChannels(),
                    catalog.liveGroups(),
                    selection,
                    groupsRepo.hidden(pl.ketchupplayer.tv.core.data.groups.GroupKind.TV),
                ) { c, g, sel, hidden ->
                    // Ukryte grupy (Ustawienia → Grupy) znikają z listy kanałów i z panelu grup.
                    Triple(c.filter { it.group !in hidden }, g.filter { it !in hidden }, sel)
                },
                combine(catalog.favoriteIds(), catalog.recentChannels(RECENT_LIMIT), epgRevision) { f, r, rev ->
                    Triple(f, r, rev)
                },
            ) { (channels, groups, sel), (favorites, recent, revision) ->
                val showFavorites = sel is GuideGroup.Favorites && favorites.isNotEmpty()
                val showRecent = sel is GuideGroup.Recent && recent.isNotEmpty()
                val group = (sel as? GuideGroup.Named)?.name?.takeIf { it in groups }
                val visible =
                    when {
                        showFavorites -> channels.filter { it.id in favorites }
                        showRecent -> recent
                        group != null -> channels.filter { it.group == group }
                        else -> channels.sortedByDescending { it.id in favorites }
                    }
                GuideUiState(
                    loading = false,
                    channels = visible,
                    favorites = favorites,
                    windowStartMs = windowStart,
                    windowEndMs = GuideGeometry.windowEnd(windowStart),
                    nowMs = clock.nowMs(),
                    epgRevision = revision,
                    groups = orderGroups(groups),
                    selectedGroup = group,
                    favoritesSelected = showFavorites,
                    recentSelected = showRecent,
                    groupCounts = channels.groupingBy { it.group.orEmpty() }.eachCount(),
                    totalCount = channels.size,
                    recentCount = recent.size,
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                GuideUiState(windowStartMs = windowStart, windowEndMs = GuideGeometry.windowEnd(windowStart)),
            )

        fun selectGroup(group: GuideGroup) {
            selection.value = group
        }

        /** Długie OK na kanale: ulubiony/nieulubiony (przeżywa odświeżenie listy dostawcy). */
        fun toggleFavorite(channelId: String) {
            viewModelScope.launch { catalog.setFavorite(channelId, channelId !in state.value.favorites) }
        }

        private val radarSlot = MutableStateFlow(RadarSlot.TOP_DAY)

        /** Radar: wybrany slot, przeliczany po imporcie EPG, zmianie ulubionych albo kanałów. */
        val radar: StateFlow<RadarUiState> =
            combine(
                catalog.liveChannels(),
                catalog.favoriteIds(),
                radarSlot,
                epgRevision,
            ) { channels, favorites, slot, _ ->
                val now = clock.nowMs()
                val window = radarWindow(now)
                val programmes = epg.window(window.dayStart, window.dayEnd)
                RadarUiState(slot, false, RadarEngine.build(slot, now, window, channels, programmes, favorites))
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RadarUiState())

        /** Moje zainteresowania: słowa kluczowe + audycje z najbliższych 24 h, które do nich pasują. */
        val interests: StateFlow<InterestsUiState> =
            combine(interestsRepo.keywords, catalog.liveChannels(), epgRevision) { keywords, channels, _ ->
                val now = clock.nowMs()
                val programmes = if (keywords.isEmpty()) emptyList() else epg.window(now, now + 24 * HOUR_MS)
                InterestsUiState(keywords, InterestMatcher.match(keywords, channels, programmes, now))
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), InterestsUiState())

        fun addInterest(keyword: String) {
            viewModelScope.launch { interestsRepo.add(keyword) }
        }

        fun removeInterest(keyword: String) {
            viewModelScope.launch { interestsRepo.remove(keyword) }
        }

        fun selectRadarSlot(slot: RadarSlot) {
            radarSlot.value = slot
        }

        /** Dzień = lokalna doba, wieczór = 18:00–23:00 lokalnie (strefa urządzenia). */
        private fun radarWindow(now: Long): RadarPolicy.Window {
            val cal =
                java.util.Calendar
                    .getInstance()
                    .apply { timeInMillis = now }
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val dayStart = cal.timeInMillis
            val hour = HOUR_MS
            return RadarPolicy.Window(
                dayStart,
                dayStart + 24 * hour,
                dayStart + EVENING_FROM_H * hour,
                dayStart + EVENING_TO_H * hour,
            )
        }

        /** Audycje jednego wiersza; wołane leniwie dla widocznych kanałów. */
        suspend fun programmes(channelId: String): List<EpgProgramme> {
            val s = state.value
            return epg.range(channelId, s.windowStartMs, s.windowEndMs)
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
            const val PREVIEW_DELAY_MS = 900L
            const val STALE_MS = 6L * 60 * 60 * 1000
            const val RECENT_LIMIT = 30
            const val HOUR_MS = 60L * 60 * 1000
            const val EVENING_FROM_H = 18
            const val EVENING_TO_H = 23
        }
    }

private val polishGroup = Regex("^(PL|POLSKA|POLAND)([^A-Za-z]|$)", RegexOption.IGNORE_CASE)

/** Grupy polskie (`PL| ...`, `Polska`) na początku, reszta alfabetycznie; sortowanie jest stabilne. */
internal fun orderGroups(groups: List<String>): List<String> =
    groups.sortedWith(compareBy({ !polishGroup.containsMatchIn(it) }, { it.lowercase() }))
