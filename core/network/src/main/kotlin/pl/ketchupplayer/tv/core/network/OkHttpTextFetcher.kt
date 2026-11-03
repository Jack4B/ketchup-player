package pl.ketchupplayer.tv.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Pobiera tekst przez OkHttp: anulowalne, z limitem rozmiaru i rozpakowaniem gzip (także surowego `.gz`).
 * [maxBytes] dotyczy odpowiedzi wczytywanych w całości do pamięci ([fetch]); listy i katalogi czytane strumieniem
 * ([fetchLines], [fetchStream]) mają osobny, bardzo wysoki limit [streamMaxBytes] (raport z boxa: lista 64 MB była
 * odrzucana, a inne odtwarzacze wczytują ją bez problemu) i dłuższy czas oczekiwania na dane od serwera.
 */
class OkHttpTextFetcher(
    private val client: OkHttpClient,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val meter: TransferMeter? = null,
    private val streamMaxBytes: Long = DEFAULT_STREAM_MAX_BYTES,
) : TextFetcher {
    /** Klient do list: serwer składający ogromną listę na żądanie bywa cichy dłużej niż 60 s. */
    private val streamClient: OkHttpClient by lazy {
        client.newBuilder().readTimeout(STREAM_READ_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS).build()
    }

    override fun withUserAgent(userAgent: String): TextFetcher =
        OkHttpTextFetcher(
            client
                .newBuilder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain
                            .request()
                            .newBuilder()
                            .header("User-Agent", userAgent)
                            .build(),
                    )
                }.build(),
            maxBytes,
            meter,
            streamMaxBytes,
        )

    override suspend fun fetch(url: String): Result<String> = fetchWithHeaders(url, emptyMap())

    override suspend fun fetchWithHeaders(
        url: String,
        headers: Map<String, String>,
    ): Result<String> =
        try {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .apply { headers.forEach { (k, v) -> header(k, v) } }
                    .build()
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) return Result.failure(response.toHttpFailure())
                Result.success(readLimited(response))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: NetworkException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(NetworkException.Io(e))
        } catch (e: IllegalArgumentException) {
            Result.failure(NetworkException.Io(e))
        }

    override suspend fun <T> fetchStream(
        url: String,
        block: (InputStream) -> T,
    ): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                streamClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@withContext Result.failure(response.toHttpFailure())
                    val body = response.body ?: return@withContext Result.success(block("".byteInputStream()))
                    val raw = BufferedInputStream(LimitedInputStream(body.byteStream(), streamMaxBytes, meter), BUFFER)
                    val counted = LimitedInputStream(maybeGunzip(raw), streamMaxBytes)
                    counted.use { Result.success(block(it)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                Result.failure(e)
            } catch (e: IOException) {
                Result.failure(NetworkException.Io(e))
            } catch (e: IllegalArgumentException) {
                Result.failure(NetworkException.Io(e))
            }
        }

    override suspend fun <T> fetchLines(
        url: String,
        block: (Sequence<String>) -> T,
    ): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                streamClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@withContext Result.failure(response.toHttpFailure())
                    val body = response.body ?: return@withContext Result.success(block(emptySequence()))
                    val raw = BufferedInputStream(LimitedInputStream(body.byteStream(), streamMaxBytes, meter), BUFFER)
                    // Drugi limit liczy bajty po rozpakowaniu, żeby „bomba gzip” też została ucięta.
                    val counted = TruncationTolerantInputStream(LimitedInputStream(maybeGunzip(raw), streamMaxBytes))
                    counted.bufferedReader(Charsets.UTF_8).use { reader ->
                        Result.success(block(reader.lineSequence()))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NetworkException) {
                Result.failure(e)
            } catch (e: IOException) {
                Result.failure(NetworkException.Io(e))
            } catch (e: IllegalArgumentException) {
                Result.failure(NetworkException.Io(e))
            }
        }

    private fun maybeGunzip(input: BufferedInputStream): InputStream {
        input.mark(2)
        val b1 = input.read()
        val b2 = input.read()
        input.reset()
        return if (b1 == GZIP_MAGIC_1.toInt() && b2 == (GZIP_MAGIC_2.toInt() and 0xff)) {
            GZIPInputStream(input, BUFFER)
        } else {
            input
        }
    }

    private fun readLimited(response: Response): String {
        val body = response.body ?: return ""
        val out = ByteArrayOutputStream()
        body.byteStream().use { input ->
            val buf = ByteArray(BUFFER)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > maxBytes) throw NetworkException.TooLarge(maxBytes)
            }
        }
        val bytes = out.toByteArray()
        return if (isGzip(bytes)) gunzip(bytes) else String(bytes, Charsets.UTF_8)
    }

    private fun isGzip(b: ByteArray) = b.size > 2 && b[0] == GZIP_MAGIC_1 && b[1] == GZIP_MAGIC_2

    private fun gunzip(bytes: ByteArray): String {
        val out = ByteArrayOutputStream()
        GZIPInputStream(bytes.inputStream()).use { input ->
            val buf = ByteArray(BUFFER)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > maxBytes) throw NetworkException.TooLarge(maxBytes)
            }
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    private companion object {
        const val DEFAULT_MAX_BYTES = 64L * 1024 * 1024
        const val DEFAULT_STREAM_MAX_BYTES = 2L * 1024 * 1024 * 1024
        const val STREAM_READ_TIMEOUT_S = 180L
        const val BUFFER = 8 * 1024
        const val GZIP_MAGIC_1: Byte = 0x1f
        const val GZIP_MAGIC_2: Byte = 0x8b.toByte()
    }
}

private suspend fun Call.await(): Response =
    suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    if (!cont.isCancelled) cont.resumeWithException(e)
                }

                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    cont.resume(response)
                }
            },
        )
    }

/**
 * Panele IPTV składają listę w locie i często zrywają połączenie przed czasem albo podają zły Content-Length;
 * inne odtwarzacze biorą wtedy to, co dotarło. Błąd wejścia/wyjścia PO odebraniu pierwszych bajtów kończy strumień
 * (ucięta ostatnia linia jest odrzucana przez parser), a bez żadnych danych nadal jest błędem. Przekroczenie limitu
 * ([NetworkException]) przechodzi bez zmian.
 */
private class TruncationTolerantInputStream(
    private val delegate: InputStream,
) : InputStream() {
    private var received = false

    override fun read(): Int =
        try {
            delegate.read().also { if (it >= 0) received = true }
        } catch (e: IOException) {
            endOrThrow(e)
        }

    override fun read(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int =
        try {
            delegate.read(b, off, len).also { if (it > 0) received = true }
        } catch (e: IOException) {
            endOrThrow(e)
        }

    override fun close() = delegate.close()

    private fun endOrThrow(e: IOException): Int = if (received) -1 else throw e
}

/** Strumień, który po przekroczeniu [limit] bajtów rzuca [NetworkException.TooLarge]. */
private class LimitedInputStream(
    private val delegate: InputStream,
    private val limit: Long,
    private val meter: TransferMeter? = null,
) : InputStream() {
    private var count = 0L

    override fun read(): Int {
        val b = delegate.read()
        if (b >= 0) add(1)
        return b
    }

    override fun read(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int {
        val n = delegate.read(b, off, len)
        if (n > 0) {
            add(n)
            meter?.add(n)
        }
        return n
    }

    override fun close() = delegate.close()

    private fun add(n: Int) {
        count += n
        if (count > limit) throw NetworkException.TooLarge(limit)
    }
}
