package pl.ketchupplayer.tv.player.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FailFastLoadPolicyTest {
    @Test
    fun `bledy klienta 4xx nie sa ponawiane`() {
        listOf(400, 401, 403, 404, 410, 451).forEach { assertFalse("kod $it", FailFastLoadPolicy.isRetryableHttp(it)) }
    }

    @Test
    fun `chwilowe 408 i 429 oraz bledy serwera 5xx sa ponawiane`() {
        listOf(408, 429, 500, 502, 503, 504).forEach { assertTrue("kod $it", FailFastLoadPolicy.isRetryableHttp(it)) }
    }
}
