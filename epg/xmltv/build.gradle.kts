plugins {
    id("ketchup.jvm.library")
}

dependencies {
    api(project(":core:model"))
    // Na Androidzie org.xmlpull.v1 dostarcza platforma; do testów JVM potrzebny kxml2.
    compileOnly(libs.xmlpull)
    testImplementation(libs.kxml2)
    testImplementation(libs.coroutines.test)
}
