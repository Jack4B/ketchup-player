package pl.ketchupplayer.tv.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ProviderEntity::class, ChannelEntity::class, ChannelFlagEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class KetchupDatabase : RoomDatabase() {
    abstract fun providers(): ProviderDao

    abstract fun channels(): ChannelDao

    abstract fun channelFlags(): ChannelFlagDao
}
