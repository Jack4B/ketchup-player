package pl.ketchupplayer.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** Pusty ekran startowy Fazy 0 (marża overscan 48×27 dp wg skilla android-tv-ui). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StartScreen() {
    MaterialTheme {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0B1220))
                    .padding(horizontal = 48.dp, vertical = 27.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = stringResource(R.string.app_name), fontSize = 48.sp, color = Color.White)
            Text(text = stringResource(R.string.start_tagline), fontSize = 20.sp, color = Color(0xFFB8C0CC))
        }
    }
}
