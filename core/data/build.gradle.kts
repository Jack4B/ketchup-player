plugins {
    id("ketchup.android.library")
    id("ketchup.hilt")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:policy"))
    api(project(":core:designsystem"))
    api(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:common"))
    implementation(project(":provider:m3u"))
    implementation(project(":provider:xtream"))
    implementation(project(":core:security"))
    implementation(project(":core:datastore"))
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
