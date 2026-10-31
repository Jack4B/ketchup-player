plugins {
    id("ketchup.android.library")
}

dependencies {
    api(project(":player:engine"))
    api(project(":core:policy"))
    implementation(project(":core:model"))
    implementation(libs.coroutines.core)
    testImplementation(libs.coroutines.test)
}
