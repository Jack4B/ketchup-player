package pl.ketchupplayer.tv.core.data.personalization

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import pl.ketchupplayer.tv.core.common.Clock
import pl.ketchupplayer.tv.core.database.ChannelEntity
import pl.ketchupplayer.tv.core.database.EpgStagingEntity
import pl.ketchupplayer.tv.core.database.EpisodeEntity
import pl.ketchupplayer.tv.core.database.KetchupDatabase
import pl.ketchupplayer.tv.core.database.ProviderEntity
import pl.ketchupplayer.tv.core.database.TeamAliasEntity
import pl.ketchupplayer.tv.core.database.TeamEntity
import pl.ketchupplayer.tv.core.database.VodItemEntity
import pl.ketchupplayer.tv.core.database.VodMetaEntity
import pl.ketchupplayer.tv.core.model.Channel
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Zegar testowy przesuwany ręcznie. */
class MutableClock(
    var now: Long,
) : Clock {
    override fun nowMs(): Long = now

    fun advance(ms: Long) {
        now += ms
    }
}

class FakePersonalizationSettings(
    initial: PersonalizationPrefs = PersonalizationPrefs(),
) : PersonalizationSettings {
    override val prefs = MutableStateFlow(initial)

    override suspend fun setEnabled(enabled: Boolean) {
        prefs.value = prefs.value.copy(enabled = enabled, incognito = prefs.value.incognito && enabled)
    }

    override suspend fun setIncognito(incognito: Boolean) {
        prefs.value = prefs.value.copy(incognito = incognito)
    }

    override suspend fun setRetentionDays(days: Int) {
        prefs.value = prefs.value.copy(retentionDays = days)
    }
}

/**
 * Pełny tor personalizacji na prawdziwej bazie Room (w pamięci) z katalogiem zmyślonych filmów, kanałów i drużyn.
 * Adres listy i strumieni celowo zawiera „sekrety” (zmyślone), żeby testy mogły sprawdzić, że nigdzie nie wyciekają.
 */
class PersonalizationFixture(
    val db: KetchupDatabase = newDb(),
    val clock: MutableClock = MutableClock(T0),
    val settings: FakePersonalizationSettings = FakePersonalizationSettings(),
) {
    val dao = db.personalization()
    val sportDao = db.sport()
    private val indexer = FeatureIndexer(dao, clock)
    val sport = SportStore(sportDao, dao)
    val engine = PersonalizationEngine(dao, settings, indexer, sport, clock)
    val recorder = SessionRecorder(dao, db.channels(), db.vod(), db.epg(), settings, engine, clock)
    val repo =
        DefaultPersonalizationRepository(
            settings,
            dao,
            sport,
            engine,
            recorder,
            clock,
            // Przewodnik nie jest potrzebny w tych testach (nasłuch zmian startuje tylko w `onAppStart`).
            dagger.Lazy<pl.ketchupplayer.tv.core.data.epg.EpgRepository> {
                error("EPG nieużywane w testach przepływu")
            },
        )

    /** „Nowy proces” na tej samej bazie: nowe obiekty, ta sama zawartość dysku. */
    fun restarted() = PersonalizationFixture(db, clock, settings)

    suspend fun seedCatalog() {
        db.providers().upsert(ProviderEntity(PROVIDER, "Lista testowa", "XTREAM", SECRET_URL, lastRefreshMs = 1))
        db.vod().insertAll(
            listOf(
                movie("film-a", "Cień nad zatoką", "Kryminał"),
                movie("film-b", "Ostatni prom", "Kryminał"),
                movie("film-c", "Wesele u sąsiadów", "Komedia"),
                movie("film-d", "Mgła na molo", "Kryminał"),
                movie("film-e", "Druga latarnia", "Kryminał, Thriller"),
                movie("film-f", "Sezon ogórkowy", "Komedia"),
                movie("film-g", "Bez metadanych", null),
                VodItemEntity(
                    "serial-port",
                    PROVIDER,
                    "SERIES",
                    "Przykład z Portu",
                    "przyklad z portu",
                    "s1",
                    null,
                    null,
                    2025,
                    null,
                    "Dramat",
                    null,
                    50,
                    null,
                    null,
                    "Jan Przykładowy, Ewa Zmyślona",
                ),
            ),
        )
        listOf("film-a", "film-b", "film-d").forEach { director(it, "Anna Testowa") }
        director("film-e", "Marek Wymyślony")
        db.vod().insertEpisodes((1..8).map { episode(it) })
        db.channels().insertAll(
            listOf(
                channel("ch_sport_1", "Sport Testowy 1"),
                channel("ch_sport_2", "Sport Testowy 2"),
                channel("ch_info", "Info"),
            ),
        )
        listOf("Wisła Nadmorska", "Hutnik Leśny", "Górnik Zmyślony", "Stal Próbna").forEach { team(it) }
        sportDao.putAliases(
            listOf("arka-testowa", "lechia-testowa")
                .map {
                    TeamAliasEntity(it, "derby trojmiasta", null, "derby-trojmiasta")
                }.also { listOf("Arka Testowa", "Lechia Testowa").forEach { n -> team(n) } },
        )
    }

    private fun movie(
        id: String,
        title: String,
        genre: String?,
    ) = VodItemEntity(
        id,
        PROVIDER,
        "MOVIE",
        title,
        Channel.normalizeName(title),
        "$SECRET_URL&stream=$id",
        null,
        null,
        2025,
        7.0,
        genre,
        null,
        100,
        null,
        null,
        null,
    )

    private suspend fun director(
        itemId: String,
        name: String,
    ) = db.vod().upsertMeta(
        VodMetaEntity(itemId, 7.1, null, null, clock.now, found = true, director = name, tmdbMatch = "ID"),
    )

    private fun episode(n: Int) =
        EpisodeEntity("serial-port:e$n", "serial-port", 1, n, "Odcinek $n", "e$n", null, 50, null, null)

    private fun channel(
        id: String,
        name: String,
    ) = ChannelEntity(
        id,
        PROVIDER,
        name,
        Channel.normalizeName(name),
        null,
        "$id.example",
        null,
        "Sport",
        "LIVE",
        "$SECRET_URL&ch=$id",
        null,
    )

    private suspend fun team(name: String) {
        val id = Channel.normalizeName(name).replace(' ', '-')
        sportDao.putTeam(TeamEntity(id, name, Channel.normalizeName(name), null, null, "USER", 1))
        sportDao.putAliases(listOf(TeamAliasEntity(id, Channel.normalizeName(name), null, null)))
    }

    /** Podmienia cały przewodnik (jak prawdziwy import EPG: atomowo). */
    suspend fun epg(vararg slots: EpgStagingEntity) {
        db.epg().clearStaging()
        db.epg().insertStaging(slots.toList())
        db.epg().swapInStaging()
    }

    fun slot(
        channel: String,
        startMs: Long,
        title: String,
        minutes: Int = 120,
        description: String? = null,
    ) = EpgStagingEntity(0, channel, startMs, startMs + minutes * MIN, title, description, "Sport")

    private fun snapshot(
        playing: Boolean,
        positionMs: Long,
        durationMs: Long?,
        buffering: Boolean = false,
        error: Boolean = false,
    ) = PlaybackSnapshot(playing, buffering, error, ended = false, positionMs = positionMs, durationMs = durationMs)

    /** Ogląda [seconds] sekund ciągłego grania krokami po 30 s i zamyka sesję. */
    suspend fun watch(
        target: PlaybackTarget,
        seconds: Long,
        durationMs: Long? = null,
        fromPositionMs: Long = 0,
    ) {
        val tracking = recorder.open(target)
        play(tracking, seconds, durationMs, fromPositionMs)
        tracking.close()
        recorder.awaitIdle()
    }

    fun play(
        tracking: PlaybackTracking,
        seconds: Long,
        durationMs: Long? = null,
        fromPositionMs: Long = 0,
    ) {
        var position = fromPositionMs
        tracking.onState(snapshot(true, position, durationMs))
        var left = seconds * 1000
        while (left > 0) {
            val step = minOf(left, STEP_MS)
            clock.advance(step)
            position += step
            left -= step
            tracking.onState(snapshot(true, position, durationMs))
        }
    }

    /** Stan bez grania (pauza albo buforowanie) przez [seconds] sekund. */
    fun idle(
        tracking: PlaybackTracking,
        seconds: Long,
        positionMs: Long,
        buffering: Boolean,
    ) {
        tracking.onState(snapshot(false, positionMs, null, buffering = buffering))
        var left = seconds * 1000
        while (left > 0) {
            val step = minOf(left, STEP_MS)
            clock.advance(step)
            left -= step
            tracking.onState(snapshot(false, positionMs, null, buffering = buffering))
        }
    }

    fun error(tracking: PlaybackTracking) = tracking.onState(snapshot(false, 0, null, error = true))

    suspend fun weight(key: String): Double =
        dao.features("household").firstOrNull { it.namespace + ":" + it.featureKey == key }?.decayedWeight ?: 0.0

    suspend fun evidence(key: String): Double =
        dao.features("household").firstOrNull { it.namespace + ":" + it.featureKey == key }?.evidence ?: 0.0

    /** Zrzut wszystkich tabel personalizacji jako tekst (do szukania wycieków). */
    fun dumpLearned(): String =
        TABLES.joinToString("\n") { table ->
            db.query("SELECT * FROM $table", null).use { c ->
                buildString {
                    while (c.moveToNext()) {
                        for (i in 0 until c.columnCount) append(c.getString(i)).append('|')
                        append('\n')
                    }
                }
            }
        }

    fun close() {
        engine.cancelPending()
        recorder.invalidate()
        db.close()
    }

    companion object {
        const val PROVIDER = "p1"
        const val MIN = 60_000L
        const val STEP_MS = 30_000L
        const val FILM_MS = 100 * MIN
        const val SECRET_USER = "USER_TAJNY_123"
        const val SECRET_PASS = "HASLO_TAJNE_456"
        const val SECRET_URL = "http://panel.example.invalid:8080/get.php?username=$SECRET_USER&password=$SECRET_PASS"

        /** Sobota 7.03.2026 10:00 UTC (11:00 w Warszawie). */
        val T0: Long = utc(3, 7, 10)

        val TABLES =
            listOf(
                "watch_sessions",
                "preference_features",
                "explicit_feedback",
                "team_follows",
                "sport_events",
                "event_availability",
                "home_snapshots",
                "personalization_meta",
            )

        fun utc(
            month: Int,
            day: Int,
            hour: Int,
            minute: Int = 0,
        ): Long = LocalDateTime.of(2026, month, day, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

        fun newDb(): KetchupDatabase =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Context>(),
                    KetchupDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
    }
}
