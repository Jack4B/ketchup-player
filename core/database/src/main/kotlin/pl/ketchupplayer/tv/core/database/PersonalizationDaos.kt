package pl.ketchupplayer.tv.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

/** Liczba pozycji z daną cechą (do korekty o powszechność cechy w katalogu). */
data class FeatureCount(
    val namespace: String,
    val featureKey: String,
    val items: Int,
)

/** Liczba sesji w danej klasie dowodu (metryki diagnostyczne, bez danych osobowych). */
data class EvidenceCount(
    val evidenceClass: String,
    val sessions: Int,
)

/** Metadane pozycji VOD potrzebne do wyliczenia cech (pola dostawcy i TMDB). */
data class VodFeatureSource(
    val id: String,
    val genre: String?,
    val cast: String?,
    val tmdbGenre: String?,
    val tmdbCast: String?,
    val director: String?,
    val tmdbMatch: String?,
)

/** Audycja EPG bez pełnego opisu (parser drużyn potrzebuje tylko początku). */
data class EpgSlotRow(
    val channelId: String,
    val startMs: Long,
    val endMs: Long,
    val title: String,
    val description: String?,
    val category: String?,
)

/** Kanał z listy użytkownika z informacją, czy jest ukryty. */
data class InventoryChannelRow(
    val id: String,
    val name: String,
    val providerId: String,
    val hidden: Boolean,
)

@Dao
@Suppress("TooManyFunctions") // DAO personalizacji: jedna funkcja = jedno zapytanie
interface PersonalizationDao {
    // ---- sesje ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSession(session: WatchSessionEntity)

    @Query("SELECT * FROM watch_sessions WHERE id = :id")
    suspend fun session(id: String): WatchSessionEntity?

    @Query("SELECT * FROM watch_sessions WHERE profileId = :profileId AND dedupKey = :dedupKey")
    suspend fun sessionByKey(
        profileId: String,
        dedupKey: String,
    ): WatchSessionEntity?

    @Query(
        "SELECT * FROM watch_sessions WHERE profileId = :profileId AND itemId = :itemId " +
            "ORDER BY lastEventAtMs DESC LIMIT 1",
    )
    suspend fun lastSessionOf(
        profileId: String,
        itemId: String,
    ): WatchSessionEntity?

    @Query("SELECT * FROM watch_sessions WHERE status = 'OPEN'")
    suspend fun openSessions(): List<WatchSessionEntity>

    @Query(
        "SELECT * FROM watch_sessions WHERE profileId = :profileId AND seriesId = :seriesId " +
            "AND startedAtMs BETWEEN :fromMs AND :toMs",
    )
    suspend fun seriesSessions(
        profileId: String,
        seriesId: String,
        fromMs: Long,
        toMs: Long,
    ): List<WatchSessionEntity>

    @Query(
        "SELECT * FROM watch_sessions WHERE profileId = :profileId AND contentKind = 'LIVE' " +
            "AND evidenceClass = 'POSITIVE' AND programmeTitleNorm IS NOT NULL AND startedAtMs >= :sinceMs",
    )
    suspend fun liveEvidence(
        profileId: String,
        sinceMs: Long,
    ): List<WatchSessionEntity>

    @Query("SELECT * FROM watch_sessions WHERE profileId = :profileId ORDER BY startedAtMs DESC LIMIT :limit")
    suspend fun recentSessions(
        profileId: String,
        limit: Int,
    ): List<WatchSessionEntity>

    @Query(
        "SELECT COUNT(*) FROM watch_sessions WHERE profileId = :profileId AND evidenceClass = 'POSITIVE' " +
            "AND contentKind != 'LIVE' AND appliedStrength > 0",
    )
    suspend fun qualifyingVodSessions(profileId: String): Int

    @Query(
        "SELECT evidenceClass, COUNT(*) AS sessions FROM watch_sessions WHERE profileId = :profileId GROUP BY evidenceClass",
    )
    suspend fun evidenceCounts(profileId: String): List<EvidenceCount>

    @Query("SELECT COUNT(*) FROM watch_sessions")
    suspend fun sessionCount(): Int

    @Query("DELETE FROM watch_sessions WHERE startedAtMs < :beforeMs")
    suspend fun deleteSessionsBefore(beforeMs: Long): Int

    @Query(
        "DELETE FROM watch_sessions WHERE id IN " +
            "(SELECT id FROM watch_sessions ORDER BY startedAtMs DESC LIMIT -1 OFFSET :keep)",
    )
    suspend fun trimSessions(keep: Int): Int

    @Query("DELETE FROM watch_sessions")
    suspend fun clearSessions()

    // ---- profil preferencji ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putFeatures(features: List<PreferenceFeatureEntity>)

    @Query("SELECT * FROM preference_features WHERE profileId = :profileId")
    suspend fun features(profileId: String): List<PreferenceFeatureEntity>

    @Query(
        "DELETE FROM preference_features WHERE profileId = :profileId AND namespace = :namespace " +
            "AND featureKey NOT IN " +
            "(SELECT featureKey FROM preference_features WHERE profileId = :profileId AND namespace = :namespace " +
            "ORDER BY ABS(decayedWeight) DESC LIMIT :keep)",
    )
    suspend fun pruneFeatures(
        profileId: String,
        namespace: String,
        keep: Int,
    ): Int

    @Query("DELETE FROM preference_features")
    suspend fun clearFeatures()

    // ---- jawne oceny ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putFeedback(feedback: ExplicitFeedbackEntity)

    @Query(
        "DELETE FROM explicit_feedback WHERE profileId = :profileId AND targetType = :targetType " +
            "AND targetId = :targetId AND feedback = :feedback",
    )
    suspend fun deleteFeedback(
        profileId: String,
        targetType: String,
        targetId: String,
        feedback: String,
    ): Int

    @Query("SELECT * FROM explicit_feedback WHERE profileId = :profileId")
    suspend fun feedback(profileId: String): List<ExplicitFeedbackEntity>

    @Query("SELECT * FROM explicit_feedback WHERE profileId = :profileId")
    fun observeFeedback(profileId: String): Flow<List<ExplicitFeedbackEntity>>

    @Query("DELETE FROM explicit_feedback")
    suspend fun clearFeedback()

    // ---- gotowe pasy Home ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSnapshot(snapshot: HomeSnapshotEntity)

    @Query("SELECT * FROM home_snapshots WHERE profileId = :profileId")
    fun observeSnapshots(profileId: String): Flow<List<HomeSnapshotEntity>>

    @Query("SELECT * FROM home_snapshots WHERE profileId = :profileId")
    suspend fun snapshots(profileId: String): List<HomeSnapshotEntity>

    @Query("DELETE FROM home_snapshots")
    suspend fun clearSnapshots()

    // ---- metadane personalizacji ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMeta(meta: PersonalizationMetaEntity)

    @Query("SELECT value FROM personalization_meta WHERE profileId = :profileId AND `key` = :key")
    suspend fun meta(
        profileId: String,
        key: String,
    ): String?

    @Query("DELETE FROM personalization_meta")
    suspend fun clearMeta()

    // ---- indeks cech VOD ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putVodFeatures(features: List<VodFeatureEntity>)

    @Query("DELETE FROM vod_feature_index WHERE itemId IN (:itemIds)")
    suspend fun deleteVodFeatures(itemIds: List<String>)

    @Query("DELETE FROM vod_feature_index")
    suspend fun clearVodFeatures()

    @Query("SELECT * FROM vod_feature_index WHERE itemId = :itemId")
    suspend fun vodFeaturesOf(itemId: String): List<VodFeatureEntity>

    @Query("SELECT * FROM vod_feature_index WHERE itemId IN (:itemIds)")
    suspend fun vodFeaturesOfItems(itemIds: List<String>): List<VodFeatureEntity>

    /** Pozycje mające którąś z cech profilu, od największej liczby wspólnych cech ([fullKeys] = `PRZESTRZEŃ:klucz`). */
    @Query(
        "SELECT itemId FROM vod_feature_index WHERE (namespace || ':' || featureKey) IN (:fullKeys) " +
            "GROUP BY itemId ORDER BY COUNT(*) DESC, itemId LIMIT :limit",
    )
    suspend fun candidateItems(
        fullKeys: List<String>,
        limit: Int,
    ): List<String>

    @Query(
        "SELECT DISTINCT itemId FROM vod_feature_index WHERE namespace = 'GENRE' AND featureKey NOT IN (:keys) LIMIT :limit",
    )
    suspend fun itemsOutsideGenres(
        keys: List<String>,
        limit: Int,
    ): List<String>

    @Query("SELECT namespace, featureKey, COUNT(*) AS items FROM vod_feature_index GROUP BY namespace, featureKey")
    suspend fun featureCounts(): List<FeatureCount>

    @Query("SELECT COUNT(DISTINCT itemId) FROM vod_feature_index")
    suspend fun indexedItems(): Int

    @Query(
        "SELECT v.id AS id, v.genre AS genre, v.`cast` AS `cast`, m.tmdbGenre AS tmdbGenre, m.tmdbCast AS tmdbCast, " +
            "m.director AS director, m.tmdbMatch AS tmdbMatch FROM vod_items v " +
            "LEFT JOIN vod_meta m ON m.itemId = v.id ORDER BY v.rowid LIMIT :limit OFFSET :offset",
    )
    suspend fun featureSources(
        limit: Int,
        offset: Int,
    ): List<VodFeatureSource>

    @Query(
        "SELECT v.id AS id, v.genre AS genre, v.`cast` AS `cast`, m.tmdbGenre AS tmdbGenre, m.tmdbCast AS tmdbCast, " +
            "m.director AS director, m.tmdbMatch AS tmdbMatch FROM vod_items v " +
            "JOIN vod_meta m ON m.itemId = v.id WHERE m.fetchedMs > :sinceMs OR m.deepMs > :sinceMs",
    )
    suspend fun featureSourcesChangedSince(sinceMs: Long): List<VodFeatureSource>

    @Query(
        "SELECT v.id AS id, v.genre AS genre, v.`cast` AS `cast`, m.tmdbGenre AS tmdbGenre, m.tmdbCast AS tmdbCast, " +
            "m.director AS director, m.tmdbMatch AS tmdbMatch FROM vod_items v " +
            "LEFT JOIN vod_meta m ON m.itemId = v.id WHERE v.id = :itemId",
    )
    suspend fun featureSource(itemId: String): VodFeatureSource?

    @Query("SELECT COUNT(*) FROM vod_items")
    suspend fun vodItemCount(): Int

    @Query("SELECT COALESCE(SUM(lastRefreshMs), 0) FROM providers")
    suspend fun catalogStamp(): Long

    @Query("SELECT id FROM vod_items WHERE id IN (:itemIds)")
    suspend fun existingVodItems(itemIds: List<String>): List<String>

    /** Pozycje, których pas rekomendacji nie powinien powtarzać: zaczęte, z Mojej listy, obejrzane, ukryte. */
    @Query(
        "SELECT itemId FROM vod_state WHERE myList = 1 OR positionMs > 0 " +
            "UNION SELECT e.seriesId FROM vod_episodes e JOIN vod_state s ON s.itemId = e.id WHERE s.positionMs > 0 " +
            "UNION SELECT itemId FROM watch_sessions WHERE profileId = :profileId AND contentKind = 'MOVIE' " +
            "AND evidenceClass = 'POSITIVE' " +
            "UNION SELECT seriesId FROM watch_sessions WHERE profileId = :profileId AND seriesId IS NOT NULL " +
            "UNION SELECT targetId FROM explicit_feedback WHERE profileId = :profileId AND targetType = 'VOD' " +
            "AND feedback IN ('HIDE', 'DISLIKE')",
    )
    suspend fun excludedVodItems(profileId: String): List<String>

    @RawQuery
    suspend fun epgSlots(query: SupportSQLiteQuery): List<EpgSlotRow>

    @Query(
        "SELECT c.id AS id, c.name AS name, c.providerId AS providerId, COALESCE(f.hidden, 0) AS hidden " +
            "FROM channels c LEFT JOIN channel_flags f ON f.channelId = c.id WHERE c.id IN (:ids)",
    )
    suspend fun inventoryChannels(ids: List<String>): List<InventoryChannelRow>

    /** Usuwa wszystko, czego nauczyła się personalizacja. Katalog, Moja lista i postęp oglądania zostają. */
    @Transaction
    suspend fun clearLearned() {
        clearSessions()
        clearFeatures()
        clearFeedback()
        clearSnapshots()
        clearMeta()
    }
}

@Dao
@Suppress("TooManyFunctions") // DAO toru sportowego: jedna funkcja = jedno zapytanie
interface SportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putTeam(team: TeamEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAliases(aliases: List<TeamAliasEntity>)

    @Query("DELETE FROM team_aliases WHERE teamId = :teamId")
    suspend fun deleteAliases(teamId: String)

    @Query("DELETE FROM team_catalog WHERE teamId = :teamId")
    suspend fun deleteTeam(teamId: String)

    @Query("SELECT * FROM team_catalog ORDER BY displayName")
    suspend fun teams(): List<TeamEntity>

    @Query("SELECT * FROM team_catalog ORDER BY displayName")
    fun observeTeams(): Flow<List<TeamEntity>>

    @Query("SELECT * FROM team_aliases")
    suspend fun aliases(): List<TeamAliasEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putFollow(follow: TeamFollowEntity)

    @Query("DELETE FROM team_follows WHERE profileId = :profileId AND teamId = :teamId")
    suspend fun deleteFollow(
        profileId: String,
        teamId: String,
    )

    @Query("SELECT * FROM team_follows WHERE profileId = :profileId")
    suspend fun follows(profileId: String): List<TeamFollowEntity>

    @Query("SELECT * FROM team_follows WHERE profileId = :profileId")
    fun observeFollows(profileId: String): Flow<List<TeamFollowEntity>>

    @Query("DELETE FROM team_follows")
    suspend fun clearFollows()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putEvents(events: List<SportEventEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAvailability(rows: List<EventAvailabilityEntity>)

    @Query("SELECT * FROM sport_events")
    suspend fun events(): List<SportEventEntity>

    @Query("SELECT * FROM event_availability")
    suspend fun availability(): List<EventAvailabilityEntity>

    @Query("DELETE FROM event_availability")
    suspend fun clearAvailability()

    @Query("DELETE FROM sport_events")
    suspend fun clearEvents()

    /** Atomowa podmiana wydarzeń i dowodów dostępności: Home nigdy nie widzi połowy stanu. */
    @Transaction
    suspend fun replaceEvents(
        events: List<SportEventEntity>,
        availability: List<EventAvailabilityEntity>,
    ) {
        clearAvailability()
        clearEvents()
        if (events.isNotEmpty()) putEvents(events)
        if (availability.isNotEmpty()) putAvailability(availability)
    }

    /** Czyści wybory i wydarzenia sportowe (drużyny dodane ręcznie też). */
    @Transaction
    suspend fun clearSport() {
        clearFollows()
        clearAvailability()
        clearEvents()
        clearUserTeams()
    }

    @Query("DELETE FROM team_catalog")
    suspend fun clearUserTeams()
}
