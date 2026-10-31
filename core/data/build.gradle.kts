plugins {
    id("ketchup.android.library")
    id("ketchup.hilt")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:policy"))
    implementation(project(":core:datastore"))
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)
    testImplementation(libs.coroutines.test)
}
