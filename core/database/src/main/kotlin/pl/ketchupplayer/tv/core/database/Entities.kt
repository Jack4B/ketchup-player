package pl.ketchupplayer.tv.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Dostawca bez sekretów: hasło/token jest w `SecretStore` pod kluczem [secretRef]. */
@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val url: String,
    val secretRef: String? = null,
    val epgUrl: String? = null,
    val sortOrder: Int = 0,
    val lastRefreshMs: Long? = null,
    val lastError: String? = null,
)

@Entity(
    tableName = "channels",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("providerId"), Index("normName"), Index("type", "groupName")],
)
data class ChannelEntity(
    val id: String,
    val providerId: String,
    val name: String,
    val normName: String,
    val number: Int?,
    val epgId: String?,
    val logo: String?,
    val groupName: String?,
    val type: String,
    val streamUrl: String?,
    val userAgent: String?,
)

/** Ulubione/ukryte/pozycja kanału; osobna tabela, żeby odświeżenie listy nie kasowało wyborów użytkownika. */
@Entity(tableName = "channel_flags", primaryKeys = ["channelId"])
data class ChannelFlagEntity(
    val channelId: String,
    val favorite: Boolean = false,
    val hidden: Boolean = false,
    val lastWatchedMs: Long? = null,
)

/** Audycje przewodnika EPG (aktualne). Zapełniane atomową podmianą z [EpgStagingEntity]. */
@Entity(tableName = "epg_programmes", indices = [Index("channelId", "startMs"), Index("endMs")])
data class EpgProgrammeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String,
    val startMs: Long,
    val endMs: Long,
    val title: String,
    val description: String?,
    val category: String?,
)

/** Tabela robocza importu: UI jej nie czyta, więc import nie pokazuje „połowy” przewodnika. */
@Entity(tableName = "epg_staging")
data class EpgStagingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String,
    val startMs: Long,
    val endMs: Long,
    val title: String,
    val description: String?,
    val category: String?,
)
