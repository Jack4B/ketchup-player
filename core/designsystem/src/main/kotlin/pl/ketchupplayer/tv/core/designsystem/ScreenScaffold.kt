package pl.ketchupplayer.tv.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

/**
 * Szkielet ekranu z marginesami zależnymi od urządzenia:
 * TV = overscan 48×27 dp (konspekt §4.1), telefon = systemowe wcięcia (paski, wycięcie) + 16 dp.
 * Zawartość przewija się pionowo, więc nic nie ucieka poza ekran w pionie ani w poziomie.
 */
@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = KetchupTheme.colors
    val margins =
        when (KetchupTheme.device) {
            DeviceClass.TV -> Modifier.padding(horizontal = TV_MARGIN_H, vertical = TV_MARGIN_V)
            DeviceClass.PHONE ->
                Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp, vertical = 12.dp)
        }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(c.background)
                .then(margins)
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

private val TV_MARGIN_H = 48.dp
private val TV_MARGIN_V = 27.dp

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ScreenTitle(text: String) {
    val phone = KetchupTheme.device == DeviceClass.PHONE
    Text(text, fontSize = if (phone) 28.sp else 32.sp, color = KetchupTheme.colors.textPrimary)
}
