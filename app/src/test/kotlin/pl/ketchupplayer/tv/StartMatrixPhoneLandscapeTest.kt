package pl.ketchupplayer.tv

import org.robolectric.annotation.Config
import pl.ketchupplayer.tv.core.designsystem.DeviceClass

@Config(sdk = [34], qualifiers = "w800dp-h360dp-xhdpi")
class StartMatrixPhoneLandscapeTest : StartMatrixBase(DeviceClass.PHONE, "phone_poziom")
