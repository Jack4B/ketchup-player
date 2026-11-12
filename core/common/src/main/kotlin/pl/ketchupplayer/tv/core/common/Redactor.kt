package pl.ketchupplayer.tv.core.common

/**
 * Maskuje sekrety w tekstach logów, komunikatów błędów i zapisanych opisów (KONSPEKT §8.4). Czysta logika, bez
 * zależności od Androida.
 *
 * Dwie warstwy:
 * 1. Reguły globalne (parametry `nazwa=wartość` z krótkiej listy, nagłówki, adres MAC, ścieżki Xtream z prefiksem
 *    `/live|movie|series/użytkownik/hasło/`), stosowane do całego tekstu.
 * 2. Reguły **w kontekście URL**: tylko wewnątrz tokenu `schemat://…` (issue #12). Dane dostępu bywają w
 *    userinfo, w parametrach zapytania i w ścieżce (`/użytkownik/hasło/id`, z prefiksem albo bez).
 *
 * Reguła ścieżki jest SZEROKA (decyzja właściciela: lepiej stracić szczegóły diagnostyczne niż ujawnić login lub
 * hasło): w adresie o co najmniej trzech niepustych segmentach ścieżki maskowane są dwa pierwsze, chyba że
 * ostatni segment ma rozszerzenie zasobu statycznego (obrazy, XML, JSON itp.). Skutek uboczny: fałszywe trafienia,
 * np. `/live/pl/tvp1.m3u8` albo `/v1/titles/tt0133093` też są maskowane (zob. RedactorTest).
 *
 * Zagnieżdżone adresy (`://`, `%3A%2F%2F`, podwójne kodowanie `%25`, zakodowany ukośnik `%2F` lub `=` w wartości)
 * nie są dekodowane ani czyszczone rekurencyjnie: cała podejrzana wartość parametru albo reszta ścieżki od takiego
 * segmentu jest zastępowana maską.
 *
 * GRANICE (nie obiecujemy braku wycieków): adres bez schematu (`host:port/użytkownik/hasło/id`), pary klucz-wartość
 * w JSON, własne nagłówki (np. `trakt-api-key`), tekst ścieżki poza URL-em, dane w innych kodowaniach niż
 * procentowe, logi bibliotek i systemu omijające ten obiekt oraz wszystko, co nie przechodzi przez [redact].
 */
object Redactor {
    const val MASK = "***"

    private val rules: List<Pair<Regex, String>> =
        listOf(
            Regex("""(?i)\b(password|passwd|pass|username|user|token|mac|api_key|apikey)=([^&\s"']+)""") to "$1=$MASK",
            Regex("""(?i)(Authorization|Cookie|Set-Cookie)\s*:\s*[^\r\n]+""") to "$1: $MASK",
            Regex("""/(live|movie|series)/([^/\s]+)/([^/\s]+)/""") to "/$1/$MASK/$MASK/",
            Regex("""\b([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\b""") to MASK,
        )

    /** Token adresu: schemat, `://` i znaki do pierwszego białego znaku, cudzysłowu lub nawiasu ostrego. */
    private val urlToken = Regex("""(?i)\b[a-z][a-z0-9+.\-]{1,15}://[^\s"'<>]+""")
    private val trailingPunctuation = Regex("""[)\]}.,;:!?]+$""")
    private val urlParts = Regex("""^([^/?#]*//)([^/?#]*)([^?#]*)(.*)$""", RegexOption.DOT_MATCHES_ALL)
    private val queryParam = Regex("""([?&;#])([^=&;#]*)=([^&;#]*)""")

    /** Nazwy parametrów z danymi dostępu, maskowane tylko w kontekście URL (`key` kolidowałoby z logiem klawiszy). */
    private val credentialParams =
        setOf(
            "password",
            "passwd",
            "pass",
            "pwd",
            "username",
            "user",
            "login",
            "token",
            "access_token",
            "auth",
            "secret",
            "api_key",
            "apikey",
            "key",
            "mac",
            "sig",
            "signature",
        )

    /** Prefiksy ścieżek Xtream, po których stoją: użytkownik, hasło, identyfikator (i ewentualnie dalsze segmenty). */
    private val pathPrefixes = setOf("live", "movie", "series", "timeshift")

    /** Rozszerzenia zasobów statycznych: ścieżka z takim końcem nie niesie danych dostępu w segmentach. */
    private val staticExtensions =
        setOf(
            "png",
            "jpg",
            "jpeg",
            "gif",
            "webp",
            "svg",
            "ico",
            "bmp",
            "css",
            "js",
            "map",
            "woff",
            "woff2",
            "ttf",
            "xml",
            "json",
            "html",
            "htm",
            "txt",
            "gz",
            "zip",
            "srt",
            "vtt",
            "php",
        )

    /** Schematy lokalne (ścieżki plików i zasobów Androida): nie niosą danych dostępu w segmentach. */
    private val localSchemes = setOf("file", "content", "android.resource", "asset")

    /** Liczba segmentów użytkownik/hasło; ścieżka z identyfikatorem ma ich co najmniej o jeden więcej. */
    private const val CREDENTIAL_SEGMENTS = 2

    /** Ślad zagnieżdżonego adresu, podwójnego kodowania albo zakodowanej ścieżki/zapytania w wartości. */
    private val nestedMarker = Regex("""(?i)(://|%3A%2F%2F|%25|%2F|%3D)""")

    fun redact(text: String): String {
        val global = rules.fold(text) { acc, (re, rep) -> re.replace(acc, rep) }
        return urlToken.replace(global) { redactUrl(it.value) }
    }

    private fun redactUrl(token: String): String {
        val trail = trailingPunctuation.find(token)?.value.orEmpty()
        val url = token.dropLast(trail.length)
        val parts = urlParts.matchEntire(url) ?: return token
        val scheme = parts.groupValues[1]
        val authority = parts.groupValues[2]
        val path = parts.groupValues[3]
        val tail = parts.groupValues[4]
        val localScheme = scheme.removeSuffix("://").lowercase() in localSchemes
        return scheme + maskUserinfo(authority) + (if (localScheme) path else redactPath(path)) + redactTail(tail) +
            trail
    }

    private fun maskUserinfo(authority: String): String {
        val at = authority.lastIndexOf('@')
        return if (at < 0) authority else MASK + authority.substring(at)
    }

    private fun redactPath(path: String): String {
        if (path.isEmpty()) return path
        val nested = nestedMarker.find(path) ?: return maskCredentialSegments(path)
        // Zagnieżdżony adres: segment ze śladem i cała reszta ścieżki są zastępowane maską. Segment ze śladem liczy
        // się jako segment (mógł być hasłem), a gdy stoi w pierwszych dwóch pozycjach, maskujemy też poprzedzające.
        val cut = path.lastIndexOf('/', nested.range.first)
        val head = path.substring(0, cut + 1)
        val keptCount = head.split('/').count { it.isNotEmpty() }
        val masked = if (keptCount < CREDENTIAL_SEGMENTS) maskAllSegments(head) else maskCredentialSegments(head + MASK)
        return if (masked.endsWith(MASK)) masked else masked + MASK
    }

    private fun maskAllSegments(path: String): String =
        path.split('/').joinToString("/") { if (it.isEmpty()) it else MASK }

    /** Szeroka reguła ścieżki: prefiks Xtream albo (bez prefiksu) dwa pierwsze z co najmniej trzech segmentów. */
    private fun maskCredentialSegments(path: String): String {
        val segments = path.split('/').toMutableList()
        // Segmenty ścieżki bez pustych brzegów (początek `//`, końcowy `/`); pusty środek (np. puste hasło) się liczy.
        val real = segments.indices.dropWhile { segments[it].isEmpty() }.dropLastWhile { segments[it].isEmpty() }
        val prefixAt = real.firstOrNull { segments[it].lowercase() in pathPrefixes }
        val afterPrefix = prefixAt?.let { p -> real.filter { it > p } }.orEmpty()
        when {
            prefixAt != null && afterPrefix.size >= CREDENTIAL_SEGMENTS + 1 -> {
                segments[afterPrefix[0]] = MASK
                segments[afterPrefix[1]] = MASK
            }
            real.size > CREDENTIAL_SEGMENTS && !hasStaticExtension(segments[real.last()]) -> {
                segments[real[0]] = MASK
                segments[real[1]] = MASK
            }
        }
        return segments.joinToString("/")
    }

    private fun hasStaticExtension(segment: String): Boolean {
        val dot = segment.lastIndexOf('.')
        return dot >= 0 && segment.substring(dot + 1).lowercase() in staticExtensions
    }

    private fun redactTail(tail: String): String =
        if (tail.isEmpty()) {
            tail
        } else {
            queryParam.replace(tail) { m ->
                val (delimiter, name, value) = m.destructured
                val secret = name.lowercase() in credentialParams || nestedMarker.containsMatchIn(value)
                if (secret) "$delimiter$name=$MASK" else m.value
            }
        }
}
