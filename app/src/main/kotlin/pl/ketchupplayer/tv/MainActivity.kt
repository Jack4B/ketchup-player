package pl.ketchupplayer.tv

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.LocalGlassBlur
import pl.ketchupplayer.tv.core.designsystem.ProvideUiScale
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    private val keyGuard = KeyRepeatGuard()

    /** Zalegające lub zbyt gęste powtórzenia klawisza są pomijane (pilot nie „przeskakuje” o kilka pól). */
    @SuppressLint("RestrictedApi") // androidx.core oznacza ten punkt jako wewnętrzny, ale to publiczny Activity API
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val navigation = event.keyCode in NAV_KEYS
        if (!navigation || event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        val now = SystemClock.uptimeMillis()
        val drop = keyGuard.shouldDrop(event.keyCode, event.repeatCount, event.eventTime, now)
        if (BuildConfig.DEBUG) logKey(event, now, drop)
        return if (drop) true else super.dispatchKeyEvent(event)
    }

    private val lastRawByKey = HashMap<Int, Long>()

    /** Diagnostyka pilota (debug): każde zdarzenie nawigacji, także odrzucone, w stałym formacie [KeyLog]. */
    private fun logKey(
        event: KeyEvent,
        now: Long,
        dropped: Boolean,
    ) {
        val gap = lastRawByKey[event.keyCode]?.let { now - it }
        lastRawByKey[event.keyCode] = now
        val age = now - event.eventTime
        val record =
            KeyLog.Record(now, event.keyCode, event.repeatCount, age, gap, event.deviceId, event.source, dropped)
        // INFO, bo RedactingTree przepuszcza tylko INFO i wyżej (wcześniejsze Timber.d było niewidoczne w logcat).
        Timber.tag(KeyLog.TAG).i(KeyLog.line(record))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { viewModel.remoteDebounceMs.collect { keyGuard.debounceMs = it.toLong() } }
        setContent {
            val appearance by viewModel.appearance.collectAsStateWithLifecycle()
            KetchupTheme(appearance.theme, appearance.effectiveAccent, customHue = appearance.customHue) {
                ProvideUiScale(appearance.uiScale) {
                    CompositionLocalProvider(LocalGlassBlur provides appearance.glassBlur) {
                        AppNavigation(onExit = { finish() })
                        AutoGlassProbe(appearance.glassBlurDecided, viewModel::applyGlassVerdict)
                    }
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
