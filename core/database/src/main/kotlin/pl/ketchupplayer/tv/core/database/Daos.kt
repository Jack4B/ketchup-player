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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(flag: ChannelFlagEntity)
}
