package pl.ketchupplayer.tv.core.data.metadata

import javax.inject.Inject
import javax.inject.Singleton

/** Adresy API metadanych. Zmienne tylko po to, by testy E2E mogły wskazać lokalny serwer zamiast internetu. */
@Singleton
class MetadataEndpoints
    @Inject
    constructor() {
        @Volatile var tmdb: String = "https://api.themoviedb.org/3"

        @Volatile var omdb: String = "https://www.omdbapi.com"

        @Volatile var trakt: String = "https://api.trakt.tv"

        @Volatile var mdblist: String = "https://api.mdblist.com"

        @Volatile var wikipedia: String = "https://pl.wikipedia.org"
    }
