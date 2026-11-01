package pl.ketchupplayer.tv.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ikony szyny nawigacji. Wszystkie rysowane z jednej siatki 24×24, kreska 2, zakończenia okrągłe (jak na makiecie v4),
 * dzięki czemu mają tę samą wagę i rozmiar optyczny. Bez zależności od pakietów ikon.
 */
enum class RailIcon(
    internal val paths: List<String>,
) {
    PROFILE(listOf("M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z", "M4 21c0-4 4-6 8-6s8 2 8 6")),
    SEARCH(listOf("M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14z", "M21 21l-5-5")),
    HOME(listOf("M3 11l9-8 9 8", "M5 10v10h14V10")),
    TV(listOf("M3 5h18v12H3z", "M8 21h8", "M12 17v4")),
    GUIDE(listOf("M4 5h16v15H4z", "M4 10h16", "M8 3v4", "M16 3v4")),
    MOVIES(listOf("M4 4h16v16H4z", "M4 9h16", "M4 15h16", "M9 4v16", "M15 4v16")),
    SERIES(listOf("M3 7h18v13H3z", "M8 3l4 4 4-4")),
    FAVORITES(listOf("M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z")),
    SETTINGS(
        listOf(
            "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z",
            "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5" +
                "V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1" +
                "a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0" +
                "-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1" +
                "a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9" +
                "a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z",
        ),
    ),
}

private const val GRID = 24f
private const val STROKE_WIDTH = 2f

@Composable
fun RailIconView(
    icon: RailIcon,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
) {
    val paths: List<Path> = remember(icon) { icon.paths.map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier.size(size)) {
        val factor = this.size.minDimension / GRID
        val stroke = Stroke(width = STROKE_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round)
        scale(factor, factor, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            paths.forEach { drawPath(it, color, style = stroke) }
        }
    }
}
