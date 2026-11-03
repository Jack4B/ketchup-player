plugins {
    id("ketchup.android.library")
    id("ketchup.android.compose")
    id("ketchup.hilt")
    alias(libs.plugins.roborazzi)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":core:policy"))
    implementation(libs.coroutines.core)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.hilt.lifecycle)
    implementation(libs.androidx.activity.compose)

    testImplementation(project(":core:testing"))
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.coroutines.test)
    debugImplementation(libs.compose.ui.test.manifest)
}
