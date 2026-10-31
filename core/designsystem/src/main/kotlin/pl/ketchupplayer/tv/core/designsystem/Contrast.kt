package pl.ketchupplayer.tv.core.designsystem

import kotlin.math.pow

/** Kontrast WCAG 2.1 (względna luminancja sRGB) dla kolorów w formacie 0xAARRGGBB. */
object Contrast {
    fun ratio(
        a: Long,
        b: Long,
    ): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
