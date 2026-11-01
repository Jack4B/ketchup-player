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
        // Wersję podaje wydanie (workflow release: -PversionName=1.0.0 -PversionCode=100); lokalnie wersja deweloperska.
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "0.1.0"
    }
    // Podpis wydania: dane z zmiennych środowiskowych (sekrety CI), nigdy z repozytorium. Bez nich release jest niepodpisany.
    val keystorePath = System.getenv("KETCHUP_KEYSTORE_FILE")
    if (keystorePath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KETCHUP_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KETCHUP_KEY_ALIAS")
                keyPassword = System.getenv("KETCHUP_KEY_PASSWORD")
            }
        }
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
            signingConfigs.findByName("release")?.let { signingConfig = it }
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
    implementation(project(":feature:guide"))
    implementation(project(":feature:vod"))
    implementation(project(":feature:details"))
    implementation(project(":feature:home"))
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
