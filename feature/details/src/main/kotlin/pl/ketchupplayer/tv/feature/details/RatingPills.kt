package pl.ketchupplayer.tv.feature.details

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pl.ketchupplayer.tv.core.data.metadata.Ratings
import pl.ketchupplayer.tv.core.designsystem.InfoPill
import java.util.Locale

/**
 * Oceny w pigułkach: IMDb, TMDB i Rotten Tomatoes z zewnętrznych źródeł. Bez nich zostaje ocena od dostawcy listy
 * (gwiazdka), a przy braku wszystkiego nic się nie pokazuje.
 */
@Composable
internal fun RatingPills(
    ratings: Ratings?,
    providerRating: Double?,
) {
    val r = ratings ?: Ratings.NONE
    r.imdb?.let { InfoPill(stringResource(R.string.details_rating_imdb, score(it)), true) }
    r.tmdb?.let { InfoPill(stringResource(R.string.details_rating_tmdb, score(it)), r.imdb == null) }
    r.rtPercent?.let { InfoPill(stringResource(R.string.details_rating_rt, it)) }
    if (r.isEmpty) providerRating?.takeIf { it > 0.0 }?.let { InfoPill("★ " + score(it), true) }
}

private fun score(value: Double): String = String.format(Locale.forLanguageTag("pl"), "%.1f", value)
