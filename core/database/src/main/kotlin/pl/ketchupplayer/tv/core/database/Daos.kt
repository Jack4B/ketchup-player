package pl.ketchupplayer.tv.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE id = :id")
    suspend fun get(id: String): ProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(provider: ProviderEntity)

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
