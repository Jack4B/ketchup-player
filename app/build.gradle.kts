plugins {
    id("ketchup.android.application")
    id("ketchup.android.compose")
    id("ketchup.hilt")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "pl.ketchupplayer.tv"
    defaultConfig {
        applicationId = "pl.ketchupplayer.tv"
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["leanbackRequired"] = "false"
        }
        release {
            manifestPlaceholders["leanbackRequired"] = "true"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Podpis release kluczem CI dodaje workflow (sekrety poza repo).
        }
    }
    buildFeatures { buildConfig = true }
    // AAPT2 nie rozwiązuje @mipmap/ic_launcher z folderu bez -v26, mimo minSdk 28.
    lint { disable += "ObsoleteSdkInt" }
    testOptions.unitTests.all {
        it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:security"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:datastore"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:providers"))
    implementation(project(":feature:livetv"))
    implementation(project(":feature:guide"))
    implementation(project(":feature:player-ui"))
    implementation(libs.timber)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.hilt.lifecycle)
    implementation(libs.lifecycle.runtime.compose)
    testImplementation(libs.junit)
    testImplementation(project(":core:testing"))
    testImplementation(libs.hilt.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(project(":core:policy"))
    testImplementation(project(":player:engine"))
    kspTest(libs.hilt.compiler)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
