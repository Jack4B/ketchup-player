package pl.ketchupplayer.tv.core.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regresja (emulator, biegi #243-#246): sekcja pod panelem Ustawień jest tylko tłem, ale jej elementy brały fokus,
 * więc PRAWO z kategorii lądowało na ukrytym plakacie, a na panelu nie było żadnego zaznaczenia.
 * [LocalFocusBlocked] wyłącza fokus w całym poddrzewie [FocusItem]. Test gryzie: bez blokady (canFocus = true) pada.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class FocusBlockedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Composable
    private fun Item(blocked: Boolean) {
        KetchupTheme {
            CompositionLocalProvider(LocalFocusBlocked provides blocked) {
                FocusItem(onClick = {}, modifier = Modifier.testTag("a").requestFocusWhenPlaced()) { Text("A") }
            }
        }
    }

    @Test
    fun zablokowanyElementNieBierzeFokusu() {
        compose.setContent { Item(blocked = true) }
        compose.waitForIdle()
        compose.onNodeWithTag("a").assertIsNotFocused()
    }
}
