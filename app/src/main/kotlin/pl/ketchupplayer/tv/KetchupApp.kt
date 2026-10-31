package pl.ketchupplayer.tv

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class KetchupApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(RedactingTree())
    }
}
