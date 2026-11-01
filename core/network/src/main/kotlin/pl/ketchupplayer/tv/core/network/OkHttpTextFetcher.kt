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

/** Pobiera tekst przez OkHttp: anulowalne, z limitem rozmiaru i rozpakowaniem gzip (także surowego `.gz`). */
class OkHttpTextFetcher(
    private val client: OkHttpClient,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) : TextFetcher {
    override suspend fun fetch(url: String): Result<String> =
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) return Result.failure(NetworkException.Http(response.code))
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

    override suspend fun <T> fetchLines(
        url: String,
        block: (Sequence<String>) -> T,
    ): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@withContext Result.failure(NetworkException.Http(response.code))
                    val body = response.body ?: return@withContext Result.success(block(emptySequence()))
                    val raw = BufferedInputStream(LimitedInputStream(body.byteStream(), maxBytes), BUFFER)
                    // Drugi limit liczy bajty po rozpakowaniu, żeby „bomba gzip” też została ucięta.
                    val counted = LimitedInputStream(maybeGunzip(raw), maxBytes)
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

/** Strumień, który po przekroczeniu [limit] bajtów rzuca [NetworkException.TooLarge]. */
private class LimitedInputStream(
    private val delegate: InputStream,
    private val limit: Long,
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
        if (n > 0) add(n)
        return n
    }

    override fun close() = delegate.close()

    private fun add(n: Int) {
        count += n
        if (count > limit) throw NetworkException.TooLarge(limit)
    }
}
