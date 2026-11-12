package pl.ketchupplayer.tv.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Warianty rozmiaru ekranu Home (różne `@Config`) nie mogą dziedziczyć testów po sobie ani zapisywać tego samego pliku
 * wzorca: odziedziczony test zrzutowy renderował wysoki ekran do wzorca niskiego (inny rozmiar obrazu), więc
 * porównanie w trybie verify nie mogło przejść. Scenariusze działania mają za to działać w obu rozmiarach.
 */
class HomeScreenVariantsTest {
    private val variants = listOf(HomeScreenTest::class.java, HomeScreenTallTest::class.java)

    private fun testNames(variant: Class<*>) =
        variant.methods
            .filter { it.isAnnotationPresent(Test::class.java) }
            .map { it.name }
            .toSet()

    @Test
    fun kazdyWariantRozmiaruMaWylacznieWlasneTesty() {
        variants.forEach { variant ->
            val inherited =
                variant.methods
                    .filter { it.isAnnotationPresent(Test::class.java) && it.declaringClass != variant }
                    .map { "${it.declaringClass.simpleName}.${it.name}" }
            assertEquals("${variant.simpleName} dziedziczy testy", emptyList<String>(), inherited)
        }
    }

    @Test
    fun kazdyPlikWzorcaMaJednoMiejsceZapisu() {
        val captures = Regex("""captureRoboImage\("([^"]+)"""")
        val paths =
            File("src/test/kotlin")
                .walkTopDown()
                .filter { it.extension == "kt" }
                .flatMap { file -> captures.findAll(file.readText()).map { it.groupValues[1] } }
                .toList()
        val duplicated =
            paths
                .groupBy { it }
                .filterValues { it.size > 1 }
                .keys
                .sorted()
        assertEquals("Ten sam plik wzorca zapisuje więcej niż jedno miejsce", emptyList<String>(), duplicated)
        assertEquals(true, paths.isNotEmpty())
    }

    @Test
    fun obaRozmiaryMajaTeSameScenariuszeDzialania() {
        val (low, tall) = variants.map { variant -> testNames(variant).filterNot { it.startsWith("screenshot") } }
        assertEquals("Scenariusze działania różnią się między rozmiarami", low.toSet(), tall.toSet())
        assertEquals(true, low.size >= 3)
    }
}
