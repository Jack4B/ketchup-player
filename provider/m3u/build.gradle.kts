plugins {
    id("ketchup.jvm.library")
}

dependencies {
    api(project(":provider:api"))
    implementation(libs.coroutines.core)
    testImplementation(libs.coroutines.test)
}
