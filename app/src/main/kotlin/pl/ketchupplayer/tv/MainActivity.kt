package pl.ketchupplayer.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import pl.ketchupplayer.tv.feature.settings.EpgSourcesRoute

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Tymczasowa nawigacja Fazy 0; NavHost z kontraktami tras w core:ui pojawi się z pierwszymi ekranami.
            var epgSources by rememberSaveable { mutableStateOf(false) }
            if (epgSources) {
                EpgSourcesRoute(onBack = { epgSources = false })
            } else {
                StartScreen(onOpenEpgSources = { epgSources = true })
            }
        }
    }
}
