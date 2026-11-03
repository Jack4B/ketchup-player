package pl.ketchupplayer.tv.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

/**
 * Pigułka z informacją o aktywnej liście („Lista „Domowa” · połączenia 1 z 2”), pokazywana w nagłówku ekranów (v5).
 * [warn] = limit połączeń wyczerpany: kropka w kolorze ostrzeżenia, a tekst i tak mówi to słowami (nie samym kolorem).
 */
data class ListBadgeUi(
    val text: String,
    val warn: Boolean = false,
)

/** Pigułka listy dla wszystkich nagłówków; null = brak listy albo brak danych (nic nie rysujemy). */
val LocalListBadge = staticCompositionLocalOf<ListBadgeUi?> { null }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ListBadgePill(
    badge: ListBadgeUi,
    modifier: Modifier = Modifier,
) {
    val c = KetchupTheme.colors
    val shape = RoundedCornerShape(PILL_RADIUS)
    val accent = if (badge.warn) Color(Palettes.WARNING_AMBER) else c.accent
    Row(
        modifier
            .background(c.surface2, shape)
            .border(1.dp, accent, shape)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).background(accent, CircleShape))
        Text(
            badge.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val PILL_RADIUS = 999.dp
