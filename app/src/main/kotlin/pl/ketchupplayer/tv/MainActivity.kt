package pl.ketchupplayer.tv

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.LocalGlassBlur

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    private val keyGuard = KeyRepeatGuard()

    /** Zalegające lub zbyt gęste powtórzenia klawisza są pomijane (pilot nie „przeskakuje” o kilka pól). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val navigation = event.keyCode in NAV_KEYS
        return if (navigation &&
            event.action == KeyEvent.ACTION_DOWN &&
            keyGuard.shouldDrop(event.repeatCount, event.eventTime, SystemClock.uptimeMillis())
        ) {
            true
        } else {
            super.dispatchKeyEvent(event)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            val appearance by viewModel.appearance.collectAsStateWithLifecycle()
            KetchupTheme(appearance.theme, appearance.effectiveAccent) {
                CompositionLocalProvider(LocalGlassBlur provides appearance.glassBlur) {
                    AppNavigation(onExit = { finish() })
                }
            }
        }
    }

    private companion object {
        val NAV_KEYS =
            setOf(
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
            )
    }
}
