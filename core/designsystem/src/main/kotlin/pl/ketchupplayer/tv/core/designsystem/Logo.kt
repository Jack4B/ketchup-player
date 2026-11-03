package pl.ketchupplayer.tv.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

/** Mała ikona aplikacji (znaczek w zaokrąglonym kwadracie) — awatar na szynie nawigacji. */
@Composable
fun KetchupLogoApp(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.logo_app), null, modifier, contentScale = ContentScale.Fit)
}

/** Szerokie logo „Ketchup Player” (splash, kreator pierwszego uruchomienia). */
@Composable
fun KetchupLogoWide(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.logo_wide), null, modifier, contentScale = ContentScale.Fit)
}
