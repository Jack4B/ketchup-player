package pl.ketchupplayer.tv.epg.xmltv

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Kanał z pliku XMLTV: [id] oraz nazwy wyświetlane (do dopasowania z katalogiem po nazwie). */
data class XmlTvChannel(
    val id: String,
    val displayNames: List<String>,
)

/** Audycja z pliku XMLTV (przed dopasowaniem do kanałów katalogu). */
data class XmlTvProgramme(
    val channelId: String,
    val startMs: Long,
    val endMs: Long,
    val title: String,
    val description: String?,
    val category: String?,
)

/**
 * Strumieniowy parser XMLTV (XmlPullParser): pamięć stała niezależnie od rozmiaru pliku (setki MB po rozpakowaniu).
 * Zwraca wyniki przez callbacki; błędne wpisy (brak kanału/czasu/tytułu) są pomijane i liczone.
 * Czasy `yyyyMMddHHmmss [+-]HHmm` są przeliczane na UTC; [timeShiftMinutes] przesuwa wszystko (ustawienie użytkownika).
 */
class XmlTvParser(
    private val timeShiftMinutes: Int = 0,
) {
    data class Result(
        val channels: Int,
        val programmes: Int,
        val skipped: Int,
    )

    fun parse(
        input: InputStream,
        onChannel: (XmlTvChannel) -> Unit,
        onProgramme: (XmlTvProgramme) -> Unit,
        shouldContinue: () -> Boolean = { true },
    ): Result {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply { setInput(input, null) }
        val counts = IntArray(COUNTERS)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && shouldContinue()) {
            if (event == XmlPullParser.START_TAG) handle(parser, onChannel, onProgramme, counts)
            event = parser.next()
        }
        return Result(counts[CHANNELS], counts[PROGRAMMES], counts[SKIPPED])
    }

    private fun handle(
        parser: XmlPullParser,
        onChannel: (XmlTvChannel) -> Unit,
        onProgramme: (XmlTvProgramme) -> Unit,
        counts: IntArray,
    ) {
        when (parser.name) {
            "channel" -> deliver(readChannel(parser), onChannel, counts, CHANNELS)
            "programme" -> deliver(readProgramme(parser), onProgramme, counts, PROGRAMMES)
        }
    }

    private fun <T> deliver(
        item: T?,
        sink: (T) -> Unit,
        counts: IntArray,
        counter: Int,
    ) {
        if (item == null) {
            counts[SKIPPED]++
        } else {
            sink(item)
            counts[counter]++
        }
    }

    private fun readChannel(p: XmlPullParser): XmlTvChannel? {
        val id = p.getAttributeValue(null, "id")?.trim().orEmpty()
        val names = mutableListOf<String>()
        forEachChild(p) {
            if (it == "display-name") {
                p
                    .nextText()
                    .trim()
                    .takeIf(String::isNotEmpty)
                    ?.let(names::add)
            }
        }
        return if (id.isEmpty()) null else XmlTvChannel(id, names)
    }

    private fun readProgramme(p: XmlPullParser): XmlTvProgramme? {
        val channel = p.getAttributeValue(null, "channel")?.trim().orEmpty()
        val start = parseTime(p.getAttributeValue(null, "start"))
        val stop = parseTime(p.getAttributeValue(null, "stop"))
        val texts = HashMap<String, String>()
        forEachChild(p) { name ->
            if (name in TEXT_TAGS) {
                val text = p.nextText().trim()
                if (text.isNotEmpty()) texts.putIfAbsent(name, text)
            }
        }
        if (channel.isEmpty() || start == null) return null
        if (stop == null || stop <= start) return null
        val title = texts["title"] ?: return null
        val shift = timeShiftMinutes * MS_PER_MINUTE
        return XmlTvProgramme(channel, start + shift, stop + shift, title, texts["desc"], texts["category"])
    }

    /** Wywołuje [onChild] dla każdego dziecka bieżącego elementu; kończy na jego znaczniku zamykającym. */
    private fun forEachChild(
        p: XmlPullParser,
        onChild: (String) -> Unit,
    ) {
        val depth = p.depth
        while (!(p.next() == XmlPullParser.END_TAG && p.depth == depth)) {
            if (p.eventType == XmlPullParser.START_TAG) onChild(p.name)
        }
    }

    companion object {
        private const val MS_PER_MINUTE = 60_000L
        private val withZone = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")
        private val withoutZone = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

        /** `20261031200000 +0100` albo bez strefy (UTC). Zwraca null dla błędnego formatu. */
        fun parseTime(raw: String?): Long? {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return null
            return runCatching {
                if (text.contains(' ')) {
                    OffsetDateTime.parse(text, withZone).toInstant().toEpochMilli()
                } else {
                    java.time.LocalDateTime
                        .parse(
                            text.take(TIME_DIGITS),
                            withoutZone,
                        ).toInstant(ZoneOffset.UTC)
                        .toEpochMilli()
                }
            }.getOrNull()
        }

        private const val TIME_DIGITS = 14
        private const val COUNTERS = 3
        private const val CHANNELS = 0
        private const val PROGRAMMES = 1
        private const val SKIPPED = 2
        private val TEXT_TAGS = setOf("title", "desc", "category")
    }
}
