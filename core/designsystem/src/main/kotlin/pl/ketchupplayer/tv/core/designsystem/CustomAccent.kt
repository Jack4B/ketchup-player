package pl.ketchupplayer.tv.core.designsystem

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Wybór akcentu: jeden z 6 gotowych albo kolor własny użytkownika (ARGB, alfa zawsze 0xFF). */
sealed interface AccentChoice {
    data class Preset(
        val id: AccentId,
    ) : AccentChoice

    data class Custom(
        val argb: Long,
    ) : AccentChoice
}

object HexColor {
    /** Przyjmuje `#RGB`, `#RRGGBB`, `RGB`, `RRGGBB` (wielkość liter bez znaczenia). Zwraca ARGB z alfą FF albo null. */
    fun parse(raw: String): Long? {
        val t = raw.trim().removePrefix("#")
        val full =
            when (t.length) {
                3 -> t.map { "$it$it" }.joinToString("")
                6 -> t
                else -> return null
            }
        if (!full.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return 0xFF000000L or full.toLong(16)
    }

    fun format(argb: Long): String = "#%06X".format(argb and 0xFFFFFF)
}

/** Wynik dopasowania własnego koloru do motywu. [adjusted] = true, gdy kolor trzeba było rozjaśnić dla czytelności. */
data class FittedAccent(
    val argb: Long,
    val onAccent: Long,
    val adjusted: Boolean,
    val warnings: Set<AccentWarning>,
)

enum class AccentWarning {
    /** Odcień zielony — łatwo pomylić ze wskaźnikiem nowych odcinków. */
    LOOKS_LIKE_NEW_EPISODE_GREEN,

    /** Odcień czerwony — kojarzy się z błędem/nagrywaniem. */
    LOOKS_LIKE_ERROR_RED,
}

object AccentFit {
    private const val MIN_RING_CONTRAST = 3.0
    private const val MIN_TEXT_CONTRAST = 4.5
    private const val BLACK = 0xFF0B0B0BL
    private const val WHITE = 0xFFFFFFFFL

    /**
     * Rozjaśnia kolor (w HSL), aż ring focusu ma ≥ 3:1 na tle i na focusie motywu (WCAG, elementy UI).
     * Wszystkie motywy v3 są ciemne, więc kierunek to zawsze rozjaśnianie; odcień i nasycenie zostają.
     */
    fun fit(
        theme: ThemeId,
        argb: Long,
    ): FittedAccent {
        val t = Palettes.tokens(theme)

        fun ok(c: Long) =
            Contrast.ratio(c, t.background) >= MIN_RING_CONTRAST &&
                Contrast.ratio(c, t.surface3) >= MIN_RING_CONTRAST
        var color = argb or 0xFF000000L
        val original = color
        if (!ok(color)) {
            val hsl = toHsl(color)
            var l = hsl[2]
            while (l < 1.0 && !ok(fromHsl(hsl[0], hsl[1], l))) l = min(1.0, l + 0.01)
            color = fromHsl(hsl[0], hsl[1], l)
        }
        return FittedAccent(color, onAccentFor(color), color != original, warningsFor(color))
    }

    /** Ciemny tekst (#0B0B0B) albo biały, zależnie od tego, który daje ≥ 4,5:1 (lepszy z dwóch). */
    fun onAccentFor(argb: Long): Long {
        val black = Contrast.ratio(BLACK, argb)
        val white = Contrast.ratio(WHITE, argb)
        return if (black >= MIN_TEXT_CONTRAST || black >= white) BLACK else WHITE
    }

    private fun warningsFor(argb: Long): Set<AccentWarning> {
        val (h, s, _) = toHsl(argb)
        val out = mutableSetOf<AccentWarning>()
        if (s >= 0.25) {
            if (h in 85.0..165.0) out += AccentWarning.LOOKS_LIKE_NEW_EPISODE_GREEN
            if (h <= 15.0 || h >= 345.0) out += AccentWarning.LOOKS_LIKE_ERROR_RED
        }
        return out
    }

    internal fun toHsl(argb: Long): DoubleArray {
        val r = ((argb shr 16) and 0xFF) / 255.0
        val g = ((argb shr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val l = (mx + mn) / 2
        val d = mx - mn
        if (d == 0.0) return doubleArrayOf(0.0, 0.0, l)
        val s = d / (1 - abs(2 * l - 1))
        val h =
            when (mx) {
                r -> 60 * (((g - b) / d) % 6)
                g -> 60 * ((b - r) / d + 2)
                else -> 60 * ((r - g) / d + 4)
            }
        return doubleArrayOf((h + 360) % 360, s, l)
    }

    internal fun fromHsl(
        h: Double,
        s: Double,
        l: Double,
    ): Long {
        val c = (1 - abs(2 * l - 1)) * s
        val x = c * (1 - abs((h / 60) % 2 - 1))
        val m = l - c / 2
        val (r, g, b) =
            when {
                h < 60 -> Triple(c, x, 0.0)
                h < 120 -> Triple(x, c, 0.0)
                h < 180 -> Triple(0.0, c, x)
                h < 240 -> Triple(0.0, x, c)
                h < 300 -> Triple(x, 0.0, c)
                else -> Triple(c, 0.0, x)
            }

        fun ch(v: Double) = ((v + m) * 255).let { Math.round(it).toLong().coerceIn(0, 255) }
        return 0xFF000000L or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
}
