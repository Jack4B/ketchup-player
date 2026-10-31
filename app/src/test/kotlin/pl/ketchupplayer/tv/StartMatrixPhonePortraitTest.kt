package pl.ketchupplayer.tv

import org.robolectric.annotation.Config
import pl.ketchupplayer.tv.core.designsystem.DeviceClass

@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class StartMatrixPhonePortraitTest : StartMatrixBase(DeviceClass.PHONE, "phone_pion")
