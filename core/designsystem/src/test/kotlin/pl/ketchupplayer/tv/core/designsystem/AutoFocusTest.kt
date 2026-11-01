package pl.ketchupplayer.tv.core.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regresja: strony osadzone w panelu Ustawień (opcje po prawej) kradły fokus z listy kategorii po lewej przy każdej
 * zmianie kategorii, więc DÓŁ/GÓRA nie przeglądały listy. Osadzona strona nie ustawia fokusu sama.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class AutoFocusTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun zwyklyEkranUstawiaFokusPoUlozeniu() {
        compose.setContent {
            Box(
                Modifier
                    .size(40.dp)
                    .testTag("cel")
                    .requestFocusWhenPlaced()
                    .focusable(),
            )
        }
        compose.waitForIdle()
        compose.onNodeWithTag("cel").assertIsFocused()
    }

    @Test
    fun stronaOsadzonaWPaneluNieKradnieFokusu() {
        compose.setContent {
            CompositionLocalProvider(LocalBreadcrumb provides "Ustawienia") {
                Box(
                    Modifier
                        .size(40.dp)
                        .testTag("cel")
                        .requestFocusWhenPlaced()
                        .focusable(),
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("cel").assertIsNotFocused()
    }
}
