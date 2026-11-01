package pl.ketchupplayer.tv.player.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** Jedna ścieżka audio lub napisów do wyboru w OSD. [id] jest nieprzezroczysty dla UI i zwracany do kontrolera. */
data class TrackOption(
    val id: String,
    val label: String,
    val selected: Boolean,
)

/** Krótki opis bieżącego strumienia (panel „Informacje”). */
data class StreamSummary(
    val video: String? = null,
    val audio: String? = null,
    val bitrateKbps: Int? = null,
)

/** Wszystko, co OSD pokazuje i zmienia poza samym przewijaniem: ścieżki, tempo, przesunięcia, informacje. */
data class TrackInfo(
    val audio: List<TrackOption> = emptyList(),
    val text: List<TrackOption> = emptyList(),
    /** Napisy włączone (któraś ścieżka tekstowa jest wybrana). */
    val textEnabled: Boolean = false,
    val speed: Float = 1f,
    /** Dodatnie = dźwięk opóźniony względem obrazu. */
    val audioDelayMs: Int = 0,
    /** Dodatnie = napisy pojawiają się później. */
    val subtitleDelayMs: Int = 0,
    val summary: StreamSummary = StreamSummary(),
)

internal val NO_TRACKS: StateFlow<TrackInfo> = MutableStateFlow(TrackInfo())
internal val NO_SUBTITLE: StateFlow<String> = MutableStateFlow("")

/** Czytelne nazwy ścieżek: „Polski · AC3 5.1”, „Angielski · AAC stereo”. Czysta logika, testowana bez Androida. */
object TrackLabels {
    private const val STEREO = 2
    private const val SURROUND_5_1 = 6
    private const val SURROUND_7_1 = 8

    fun audio(
        language: String?,
        label: String?,
        channels: Int,
        mime: String?,
        index: Int,
    ): String {
        val name = label?.takeIf { it.isNotBlank() } ?: languageName(language) ?: "Ścieżka ${index + 1}"
        val details = listOfNotNull(codec(mime), layout(channels)).joinToString(" ")
        return if (details.isEmpty()) name else "$name · $details"
    }

    fun text(
        language: String?,
        label: String?,
        index: Int,
    ): String = label?.takeIf { it.isNotBlank() } ?: languageName(language) ?: "Napisy ${index + 1}"

    private fun languageName(code: String?): String? {
        if (code.isNullOrBlank() || code == "und") return null
        val name = Locale.forLanguageTag(code).getDisplayLanguage(POLISH)
        return name
            .takeIf {
                it.isNotBlank() &&
                    !it.equals(
                        code,
                        ignoreCase = true,
                    )
            }?.replaceFirstChar { it.uppercase() }
            ?: code.uppercase()
    }

    private fun layout(channels: Int): String? =
        when (channels) {
            1 -> "mono"
            STEREO -> "stereo"
            SURROUND_5_1 -> "5.1"
            SURROUND_7_1 -> "7.1"
            else -> if (channels > 0) "$channels kan." else null
        }

    fun codec(mime: String?): String? =
        when (mime?.lowercase()) {
            "audio/ac3" -> "AC3"
            "audio/eac3", "audio/eac3-joc" -> "E-AC3"
            "audio/mp4a-latm" -> "AAC"
            "audio/mpeg", "audio/mpeg-l2" -> "MP3"
            "audio/opus" -> "Opus"
            "audio/vorbis" -> "Vorbis"
            "audio/vnd.dts", "audio/vnd.dts.hd" -> "DTS"
            "audio/true-hd" -> "TrueHD"
            else -> null
        }

    private val POLISH = Locale.forLanguageTag("pl")
}
