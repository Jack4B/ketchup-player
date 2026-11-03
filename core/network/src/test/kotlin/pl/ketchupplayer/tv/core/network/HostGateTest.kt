package pl.ketchupplayer.tv.core.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Panele IPTV zwykle dopuszczają jedno połączenie na konto: ruch do jednego hosta nie może się nakładać. */
@OptIn(ExperimentalCoroutinesApi::class)
class HostGateTest {
    @Test
    fun `dwa zadania do tego samego hosta nie nakladaja sie, rozne hosty tak`() =
        runTest {
            val events = mutableListOf<String>()
            val firstInside = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val a =
                launch {
                    HostGate.exclusive("http://panel.example.invalid:80/get.php?u=1") {
                        events += "a-start"
                        firstInside.complete(Unit)
                        release.await()
                        events += "a-end"
                    }
                }
            firstInside.await()
            val b =
                launch {
                    HostGate.exclusive("http://panel.example.invalid:80/xmltv.php?u=1") {
                        events += "b-start"
                    }
                }
            testScheduler.runCurrent() // b startuje i staje w kolejce do hosta
            // Inny host nie czeka na pierwszy.
            HostGate.exclusive("http://inny.example.invalid/x") { events += "inny" }
            assertTrue("b czeka, bo to ten sam host", HostGate.contended("http://panel.example.invalid/zzz"))
            assertEquals(listOf("a-start", "inny"), events)
            release.complete(Unit)
            a.join()
            b.join()
            assertEquals(listOf("a-start", "inny", "a-end", "b-start"), events)
            assertFalse(HostGate.contended("http://panel.example.invalid/zzz"))
        }
}
