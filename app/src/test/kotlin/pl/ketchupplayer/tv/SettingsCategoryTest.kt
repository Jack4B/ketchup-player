package pl.ketchupplayer.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsCategoryTest {
    /** Regresja: bez wskazanej strony Ustawienia startowały na „Filmy i seriale” (pierwsza kategoria bez strony). */
    @Test
    fun `bez wskazanej strony start na pierwszej kategorii`() {
        assertEquals(0, initialCategoryIndex(null))
        assertEquals(SettingsCategory.PROVIDERS, SettingsCategory.entries[initialCategoryIndex(null)])
    }

    @Test
    fun `wskazana strona wybiera jej kategorie`() {
        val appearance = initialCategoryIndex(SettingsPage.APPEARANCE)
        val tv = initialCategoryIndex(SettingsPage.EPG_SOURCES)
        assertEquals(SettingsCategory.APPEARANCE, SettingsCategory.entries[appearance])
        assertEquals(SettingsCategory.TV, SettingsCategory.entries[tv])
    }
}
