package pl.ketchupplayer.tv.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Lokalna sesja oglądania (personalizacja). Bez adresów strumieni i danych dostępu: tylko id z katalogu, czasy
 * i liczniki. Bez klucza obcego do kanałów i VOD, żeby odświeżenie listy nie kasowało historii; osierocone wpisy
 * usuwa retencja.
 */
@Entity(
    tableName = "watch_sessions",
    indices = [
        Index(value = ["profileId", "dedupKey"], unique = true),
        Index("profileId", "status"),
        Index("profileId", "itemId", "lastEventAtMs"),
        Index("startedAtMs"),
    ],
)
data class WatchSessionEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val providerId: String,
    val itemId: String,
    val contentKind: String,
    val seriesId: String?,
    val programmeStartMs: Long?,
    val programmeEndMs: Long?,
    val programmeTitleNorm: String?,
    val startedAtMs: Long,
    val lastEventAtMs: Long,
    val endedAtMs: Long?,
    val status: String,
    val activeWatchMs: Long,
    val wallWatchMs: Long,
    val pauseMs: Long,
    val bufferMs: Long,
    val seekCount: Int,
    val rebufferCount: Int,
    val maxPositionMs: Long,
    val durationMs: Long?,
    val startReason: String,
    val endReason: String?,
    val evidenceClass: String,
    val sourceVersion: Int,
    val dedupKey: String,
    @ColumnInfo(defaultValue = "0") val appliedStrength: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val incognito: Boolean = false,
)

/** Waga cechy w profilu preferencji (gatunek, reżyser, obsada, drużyna, kanał). */
@Entity(
    tableName = "preference_features",
    primaryKeys = ["profileId", "namespace", "featureKey"],
    indices = [Index("profileId", "namespace", "decayedWeight")],
)
data class PreferenceFeatureEntity(
    val profileId: String,
    val namespace: String,
    val featureKey: String,
    val displayLabel: String,
    val rawWeight: Double,
    val decayedWeight: Double,
    val evidence: Double,
    val confidence: Double,
    val lastUpdatedMs: Long,
    val provenance: String,
)

/** Jawna ocena użytkownika (LIKE, HIDE...) pozycji katalogu albo drużyny. */
@Entity(tableName = "explicit_feedback", primaryKeys = ["profileId", "targetType", "targetId", "feedback"])
data class ExplicitFeedbackEntity(
    val profileId: String,
    val targetType: String,
    val targetId: String,
    val feedback: String,
    val createdAtMs: Long,
)

/** Drużyna dodana przez użytkownika (wbudowany słownik nazw jest w kodzie: `BundledTeams`). */
@Entity(tableName = "team_catalog")
data class TeamEntity(
    @PrimaryKey val teamId: String,
    val displayName: String,
    val normName: String,
    val competitionKey: String?,
    val country: String?,
    val source: String,
    val sourceVersion: Int,
)

@Entity(
    tableName = "team_aliases",
    primaryKeys = ["teamId", "aliasNorm"],
    foreignKeys = [
        ForeignKey(
            entity = TeamEntity::class,
            parentColumns = ["teamId"],
            childColumns = ["teamId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("aliasNorm")],
)
data class TeamAliasEntity(
    val teamId: String,
    val aliasNorm: String,
    val language: String?,
    val ambiguousGroup: String?,
    @ColumnInfo(defaultValue = "0") val weak: Boolean = false,
)

/** Stan drużyny w profilu: propozycja, obserwowana albo odrzucona. Obserwowanie zawsze potwierdza użytkownik. */
@Entity(tableName = "team_follows", primaryKeys = ["profileId", "teamId"])
data class TeamFollowEntity(
    val profileId: String,
    val teamId: String,
    val state: String,
    val evidenceCount: Int,
    val lastEvidenceMs: Long?,
    val reasonSummary: String?,
    val updatedAtMs: Long,
)

/** Wydarzenie sportowe wyprowadzone z EPG. Czasy w UTC; `eventId` jest zarazem kluczem deduplikacji. */
@Entity(tableName = "sport_events", indices = [Index("kickoffUtcMs"), Index("status")])
data class SportEventEntity(
    @PrimaryKey val eventId: String,
    val competitionKey: String?,
    val homeTeamId: String?,
    val awayTeamId: String?,
    val titleRaw: String,
    val kickoffUtcMs: Long,
    val endUtcMs: Long?,
    val status: String,
    val isReplay: Boolean,
    val sourceKind: String,
    val sourceVersion: Int,
    val scheduleUpdatedAtMs: Long,
)

/** Dowód, że wydarzenie jest na kanale z listy użytkownika. `abstain` = nie wolno wskazać kanału. */
@Entity(
    tableName = "event_availability",
    primaryKeys = ["eventId", "channelId"],
    foreignKeys = [
        ForeignKey(
            entity = SportEventEntity::class,
            parentColumns = ["eventId"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class EventAvailabilityEntity(
    val eventId: String,
    val channelId: String,
    val providerId: String?,
    val matchMethod: String,
    val confidence: Double,
    val evidenceAtMs: Long,
    val programmeStartMs: Long,
    val programmeEndMs: Long,
    val abstain: Boolean,
)

/** Gotowy pas Home (JSON z identyfikatorami i etykietami, bez adresów); ekran tylko go czyta. */
@Entity(tableName = "home_snapshots", primaryKeys = ["profileId", "lane"])
data class HomeSnapshotEntity(
    val profileId: String,
    val lane: String,
    val payloadJson: String,
    val builtAtMs: Long,
    val profileVersion: Long,
    val catalogVersion: Long,
)

@Entity(tableName = "personalization_meta", primaryKeys = ["profileId", "key"])
data class PersonalizationMetaEntity(
    val profileId: String,
    val key: String,
    val value: String,
    val updatedAtMs: Long,
)

/** Cechy pozycji VOD wyliczone z metadanych: indeks do rankingu, odświeżany po zmianie katalogu lub metadanych. */
@Entity(
    tableName = "vod_feature_index",
    primaryKeys = ["itemId", "namespace", "featureKey"],
    indices = [Index("namespace", "featureKey")],
)
data class VodFeatureEntity(
    val itemId: String,
    val namespace: String,
    val featureKey: String,
    val displayLabel: String,
    val weightOnItem: Double,
    val provenance: String,
    val confidence: Double,
    val sourceVersion: Int,
)
