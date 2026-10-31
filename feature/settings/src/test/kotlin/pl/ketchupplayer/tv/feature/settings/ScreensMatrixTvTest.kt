package pl.ketchupplayer.tv.feature.settings

import org.robolectric.annotation.Config
import pl.ketchupplayer.tv.core.designsystem.DeviceClass

@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class ScreensMatrixTvTest : ScreensMatrixBase(DeviceClass.TV, "tv")
