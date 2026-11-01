package pl.ketchupplayer.tv.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Ikony szyny nawigacji (kontur 2 dp, jak na makietach v3), rysowane na Canvas: bez zależności od pakietów ikon. */
enum class RailIcon { PROFILE, SEARCH, HOME, TV, GUIDE, MOVIES, SERIES, FAVORITES, SETTINGS }

@Composable
fun RailIconView(
    icon: RailIcon,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    Canvas(modifier.size(size)) { draw(icon, color) }
}

private fun DrawScope.draw(
    icon: RailIcon,
    color: Color,
) {
    val w = size.minDimension
    val stroke = Stroke(width = w * STROKE_RATIO, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (icon) {
        RailIcon.PROFILE -> profile(w, color, stroke)
        RailIcon.SEARCH -> search(w, color, stroke)
        RailIcon.HOME -> home(w, color, stroke)
        RailIcon.TV -> tv(w, color, stroke)
        RailIcon.GUIDE -> guide(w, color, stroke)
        RailIcon.MOVIES -> movies(w, color, stroke)
        RailIcon.SERIES -> series(w, color, stroke)
        RailIcon.FAVORITES -> star(w, color, stroke)
        RailIcon.SETTINGS -> gear(w, color, stroke)
    }
}

private const val STROKE_RATIO = 0.085f

private fun DrawScope.profile(
    w: Float,
    c: Color,
    s: Stroke,
) {
    drawCircle(c, radius = w * 0.2f, center = Offset(w * 0.5f, w * 0.32f), style = s)
    val p =
        Path().apply {
            moveTo(w * 0.14f, w * 0.88f)
            cubicTo(w * 0.14f, w * 0.62f, w * 0.86f, w * 0.62f, w * 0.86f, w * 0.88f)
        }
    drawPath(p, c, style = s)
}

private fun DrawScope.search(
    w: Float,
    c: Color,
    s: Stroke,
) {
    drawCircle(c, radius = w * 0.3f, center = Offset(w * 0.43f, w * 0.43f), style = s)
    drawLine(c, Offset(w * 0.65f, w * 0.65f), Offset(w * 0.88f, w * 0.88f), s.width, StrokeCap.Round)
}

private fun DrawScope.home(
    w: Float,
    c: Color,
    s: Stroke,
) {
    val p =
        Path().apply {
            moveTo(w * 0.1f, w * 0.48f)
            lineTo(w * 0.5f, w * 0.14f)
            lineTo(w * 0.9f, w * 0.48f)
            moveTo(w * 0.2f, w * 0.42f)
            lineTo(w * 0.2f, w * 0.86f)
            lineTo(w * 0.8f, w * 0.86f)
            lineTo(w * 0.8f, w * 0.42f)
        }
    drawPath(p, c, style = s)
    drawLine(c, Offset(w * 0.42f, w * 0.86f), Offset(w * 0.42f, w * 0.62f), s.width, StrokeCap.Round)
    drawLine(c, Offset(w * 0.58f, w * 0.86f), Offset(w * 0.58f, w * 0.62f), s.width, StrokeCap.Round)
    drawLine(c, Offset(w * 0.42f, w * 0.62f), Offset(w * 0.58f, w * 0.62f), s.width, StrokeCap.Round)
}

private fun DrawScope.tv(
    w: Float,
    c: Color,
    s: Stroke,
) {
    drawRoundRect(c, Offset(w * 0.08f, w * 0.28f), Size(w * 0.84f, w * 0.58f), CornerRadius(w * 0.1f), style = s)
    drawLine(c, Offset(w * 0.34f, w * 0.1f), Offset(w * 0.5f, w * 0.26f), s.width, StrokeCap.Round)
    drawLine(c, Offset(w * 0.66f, w * 0.1f), Offset(w * 0.5f, w * 0.26f), s.width, StrokeCap.Round)
}

private fun DrawScope.guide(
    w: Float,
    c: Color,
    s: Stroke,
) {
    drawRoundRect(c, Offset(w * 0.1f, w * 0.18f), Size(w * 0.8f, w * 0.72f), CornerRadius(w * 0.1f), style = s)
    drawLine(c, Offset(w * 0.1f, w * 0.4f), Offset(w * 0.9f, w * 0.4f), s.width, StrokeCap.Round)
    drawLine(c, Offset(w * 0.3f, w * 0.08f), Offset(w * 0.3f, w * 0.26f), s.width, StrokeCap.Round)
    drawLine(c, Offset(w * 0.7f, w * 0.08f), Offset(w * 0.7f, w * 0.26f), s.width, StrokeCap.Round)
    drawLine(c, Offset(w * 0.26f, w * 0.56f), Offset(w * 0.5f, w * 0.56f), s.width, StrokeCap.Round)
    drawLine(c, Offset(w * 0.26f, w * 0.72f), Offset(w * 0.74f, w * 0.72f), s.width, StrokeCap.Round)
}

private fun DrawScope.movies(
    w: Float,
    c: Color,
    s: Stroke,
) {
    drawRoundRect(c, Offset(w * 0.18f, w * 0.1f), Size(w * 0.64f, w * 0.8f), CornerRadius(w * 0.06f), style = s)
    listOf(0.26f, 0.5f, 0.74f).forEach { y ->
        drawLine(c, Offset(w * 0.18f, w * y), Offset(w * 0.32f, w * y), s.width, StrokeCap.Round)
        drawLine(c, Offset(w * 0.68f, w * y), Offset(w * 0.82f, w * y), s.width, StrokeCap.Round)
    }
}

private fun DrawScope.series(
    w: Float,
    c: Color,
    s: Stroke,
) {
    drawRoundRect(c, Offset(w * 0.1f, w * 0.34f), Size(w * 0.8f, w * 0.56f), CornerRadius(w * 0.08f), style = s)
    drawLine(c, Offset(w * 0.22f, w * 0.2f), Offset(w * 0.78f, w * 0.2f), s.width, StrokeCap.Round)
    drawLine(c, Offset(w * 0.32f, w * 0.08f), Offset(w * 0.68f, w * 0.08f), s.width, StrokeCap.Round)
    val p =
        Path().apply {
            moveTo(w * 0.44f, w * 0.5f)
            lineTo(w * 0.62f, w * 0.62f)
            lineTo(w * 0.44f, w * 0.74f)
            close()
        }
    drawPath(p, c, style = s)
}

private fun DrawScope.star(
    w: Float,
    c: Color,
    s: Stroke,
) {
    val cx = w * 0.5f
    val cy = w * 0.54f
    val outer = w * 0.4f
    val inner = w * 0.17f
    val p = Path()
    for (i in 0 until STAR_POINTS * 2) {
        val r = if (i % 2 == 0) outer else inner
        val a = Math.PI * i / STAR_POINTS - Math.PI / 2
        val x = cx + (r * cos(a)).toFloat()
        val y = cy + (r * sin(a)).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, c, style = s)
}

private const val STAR_POINTS = 5

private fun DrawScope.gear(
    w: Float,
    c: Color,
    s: Stroke,
) {
    val cx = w * 0.5f
    val cy = w * 0.5f
    drawCircle(c, radius = w * 0.16f, center = Offset(cx, cy), style = s)
    val p = Path()
    val teeth = GEAR_TEETH
    for (i in 0 until teeth * 4) {
        val a = 2 * Math.PI * i / (teeth * 4)
        val r = if ((i % 4) in 1..2) w * 0.42f else w * 0.33f
        val x = cx + (r * cos(a)).toFloat()
        val y = cy + (r * sin(a)).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, c, style = s)
}

private const val GEAR_TEETH = 8
