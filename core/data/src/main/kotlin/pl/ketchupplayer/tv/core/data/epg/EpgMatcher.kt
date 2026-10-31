package pl.ketchupplayer.tv.core.data.epg

import pl.ketchupplayer.tv.core.database.ChannelEpgKey
import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.epg.xmltv.XmlTvChannel

/**
 * Dopasowanie kanałów z pliku XMLTV do kanałów katalogu: najpierw po `epgId` (bez względu na wielkość liter),
 * potem po znormalizowanej nazwie (z sufiksami jakości HD/FHD/UHD/4K/SD odciętymi po obu stronach).
 */
class EpgMatcher(
    keys: List<ChannelEpgKey>,
) {
    private val byEpgId: Map<String, List<String>> =
        keys.filter { !it.epgId.isNullOrBlank() }.groupBy({ it.epgId!!.trim().lowercase() }, { it.id })
    private val byName: Map<String, List<String>> = keys.groupBy({ simplify(it.normName) }, { it.id })

    fun match(channel: XmlTvChannel): Set<String> {
        val ids = LinkedHashSet<String>()
        byEpgId[channel.id.trim().lowercase()]?.let(ids::addAll)
        channel.displayNames.forEach { name -> byName[simplify(Channel.normalizeName(name))]?.let(ids::addAll) }
        return ids
    }

    private fun simplify(normName: String): String =
        normName.split(' ').filter { it.isNotEmpty() && it !in QUALITY_TOKENS }.joinToString(" ")

    private companion object {
        val QUALITY_TOKENS = setOf("hd", "fhd", "uhd", "4k", "sd", "h265", "hevc")
    }
}
