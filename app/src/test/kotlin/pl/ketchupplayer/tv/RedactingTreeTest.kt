package pl.ketchupplayer.tv

import android.app.Application
import android.util.Log
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import timber.log.Timber

/**
 * Drzewo Timber maskuje komunikat i stos wyjątku (issue #12). Dotyczy wyłącznie logów przechodzących przez Timber:
 * logi bibliotek (Media3, OkHttp) i systemu omijają to drzewo. Dane wyłącznie fikcyjne.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RedactingTreeTest {
    private val secretUrl = "http://srv.example.invalid:8080/user123/pass456/77.ts"

    @Before
    fun plant() {
        ShadowLog.clear()
        Timber.uprootAll()
        Timber.plant(RedactingTree())
    }

    @After
    fun uproot() = Timber.uprootAll()

    private fun logged(): String = ShadowLog.getLogs().joinToString("\n") { it.msg }

    @Test
    fun `adres w komunikacie nie trafia do logu`() {
        Timber.e("Nie można pobrać %s", secretUrl)
        val out = logged()
        assertTrue(out, out.contains("Nie można pobrać"))
        assertFalse(out, out.contains("user123") || out.contains("pass456"))
    }

    @Test
    fun `adres w tekscie i stosie wyjatku nie trafia do logu`() {
        Timber.w(IllegalArgumentException("unexpected url: $secretUrl"), "Błąd listy")
        val out = logged()
        assertTrue(out, out.contains("Błąd listy") && out.contains("IllegalArgumentException"))
        assertFalse(out, out.contains("user123") || out.contains("pass456"))
    }

    @Test
    fun `poziom ponizej progu nie jest logowany`() {
        Timber.d("debug $secretUrl")
        assertEquals(0, ShadowLog.getLogs().size)
    }

    @Test
    fun `linia klawisza przechodzi bez zmian`() {
        val line = KeyLog.line(KeyLog.Record(123_456_789, 20, 0, 3, 42, 5, 0x501, false))
        Timber.tag("KeyGuard").i(line)
        assertTrue(ShadowLog.getLogs().any { it.msg == line && it.type == Log.INFO })
    }
}
