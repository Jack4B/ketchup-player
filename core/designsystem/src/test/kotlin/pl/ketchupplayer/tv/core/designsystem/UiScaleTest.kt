package pl.ketchupplayer.tv.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class UiScaleTest {
    @Test
    fun autoNaFullHdPrzy320DpiZmniejszaDoOkolo075() {
        assertEquals(0.75f, resolveUiScale(UiScale.AUTO, 960), 0.001f)
    }

    @Test
    fun autoNaTypowymTelewizorze1280DpZostawia100() {
        assertEquals(1f, resolveUiScale(UiScale.AUTO, 1280), 0.001f)
    }

    @Test
    fun autoNigdyPonizej70ProcentINaTelefonieBezZmian() {
        assertEquals(0.7f, resolveUiScale(UiScale.AUTO, 700), 0.001f)
        assertEquals(1f, resolveUiScale(UiScale.AUTO, 400), 0.001f)
    }

    @Test
    fun staleProcentyDzialajaNiezalezenieOdEkranu() {
        assertEquals(0.8f, resolveUiScale(UiScale.X80, 960), 0.001f)
        assertEquals(1.15f, resolveUiScale(UiScale.X115, 1920), 0.001f)
    }
}
