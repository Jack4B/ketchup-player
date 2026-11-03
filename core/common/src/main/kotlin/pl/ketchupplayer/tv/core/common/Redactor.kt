package pl.ketchupplayer.tv.core.common

/** Maskuje sekrety w tekstach logów (KONSPEKT §8.4). Czysta logika, bez zależności od Androida. */
object Redactor {
    const val MASK = "***"

    private val rules: List<Pair<Regex, String>> =
        listOf(
            Regex("""(?i)\b(password|passwd|pass|username|user|token|mac|api_key|apikey)=([^&\s"']+)""") to "$1=$MASK",
            Regex("""(?i)(Authorization|Cookie|Set-Cookie)\s*:\s*[^\r\n]+""") to "$1: $MASK",
            Regex("""/(live|movie|series)/([^/\s]+)/([^/\s]+)/""") to "/$1/$MASK/$MASK/",
            Regex("""\b([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\b""") to MASK,
        )

    fun redact(text: String): String = rules.fold(text) { acc, (re, rep) -> re.replace(acc, rep) }
}
