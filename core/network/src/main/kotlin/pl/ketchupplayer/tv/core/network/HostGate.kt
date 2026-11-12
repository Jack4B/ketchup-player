package pl.ketchupplayer.tv.core.network

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Jedno ciężkie pobranie naraz do jednego serwera. Panele IPTV zwykle dopuszczają jedno połączenie na konto i przy
 * drugim odpowiadają blokadą (np. błąd 444), więc lista, katalog filmów i przewodnik EPG z tego samego konta idą
 * po kolei. Zadania w tle (EPG) sprawdzają [contended] i ustępują, gdy czeka lista.
 */
object HostGate {
    private class Gate {
        val mutex = Mutex()
        val waiting = AtomicInteger(0)
    }

    private val gates = ConcurrentHashMap<String, Gate>()

    private fun gate(url: String) = gates.getOrPut(hostKey(url)) { Gate() }

    suspend fun <T> exclusive(
        url: String,
        block: suspend () -> T,
    ): T {
        val g = gate(url)
        g.waiting.incrementAndGet()
        var counted = true
        try {
            return g.mutex.withLock {
                g.waiting.decrementAndGet()
                counted = false
                block()
            }
        } finally {
            if (counted) g.waiting.decrementAndGet() // anulowane w kolejce
        }
    }

    /** Ktoś czeka na ten host: zadanie w tle powinno oddać połączenie. */
    fun contended(url: String): Boolean = gate(url).waiting.get() > 0

    /** `host:port` z domyślnym portem schematu, żeby `http://h:80/x` i `http://h/y` były tym samym serwerem. */
    private fun hostKey(url: String): String =
        runCatching {
            val uri = URI(url)
            val port =
                if (uri.port >
                    0
                ) {
                    uri.port
                } else if (uri.scheme.equals("https", ignoreCase = true)) {
                    HTTPS
                } else {
                    HTTP
                }
            "${uri.host.orEmpty().lowercase()}:$port"
        }.getOrDefault(url)

    private const val HTTP = 80
    private const val HTTPS = 443
}
