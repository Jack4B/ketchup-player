package pl.ketchupplayer.tv.provider.m3u

/** Atrybuty wiersza `#EXTINF`, z których korzystamy; reszta nie jest kopiowana (lista ma 100 tys. wpisów). */
internal class M3uAttrs {
    var tvgId: String? = null
    var tvgName: String? = null
    var logo: String? = null
    var group: String? = null
    var chno: String? = null
    var channelNumber: String? = null
}

/**
 * Skaner `klucz="wartość"` bez regexa i bez mapy: na słabym boxie regex z `findAll` i `associate`
 * był najdroższą częścią czytania listy. Semantyka jak poprzedniego regexa `([A-Za-z0-9_-]+)="([^"]*)"`.
 */
internal class M3uAttributeScanner(
    private val line: String,
) {
    private val out = M3uAttrs()
    private var keyStart = 0
    private var keyLen = 0
    private var valueStart = 0
    private var valueEnd = 0

    fun scan(end: Int): M3uAttrs {
        var i = 0
        while (i < end) i = scanNext(i, end)
        return out
    }

    /** Czyta jedną parę od pozycji [from]; zwraca pozycję, od której szukać następnej ([end] = koniec). */
    private fun scanNext(
        from: Int,
        end: Int,
    ): Int {
        val eq = line.indexOf("=\"", from)
        val close = if (eq < 0 || eq >= end) -1 else line.indexOf('"', eq + 2)
        if (close < 0) return end
        var start = eq
        while (start > from && isKeyChar(line[start - 1])) start--
        keyStart = start
        keyLen = eq - start
        valueStart = eq + 2
        valueEnd = close
        if (keyLen > 0) assign()
        return close + 1
    }

    private fun isKeyChar(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_' || c == '-'

    private fun key(name: String) =
        keyLen == name.length && line.regionMatches(keyStart, name, 0, keyLen, ignoreCase = true)

    private fun value() = line.substring(valueStart, valueEnd)

    private fun assign() {
        when {
            key("tvg-id") -> out.tvgId = value()
            key("tvg-name") -> out.tvgName = value()
            key("tvg-logo") -> out.logo = value()
            key("group-title") -> out.group = value()
            key("tvg-chno") -> out.chno = value()
            key("channel-number") -> out.channelNumber = value()
        }
    }
}

/** Szesnastkowy skrót bez `String.format` (na Androidzie kilkadziesiąt razy wolniejszy; wołany 100 tys. razy). */
internal object Hex {
    private const val DIGITS = "0123456789abcdef"
    private const val NIBBLE = 4
    private const val MASK = 0x0f
    private const val BYTE_MASK = 0xff

    fun of(
        bytes: ByteArray,
        count: Int,
    ): String {
        val out = CharArray(count * 2)
        for (i in 0 until count) {
            val v = bytes[i].toInt() and BYTE_MASK
            out[i * 2] = DIGITS[v ushr NIBBLE]
            out[i * 2 + 1] = DIGITS[v and MASK]
        }
        return String(out)
    }
}
