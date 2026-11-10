package pl.ketchupplayer.tv.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Id i odwołanie do strumienia (adres z listy M3U albo `xtream:...`); wynik zapytań o tożsamość strumienia. */
data class ChannelRef(
    val id: String,
    val ref: String?,
)

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE id = :id")
    suspend fun get(id: String): ProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(provider: ProviderEntity)

    @Query("UPDATE providers SET requestMethod = :method WHERE id = :id")
    suspend fun setRequestMethod(
        id: String,
        method: String,
    )

    @Query("UPDATE providers SET userAgent = :userAgent WHERE id = :id")
    suspend fun setUserAgent(
        id: String,
        userAgent: String?,
    )

    @Query("SELECT userAgent FROM providers WHERE id = :id")
    fun observeUserAgent(id: String): Flow<String?>

    @Query("DELETE FROM providers WHERE id = :id")
    suspend fun delete(id: String)

    @Query(
        "UPDATE providers SET lastRefreshMs = :ms, lastError = :error, " +
            "epgUrl = COALESCE(:epgUrl, epgUrl) WHERE id = :id",
    )
    suspend fun markRefreshed(
        id: String,
        ms: Long,
        error: String?,
        epgUrl: String?,
    )
}

@Dao
interface ChannelDao {
    @Query(
        "SELECT * FROM channels WHERE type = :type ORDER BY CASE WHEN number IS NULL THEN 1 ELSE 0 END, number, name",
    )
    fun observeByType(type: String): Flow<List<ChannelEntity>>

    @Query("SELECT DISTINCT groupName FROM channels WHERE type = :type AND groupName IS NOT NULL ORDER BY groupName")
    fun observeGroups(type: String): Flow<List<String>>

    @Query(
        "SELECT groupName AS name, COUNT(*) AS cnt FROM channels " +
            "WHERE type = :type AND groupName IS NOT NULL GROUP BY groupName ORDER BY groupName",
    )
    fun observeGroupCounts(type: String): Flow<List<NameCount>>

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun get(id: String): ChannelEntity?

    /** Ten sam kanał (po znormalizowanej nazwie) u wszystkich dostawców, wg kolejności dostawców. */
    @Query(
        "SELECT c.* FROM channels c JOIN providers p ON p.id = c.providerId " +
            "WHERE c.type = 'LIVE' AND c.normName = :normName ORDER BY p.sortOrder, p.name, c.name",
    )
    suspend fun sameNamed(normName: String): List<ChannelEntity>

    @Query("SELECT id, epgId, normName FROM channels WHERE type = 'LIVE'")
    suspend fun liveEpgKeys(): List<ChannelEpgKey>

    @Query("SELECT COUNT(*) FROM channels WHERE providerId = :providerId")
    suspend fun count(providerId: String): Int

    @Query("DELETE FROM channels WHERE providerId = :providerId")
    suspend fun deleteByProvider(providerId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)

    /** Podmiana całej listy dostawcy w jednej transakcji (UI widzi starą albo nową, nigdy połowę). */
    @Transaction
    suspend fun replaceAll(
        providerId: String,
        channels: List<ChannelEntity>,
    ) {
        deleteByProvider(providerId)
        channels.chunked(INSERT_CHUNK).forEach { insertAll(it) }
    }

    @Query("SELECT id, streamUrl AS ref FROM channels WHERE providerId = :providerId")
    suspend fun idRefs(providerId: String): List<ChannelRef>

    @Query("SELECT * FROM channel_flags WHERE channelId = :channelId")
    suspend fun flagOf(channelId: String): ChannelFlagEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putFlag(flag: ChannelFlagEntity)

    @Query("DELETE FROM channel_flags WHERE channelId = :channelId")
    suspend fun deleteFlag(channelId: String)

    /**
     * Jak [replaceAll], ale w tej samej transakcji przenosi ulubione, ukryte i „ostatnio oglądane” ze starych id na
     * nowe ([remap]: stare id -> nowe id, pary jednoznaczne). Gdy nowe id ma już własne znaczniki, łączymy je (suma
     * ulubionych i ukrytych, nowsza pozycja oglądania). Stary wpis znika dopiero po zapisaniu nowego; id bez pary
     * zostają nietknięte.
     */
    @Transaction
    suspend fun replaceAllRemapping(
        providerId: String,
        channels: List<ChannelEntity>,
        remap: Map<String, String>,
    ) {
        for ((oldId, newId) in remap) {
            val old = flagOf(oldId) ?: continue
            val current = flagOf(newId)
            putFlag(
                ChannelFlagEntity(
                    channelId = newId,
                    favorite = old.favorite || current?.favorite == true,
                    hidden = old.hidden || current?.hidden == true,
                    lastWatchedMs = listOfNotNull(old.lastWatchedMs, current?.lastWatchedMs).maxOrNull(),
                ),
            )
            deleteFlag(oldId)
        }
        replaceAll(providerId, channels)
    }

    private companion object {
        const val INSERT_CHUNK = 500
    }
}

@Dao
interface ChannelFlagDao {
    @Query("SELECT * FROM channel_flags")
    fun observeAll(): Flow<List<ChannelFlagEntity>>

    @Query("SELECT * FROM channel_flags WHERE channelId = :channelId")
    suspend fun get(channelId: String): ChannelFlagEntity?

    /** Id ostatnio oglądanych kanałów, od najnowszego. */
    @Query(
        "SELECT channelId FROM channel_flags WHERE lastWatchedMs IS NOT NULL " +
            "ORDER BY lastWatchedMs DESC LIMIT :limit",
    )
    fun observeRecentIds(limit: Int): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(flag: ChannelFlagEntity)
}

@Dao
interface EpgDao {
    @Insert
    suspend fun insertStaging(rows: List<EpgStagingEntity>)

    /** Wersja blokująca dla parsera strumieniowego (wołać tylko z wątku IO, nigdy z głównego). */
    @Insert
    fun insertStagingBlocking(rows: List<EpgStagingEntity>)

    @Query("DELETE FROM epg_staging")
    suspend fun clearStaging()

    @Query("DELETE FROM epg_programmes")
    suspend fun clearLive()

    @Query(
        "INSERT INTO epg_programmes (channelId, startMs, endMs, title, description, category) " +
            "SELECT channelId, startMs, endMs, title, description, category FROM epg_staging",
    )
    suspend fun promoteStaging()

    /** Atomowa podmiana: UI widzi stary przewodnik albo nowy, nigdy mieszankę. */
    @Transaction
    suspend fun swapInStaging() {
        clearLive()
        promoteStaging()
        clearStaging()
    }

    @Query("SELECT * FROM epg_programmes WHERE startMs <= :nowMs AND endMs > :nowMs")
    fun observeOnAir(nowMs: Long): Flow<List<EpgProgrammeEntity>>

    /** Audycje wszystkich kanałów zachodzące na okno (Radar). */
    @Query("SELECT * FROM epg_programmes WHERE endMs > :fromMs AND startMs < :toMs")
    suspend fun between(
        fromMs: Long,
        toMs: Long,
    ): List<EpgProgrammeEntity>

    @Query(
        "SELECT * FROM epg_programmes WHERE channelId = :channelId AND endMs > :fromMs " +
            "AND startMs < :toMs ORDER BY startMs",
    )
    suspend fun range(
        channelId: String,
        fromMs: Long,
        toMs: Long,
    ): List<EpgProgrammeEntity>

    @Query("SELECT COUNT(*) FROM epg_programmes")
    suspend fun count(): Int
}

/** Minimalny widok kanału do dopasowania EPG (bez ładowania całej encji). */
data class ChannelEpgKey(
    val id: String,
    val epgId: String?,
    val normName: String,
)

@Suppress("TooManyFunctions") // DAO katalogu VOD: jedna funkcja = jedno zapytanie
@Dao
interface VodDao {
    // Kolejność dostawcy (wstawienia): bez dat dodania „Najnowsze” to jedyna wskazówka, co jest nowe.
    @Query("SELECT * FROM vod_items WHERE kind = :kind ORDER BY rowid")
    fun observeByKind(kind: String): Flow<List<VodItemEntity>>

    @Query(
        "SELECT category AS name, COUNT(*) AS cnt FROM vod_items " +
            "WHERE kind = :kind AND category IS NOT NULL GROUP BY category ORDER BY category",
    )
    fun observeCategoryCounts(kind: String): Flow<List<NameCount>>

    /** Ile list (dostawców) ma ten sam tytuł: ten sam rodzaj, znormalizowana nazwa i rok. */
    @Query(
        "SELECT COUNT(DISTINCT providerId) FROM vod_items " +
            "WHERE kind = :kind AND normTitle = :normTitle AND year IS :year",
    )
    suspend fun sourceCount(
        kind: String,
        normTitle: String,
        year: Int?,
    ): Int

    @Query("SELECT * FROM vod_items WHERE id = :id")
    suspend fun get(id: String): VodItemEntity?

    @Query("SELECT * FROM vod_items WHERE kind = :kind")
    suspend fun byKind(kind: String): List<VodItemEntity>

    /** Filmy bez sprawdzonego czasu trwania (NULL; 0 = sprawdzono, brak u dostawcy), najlepiej oceniane. */
    @Query("SELECT * FROM vod_items WHERE kind = 'MOVIE' AND durationMin IS NULL ORDER BY rating DESC LIMIT :limit")
    suspend fun withoutDuration(limit: Int): List<VodItemEntity>

    /** Filmy ze znanym czasem trwania, najlepiej oceniane (do „Mam czas na…”). */
    @Query(
        "SELECT * FROM vod_items WHERE kind = 'MOVIE' AND durationMin > 0 " +
            "ORDER BY rating DESC LIMIT :limit",
    )
    fun observeTimed(limit: Int): Flow<List<VodItemEntity>>

    @Query("SELECT COUNT(*) FROM vod_items WHERE providerId = :providerId")
    suspend fun count(providerId: String): Int

    @Query("DELETE FROM vod_items WHERE providerId = :providerId")
    suspend fun deleteByProvider(providerId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<VodItemEntity>)

    /**
     * Aktualizacja jednej pozycji w miejscu. `insertAll` (REPLACE) kasuje i wstawia wiersz od nowa, więc film
     * trafiał na koniec katalogu (kolejność „Najnowsze” to rowid) i siatka podążała za nim.
     */
    @androidx.room.Update
    suspend fun update(item: VodItemEntity)

    @Query("DELETE FROM vod_episodes WHERE seriesId IN (SELECT id FROM vod_items WHERE providerId = :providerId)")
    suspend fun deleteEpisodesByProvider(providerId: String)

    @Query("SELECT COUNT(*) FROM vod_episodes WHERE seriesId = :seriesId")
    suspend fun episodeCount(seriesId: String): Int

    @Transaction
    suspend fun replaceAll(
        providerId: String,
        items: List<VodItemEntity>,
        episodes: List<EpisodeEntity> = emptyList(),
    ) {
        deleteEpisodesByProvider(providerId)
        deleteByProvider(providerId)
        items.chunked(INSERT_CHUNK).forEach { insertAll(it) }
        episodes.chunked(INSERT_CHUNK).forEach { insertEpisodes(it) }
    }

    @Query("SELECT id, ref FROM vod_items WHERE providerId = :providerId AND kind = 'MOVIE'")
    suspend fun movieRefs(providerId: String): List<ChannelRef>

    @Query("SELECT * FROM vod_state WHERE itemId = :itemId")
    suspend fun stateOf(itemId: String): VodStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putState(state: VodStateEntity)

    @Query("DELETE FROM vod_state WHERE itemId = :itemId")
    suspend fun deleteState(itemId: String)

    @Query("SELECT * FROM vod_meta WHERE itemId = :itemId")
    suspend fun metaOf(itemId: String): VodMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMeta(meta: VodMetaEntity)

    @Query("DELETE FROM vod_meta WHERE itemId = :itemId")
    suspend fun deleteMeta(itemId: String)

    /**
     * Jak [replaceAll], z przeniesieniem „Mojej listy”, postępu oglądania i ocen filmów ze starych id na nowe
     * ([remap], pary jednoznaczne po numerze strumienia filmu). Seriale i odcinki nie mają bezpiecznego odpowiednika
     * bez dodatkowych zapytań do dostawcy, więc ich dane zostają pod starymi id (nic nie jest kasowane).
     */
    @Transaction
    suspend fun replaceAllRemapping(
        providerId: String,
        items: List<VodItemEntity>,
        episodes: List<EpisodeEntity>,
        remap: Map<String, String>,
    ) {
        for ((oldId, newId) in remap) {
            stateOf(oldId)?.let { old ->
                val current = stateOf(newId)
                val newer = if ((current?.updatedMs ?: -1L) >= (old.updatedMs ?: -1L)) current else old
                putState(
                    VodStateEntity(
                        itemId = newId,
                        myList = old.myList || current?.myList == true,
                        positionMs = newer?.positionMs ?: 0,
                        durationMs = newer?.durationMs ?: 0,
                        updatedMs = listOfNotNull(old.updatedMs, current?.updatedMs).maxOrNull(),
                    ),
                )
                deleteState(oldId)
            }
            metaOf(oldId)?.let { old ->
                if (metaOf(newId) == null) putMeta(old.copy(itemId = newId))
                deleteMeta(oldId)
            }
        }
        replaceAll(providerId, items, episodes)
    }

    /**
     * Po imporcie: seriale, których jeszcze nie znamy, dostają stan wyjściowy (nowe odcinki liczymy od teraz).
     * Wyjątek: serial zmieniony nie dawniej niż [sinceMs] zostaje „niewidziany” (NULL), więc jest nowy do otwarcia.
     */
    @Query(
        "INSERT OR IGNORE INTO vod_seen(itemId, seenAddedMs, seenEpisodes) " +
            "SELECT id, CASE WHEN addedMs >= :sinceMs THEN NULL ELSE addedMs END, " +
            "(SELECT COUNT(*) FROM vod_episodes e WHERE e.seriesId = vod_items.id) " +
            "FROM vod_items WHERE kind = 'SERIES'",
    )
    suspend fun baselineSeen(sinceMs: Long)

    /** Otwarcie serialu: bieżący stan staje się „widzianym”, kropka znika. */
    @Query(
        "INSERT OR REPLACE INTO vod_seen(itemId, seenAddedMs, seenEpisodes) " +
            "SELECT id, addedMs, (SELECT COUNT(*) FROM vod_episodes e WHERE e.seriesId = vod_items.id) " +
            "FROM vod_items WHERE id = :id AND kind = 'SERIES'",
    )
    suspend fun markSeen(id: String)

    @Query(
        "SELECT i.id FROM vod_items i JOIN vod_seen s ON s.itemId = i.id WHERE i.kind = 'SERIES' AND (" +
            "(i.addedMs IS NOT NULL AND s.seenAddedMs IS NULL AND i.addedMs >= :sinceMs) OR " +
            "(i.addedMs IS NOT NULL AND s.seenAddedMs IS NOT NULL AND i.addedMs > s.seenAddedMs) OR " +
            "(s.seenEpisodes > 0 AND (SELECT COUNT(*) FROM vod_episodes e WHERE e.seriesId = i.id) > s.seenEpisodes))",
    )
    fun observeNewSeriesIds(sinceMs: Long): Flow<List<String>>

    @Query("SELECT * FROM vod_meta")
    fun observeMeta(): Flow<List<VodMetaEntity>>

    @Query("SELECT * FROM vod_meta WHERE itemId = :id")
    suspend fun meta(id: String): VodMetaEntity?

    @Query("SELECT * FROM vod_meta WHERE itemId = :id")
    fun observeMetaOf(id: String): Flow<VodMetaEntity?>

    @Query("SELECT * FROM vod_items WHERE id = :id")
    fun observeItem(id: String): Flow<VodItemEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeta(meta: VodMetaEntity)

    @Query("SELECT * FROM vod_episodes WHERE seriesId = :seriesId ORDER BY season, number")
    fun observeEpisodes(seriesId: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM vod_episodes WHERE id = :id")
    suspend fun episode(id: String): EpisodeEntity?

    @Query("DELETE FROM vod_episodes WHERE seriesId = :seriesId")
    suspend fun deleteEpisodes(seriesId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(items: List<EpisodeEntity>)

    @Transaction
    suspend fun replaceEpisodes(
        seriesId: String,
        items: List<EpisodeEntity>,
    ) {
        deleteEpisodes(seriesId)
        insertEpisodes(items)
    }

    @Query("SELECT * FROM vod_state")
    fun observeState(): Flow<List<VodStateEntity>>

    @Query("SELECT * FROM vod_state WHERE itemId = :id")
    suspend fun state(id: String): VodStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(state: VodStateEntity)

    private companion object {
        const val INSERT_CHUNK = 500
    }
}

/** Nazwa grupy/kategorii i liczba pozycji (do ekranu wyboru grup). */
data class NameCount(
    val name: String,
    val cnt: Int,
)
