package pl.ketchupplayer.tv.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream

class OkHttpStreamFetcher(
    private val client: OkHttpClient,
) : StreamFetcher {
    override suspend fun <T> open(
        url: String,
        block: (InputStream) -> T,
    ): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@withContext Result.failure(response.toHttpFailure())
                    val body =
                        response.body
                            ?: return@withContext Result.failure(NetworkException.Io(IOException("Pusta odpowiedź")))
                    Result.success(block(maybeGunzip(BufferedInputStream(body.byteStream(), BUFFER))))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Result.failure(NetworkException.Io(e))
            }
        }

    private fun maybeGunzip(input: BufferedInputStream): InputStream {
        input.mark(MAGIC_BYTES)
        val b1 = input.read()
        val b2 = input.read()
        input.reset()
        return if (b1 == GZIP_1 && b2 == GZIP_2) GZIPInputStream(input, BUFFER) else input
    }

    private companion object {
        const val BUFFER = 64 * 1024
        const val MAGIC_BYTES = 2
        const val GZIP_1 = 0x1f
        const val GZIP_2 = 0x8b
    }
}
