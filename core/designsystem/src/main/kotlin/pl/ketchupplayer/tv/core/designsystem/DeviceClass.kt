package pl.ketchupplayer.tv.core.designsystem

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Klasa urządzenia: TV/box (pilot, 10-foot UI) albo telefon (dotyk, układ adaptacyjny pion/poziom). */
enum class DeviceClass { TV, PHONE }

@Composable
fun rememberDeviceClass(): DeviceClass {
    val context = LocalContext.current
    return remember(context) { detectDeviceClass(context) }
}

fun detectDeviceClass(context: Context): DeviceClass {
    val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val isTv =
        uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    return if (isTv) DeviceClass.TV else DeviceClass.PHONE
}
