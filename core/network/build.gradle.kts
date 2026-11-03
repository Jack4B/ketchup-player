plugins {
    id("ketchup.jvm.library")
}

dependencies {
    api(libs.okhttp)
    api(libs.coroutines.core)
    implementation(project(":core:common"))
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.coroutines.test)
}
