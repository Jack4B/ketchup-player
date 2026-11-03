package pl.ketchupplayer.tv

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource
import org.robolectric.Shadows

/**
 * Scenariusze E2E mają działać jak na telewizorze (cel wydania 1.0): bez cechy `leanback` aplikacja uznawała
 * Robolectric za telefon i układy TV (np. stałe 200 dp kart motywów) nie były w ogóle sprawdzane. Reguła musi
 * zadziałać przed uruchomieniem aktywności (order = -1).
 */
class TvBoxRule : ExternalResource() {
    override fun before() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        Shadows.shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
    }
}
