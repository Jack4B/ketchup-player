package pl.ketchupplayer.tv

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pl.ketchupplayer.tv.core.data.metadata.ApiKeys
import pl.ketchupplayer.tv.core.data.metadata.DefaultApiKeys

/** Klucze metadanych wbudowane w kompilację (sekrety CI); puste w kompilacji lokalnej. */
@Module
@InstallIn(SingletonComponent::class)
object MetadataDefaultsModule {
    @Provides
    fun defaultKeys(): DefaultApiKeys = DefaultApiKeys { ApiKeys(BuildConfig.TMDB_KEY, BuildConfig.OMDB_KEY) }
}
