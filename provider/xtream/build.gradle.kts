plugins {
    id("ketchup.jvm.library")
}

dependencies {
    api(project(":provider:api"))
    implementation(project(":core:network"))
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)
    testImplementation(libs.coroutines.test)
}
