plugins {
    id("ketchup.android.library")
    id("ketchup.hilt")
}

dependencies {
    api(libs.datastore.preferences)
    implementation(libs.coroutines.core)
    testImplementation(libs.coroutines.test)
}
