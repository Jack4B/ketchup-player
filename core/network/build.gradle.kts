plugins {
    id("ketchup.jvm.library")
}

dependencies {
    api(libs.okhttp)
    api(libs.coroutines.core)
    // api: NetworkException ma supertyp SafeErrorMessage z core:common, widoczny dla modułów używających wyjątku.
    api(project(":core:common"))
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.coroutines.test)
}
