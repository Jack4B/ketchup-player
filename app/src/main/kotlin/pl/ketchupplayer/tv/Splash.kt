package pl.ketchupplayer.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import pl.ketchupplayer.tv.core.designsystem.KetchupLogoWide

/** Jak długo pokazywać ekran powitalny z logo po systemowym splashu; 0 = wcale (testy). */
interface SplashPolicy {
    val durationMs: Long
}

@Module
@InstallIn(SingletonComponent::class)
object SplashModule {
    @Provides
    fun splashPolicy(): SplashPolicy =
        object : SplashPolicy {
            override val durationMs = SPLASH_MS
        }

    private const val SPLASH_MS = 1_400L
}

/** Pełnoekranowe szerokie logo przez [durationMs] po starcie, potem znika (nie wraca po obrocie). */
@Composable
fun SplashOverlay(
    durationMs: Long,
    content: @Composable () -> Unit,
) {
    var visible by rememberSaveable { mutableStateOf(durationMs > 0) }
    LaunchedEffect(Unit) {
        if (visible) {
            delay(durationMs)
            visible = false
        }
    }
    Box(Modifier.fillMaxSize()) {
        content()
        AnimatedVisibility(visible, exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(SPLASH_BG), contentAlignment = Alignment.Center) {
                KetchupLogoWide(Modifier.fillMaxHeight(SPLASH_LOGO_HEIGHT).padding(24.dp))
            }
        }
    }
}

private val SPLASH_BG = Color(0xFF0E1017)
private const val SPLASH_LOGO_HEIGHT = 0.5f
