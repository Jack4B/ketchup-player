package pl.ketchupplayer.tv.core.data

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import pl.ketchupplayer.tv.core.common.Clock
import pl.ketchupplayer.tv.core.data.appearance.AppearanceRepository
import pl.ketchupplayer.tv.core.data.appearance.DataStoreAppearanceRepository
import pl.ketchupplayer.tv.core.data.catalog.CatalogRepository
import pl.ketchupplayer.tv.core.data.catalog.RoomCatalogRepository
import pl.ketchupplayer.tv.core.data.epg.DataStoreEpgSourcesRepository
import pl.ketchupplayer.tv.core.data.epg.EpgRepository
import pl.ketchupplayer.tv.core.data.epg.EpgSourcesRepository
import pl.ketchupplayer.tv.core.data.epg.RoomEpgRepository
import pl.ketchupplayer.tv.core.data.playback.DataStoreFailoverSettingsRepository
import pl.ketchupplayer.tv.core.data.playback.FailoverSettingsRepository
import pl.ketchupplayer.tv.core.database.ChannelDao
import pl.ketchupplayer.tv.core.database.ChannelFlagDao
import pl.ketchupplayer.tv.core.database.EpgDao
import pl.ketchupplayer.tv.core.database.KetchupDatabase
import pl.ketchupplayer.tv.core.database.ProviderDao
import pl.ketchupplayer.tv.core.network.NetworkClients
import pl.ketchupplayer.tv.core.network.OkHttpStreamFetcher
import pl.ketchupplayer.tv.core.network.OkHttpTextFetcher
import pl.ketchupplayer.tv.core.network.StreamFetcher
import pl.ketchupplayer.tv.core.network.TextFetcher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindEpgSources(impl: DataStoreEpgSourcesRepository): EpgSourcesRepository

    @Binds
    abstract fun bindAppearance(impl: DataStoreAppearanceRepository): AppearanceRepository

    @Binds
    abstract fun bindFailoverSettings(impl: DataStoreFailoverSettingsRepository): FailoverSettingsRepository

    @Binds
    abstract fun bindCatalog(impl: RoomCatalogRepository): CatalogRepository

    @Binds
    abstract fun bindVod(
        impl: pl.ketchupplayer.tv.core.data.vod.RoomVodRepository,
    ): pl.ketchupplayer.tv.core.data.vod.VodRepository

    @Binds
    abstract fun bindOnboarding(
        impl: pl.ketchupplayer.tv.core.data.onboarding.DataStoreOnboardingRepository,
    ): pl.ketchupplayer.tv.core.data.onboarding.OnboardingRepository

    @Binds
    abstract fun bindEpg(impl: RoomEpgRepository): EpgRepository

    companion object {
        @Provides
        @Singleton
        fun database(
            @ApplicationContext context: Context,
        ): KetchupDatabase = Room.databaseBuilder(context, KetchupDatabase::class.java, "ketchup.db").build()

        @Provides
        fun providerDao(db: KetchupDatabase): ProviderDao = db.providers()

        @Provides
        fun channelDao(db: KetchupDatabase): ChannelDao = db.channels()

        @Provides
        fun channelFlagDao(db: KetchupDatabase): ChannelFlagDao = db.channelFlags()

        @Provides
        @Singleton
        fun textFetcher(): TextFetcher = OkHttpTextFetcher(NetworkClients.stream())

        @Provides
        fun vodDao(db: KetchupDatabase): pl.ketchupplayer.tv.core.database.VodDao = db.vod()

        @Provides
        fun epgDao(db: KetchupDatabase): EpgDao = db.epg()

        @Provides
        @Singleton
        fun streamFetcher(): StreamFetcher = OkHttpStreamFetcher(NetworkClients.stream())

        @Provides
        fun clock(): Clock = Clock.System
    }
}
