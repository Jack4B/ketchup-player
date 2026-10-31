plugins {
    id("ketchup.android.library")
    id("ketchup.hilt")
}

dependencies {
    api(project(":core:model"))
    api(libs.media3.exoplayer)
    implementation(libs.media3.hls)
    implementation(libs.media3.okhttp)
    implementation(project(":core:network"))
    implementation(libs.coroutines.core)
    testImplementation(libs.coroutines.test)
}
