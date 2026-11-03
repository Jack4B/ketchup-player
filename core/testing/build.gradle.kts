plugins {
    id("ketchup.android.library")
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.ui.test.junit4)
}
