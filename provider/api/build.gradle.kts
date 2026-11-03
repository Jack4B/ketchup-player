plugins {
    id("ketchup.jvm.library")
}

dependencies {
    api(project(":core:model"))
    api(libs.coroutines.core)
    testImplementation(libs.coroutines.test)
}
