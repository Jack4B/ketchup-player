package pl.ketchupplayer.tv

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.Shadows

/**
 * Test, którego okno startuje w trybie nie-dotykowym, jak aplikacja na TV (Compose: `InputMode.Keyboard` od pierwszej
 * klatki). Robolectric w trybie NATIVE startuje w trybie dotyku, a `performKeyInput` go nie wyłącza; w trybie dotyku
 * semantyka `Focused` elementów `FocusItem` jest pusta (ustalenie z PR #11, zob. `FocusItemTest`), więc scenariusze
 * pilota byłyby ślepe. Dotyczy tylko oznaczonych testów: reszta scenariuszy zostaje w trybie dotyku.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class StartsInRemoteMode

/**
 * Scenariusze E2E mają działać jak na telewizorze (cel wydania 1.0): bez cechy `leanback` aplikacja uznawała
 * Robolectric za telefon i układy TV (np. stałe 200 dp kart motywów) nie były w ogóle sprawdzane. Reguła musi
 * zadziałać przed uruchomieniem aktywności (order = -1). Test oznaczony [StartsInRemoteMode] dostaje też okno w
 * trybie nie-dotykowym: obsługiwany sposób to `Instrumentation.setInTouchMode(false)` przed startem aktywności
 * (Robolectric przekazuje to oknom przy ich dodaniu); stan okna sprawdza `ScenariosTest.requireRemoteMode`.
 */
class TvBoxRule : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            @Suppress("DEPRECATION")
            override fun evaluate() {
                val context = ApplicationProvider.getApplicationContext<android.app.Application>()
                Shadows.shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
                val remote = description.getAnnotation(StartsInRemoteMode::class.java) != null
                if (remote) InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
                try {
                    base.evaluate()
                } finally {
                    // Izolacja: kolejne testy zaczynają w domyślnym trybie dotyku Robolectric (NATIVE), nawet gdyby
                    // reset cieni nie zadziałał; Robolectric i tak odtwarza stan sesji okien po teście.
                    if (remote) InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
                }
            }
        }
}
