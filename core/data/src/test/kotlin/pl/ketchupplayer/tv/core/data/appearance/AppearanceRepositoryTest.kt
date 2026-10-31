package pl.ketchupplayer.tv.core.data.appearance

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pl.ketchupplayer.tv.core.designsystem.AccentChoice
import pl.ketchupplayer.tv.core.designsystem.AccentId
import pl.ketchupplayer.tv.core.designsystem.ThemeId
import java.io.File

class AppearanceRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun repo() =
        DataStoreAppearanceRepository(
            PreferenceDataStoreFactory.create { File(tmp.root, "a.preferences_pb") },
        )

    @Test
    fun `domyslnie Granat z akcentem Turkus`() =
        runTest {
            val a = repo().appearance.first()
            assertEquals(ThemeId.GRANAT, a.theme)
            assertNull(a.accent)
            assertEquals(AccentChoice.Preset(AccentId.TURKUS), a.effectiveAccent)
        }

    @Test
    fun `zmiana motywu bez wlasnego akcentu zmienia akcent domyslny`() =
        runTest {
            val r = repo()
            r.setTheme(ThemeId.BORDO)
            assertEquals(AccentChoice.Preset(AccentId.BURSZTYN), r.appearance.first().effectiveAccent)
        }

    @Test
    fun `wlasny akcent HEX jest trwaly i przezywa zmiane motywu`() =
        runTest {
            val r = repo()
            r.setAccent(AccentChoice.Custom(0xFFAA33CCL))
            r.setTheme(ThemeId.CZERN)
            val a = r.appearance.first()
            assertEquals(ThemeId.CZERN, a.theme)
            assertEquals(AccentChoice.Custom(0xFFAA33CCL), a.effectiveAccent)
        }

    @Test
    fun `preset i powrot do domyslnego`() =
        runTest {
            val r = repo()
            r.setAccent(AccentChoice.Preset(AccentId.FIOLET))
            assertEquals(AccentChoice.Preset(AccentId.FIOLET), r.appearance.first().accent)
            r.setAccent(null)
            assertNull(r.appearance.first().accent)
        }
}
