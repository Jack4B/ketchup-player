package pl.ketchupplayer.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.designsystem.DeviceClass
import pl.ketchupplayer.tv.core.designsystem.KetchupButton
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme

/**
 * Ekran startowy Fazy 0. TV: marża overscan 48×27 dp, przyciski wyśrodkowane. Telefon: wcięcia systemowe,
 * przyciski na całą szerokość (pion i poziom), przewijanie przy małej wysokości.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StartScreen(onNavigate: (Destination) -> Unit = {}) {
    val c = KetchupTheme.colors
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    val phone = KetchupTheme.device == DeviceClass.PHONE
    val margins =
        if (phone) {
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp, vertical = 16.dp)
        } else {
            Modifier.padding(horizontal = 48.dp, vertical = 27.dp)
        }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(c.background)
                .then(margins)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            fontSize = if (phone) 40.sp else 48.sp,
            lineHeight = if (phone) 48.sp else 56.sp,
            textAlign = TextAlign.Center,
            color = c.textPrimary,
        )
        Text(
            text = stringResource(R.string.start_tagline),
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            color = c.textSecondary,
        )
        val buttons = Modifier.padding(top = 4.dp).widthIn(max = 420.dp).fillMaxWidth(if (phone) 1f else 0.4f)
        val entries =
            listOf(
                R.string.start_live to Destination.LIVE,
                R.string.start_guide to Destination.GUIDE,
                R.string.start_providers to Destination.PROVIDERS,
                R.string.start_epg_sources to Destination.EPG_SOURCES,
                R.string.start_playback to Destination.PLAYBACK,
                R.string.start_appearance to Destination.APPEARANCE,
            )
        entries.forEachIndexed { i, (label, destination) ->
            KetchupButton(
                stringResource(label),
                { onNavigate(destination) },
                buttons,
                primary = i == 0,
                focusRequester = if (i == 0) first else null,
            )
        }
    }
}
