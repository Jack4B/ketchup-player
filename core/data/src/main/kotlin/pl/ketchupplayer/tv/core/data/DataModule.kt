package pl.ketchupplayer.tv.core.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pl.ketchupplayer.tv.core.data.appearance.AppearanceRepository
import pl.ketchupplayer.tv.core.data.appearance.DataStoreAppearanceRepository
import pl.ketchupplayer.tv.core.data.epg.DataStoreEpgSourcesRepository
import pl.ketchupplayer.tv.core.data.epg.EpgSourcesRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindEpgSources(impl: DataStoreEpgSourcesRepository): EpgSourcesRepository

    @Binds
    abstract fun bindAppearance(impl: DataStoreAppearanceRepository): AppearanceRepository
}
