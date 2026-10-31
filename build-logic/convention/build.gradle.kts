plugins {
    `kotlin-dsl`
}

group = "pl.ketchupplayer.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.compiler.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    compileOnly(libs.spotless.gradlePlugin)
    testImplementation(libs.junit)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "ketchup.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "ketchup.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("jvmLibrary") {
            id = "ketchup.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "ketchup.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("hilt") {
            id = "ketchup.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("quality") {
            id = "ketchup.quality"
            implementationClass = "QualityConventionPlugin"
        }
        register("moduleRules") {
            id = "ketchup.module-rules"
            implementationClass = "ModuleRulesConventionPlugin"
        }
    }
}
