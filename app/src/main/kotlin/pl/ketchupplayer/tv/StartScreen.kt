package pl.ketchupplayer.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.ui.touchClickable

/** Pusty ekran startowy Fazy 0 (marża overscan 48×27 dp wg skilla android-tv-ui). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StartScreen(
    onOpenEpgSources: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(KetchupTheme.colors.background)
                .padding(horizontal = 48.dp, vertical = 27.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 48.sp,
            lineHeight = 56.sp,
            textAlign = TextAlign.Center,
            color = KetchupTheme.colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.start_tagline),
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            color = KetchupTheme.colors.textSecondary,
        )
        Button(
            onClick = onOpenEpgSources,
            modifier = Modifier.padding(top = 24.dp).touchClickable(onOpenEpgSources),
        ) {
            Text(stringResource(R.string.start_epg_sources))
        }
        Button(
            onClick = onOpenAppearance,
            modifier = Modifier.padding(top = 12.dp).touchClickable(onOpenAppearance),
        ) {
            Text(stringResource(R.string.start_appearance))
        }
    }
}
