import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleRulesTest {
    @Test
    fun `poprawny graf nie ma naruszen`() {
        val graph = mapOf(
            ":app" to setOf(":feature:home", ":core:ui"),
            ":feature:home" to setOf(":core:ui", ":core:designsystem", ":provider:api"),
            ":core:policy" to setOf(":core:model"),
            ":provider:xtream" to setOf(":provider:api", ":core:network"),
        )
        assertTrue(ModuleRules.check(graph).isEmpty())
    }

    @Test
    fun `feature zalezny od feature lamie regule`() {
        val v = ModuleRules.check(mapOf(":feature:home" to setOf(":feature:vod")))
        assertEquals(1, v.size)
        assertEquals(":feature:home", v[0].from)
        assertEquals(":feature:vod", v[0].to)
    }

    @Test
    fun `czysta JVM nie moze zalezec od core common`() {
        assertEquals(1, ModuleRules.check(mapOf(":core:policy" to setOf(":core:common"))).size)
    }

    @Test
    fun `provider nie zna Room ani UI`() {
        val v = ModuleRules.check(mapOf(":provider:m3u" to setOf(":core:database", ":core:ui")))
        assertEquals(2, v.size)
    }

    @Test
    fun `core nie zalezy od feature`() {
        assertEquals(1, ModuleRules.check(mapOf(":core:data" to setOf(":feature:home"))).size)
        assertEquals(1, ModuleRules.check(mapOf(":core:ui" to setOf(":provider:api"))).size)
    }

    @Test
    fun `core data moze zalezec od dostawcow i EPG`() {
        val graph = mapOf(":core:data" to setOf(":provider:m3u", ":epg:xmltv", ":core:database"))
        assertTrue(ModuleRules.check(graph).isEmpty())
    }
}
