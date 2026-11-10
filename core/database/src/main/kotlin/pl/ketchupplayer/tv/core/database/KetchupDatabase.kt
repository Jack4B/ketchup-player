package pl.ketchupplayer.tv.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProviderEntity::class,
        ChannelEntity::class,
        ChannelFlagEntity::class,
        EpgProgrammeEntity::class,
        EpgStagingEntity::class,
        VodItemEntity::class,
        EpisodeEntity::class,
        VodStateEntity::class,
        VodSeenEntity::class,
        VodMetaEntity::class,
    ],
    version = 9,
    autoMigrations =
        [
            AutoMigration(
                from = 1,
                to = 2,
            ), AutoMigration(
                from = 2,
                to = 3,
            ), AutoMigration(
                from = 3,
                to = 4,
            ), AutoMigration(
                from = 4,
                to = 5,
            ), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7), AutoMigration(from = 7, to = 8),
            AutoMigration(from = 8, to = 9),
        ],
    exportSchema = true,
)
abstract class KetchupDatabase : RoomDatabase() {
    abstract fun providers(): ProviderDao

    abstract fun channels(): ChannelDao

    abstract fun channelFlags(): ChannelFlagDao

    abstract fun epg(): EpgDao

    abstract fun vod(): VodDao
}
