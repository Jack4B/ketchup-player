package pl.ketchupplayer.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.feature.settings.AppearanceRoute
import pl.ketchupplayer.tv.feature.settings.EpgSourcesRoute

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            val appearance by viewModel.appearance.collectAsStateWithLifecycle()
            KetchupTheme(appearance.theme, appearance.effectiveAccent) {
                // Tymczasowa nawigacja Fazy 0; NavHost z kontraktami tras w core:ui pojawi się z pierwszymi ekranami.
                var screen by rememberSaveable { mutableStateOf("start") }
                when (screen) {
                    "epg" -> EpgSourcesRoute(onBack = { screen = "start" })
                    "look" -> AppearanceRoute(onBack = { screen = "start" })
                    else ->
                        StartScreen(
                            onOpenEpgSources = { screen = "epg" },
                            onOpenAppearance = { screen = "look" },
                        )
                }
            }
        }
    }
}
