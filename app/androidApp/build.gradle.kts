plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
}

kotlin {
    androidTarget()
    sourceSets {
        val androidMain by getting {
            dependencies {
                implementation(project(":shared"))
            }
        }
    }
}

android {
    compileSdk = (findProperty("android.compileSdk") as String).toInt()
    namespace = "com.daria.recipes"

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

    defaultConfig {
        applicationId = "com.daria.recipes"
        minSdk = (findProperty("android.minSdk") as String).toInt()
        targetSdk = (findProperty("android.targetSdk") as String).toInt()
        versionCode = 3
        versionName = "1.0"
    }
    // Release signing. Credentials come from Gradle properties, which means they can live in
    // ~/.gradle/gradle.properties — outside this repository, where no `git add -A` can reach them.
    // Never put the keystore or its password in the project tree.
    //
    //   RECIPE_KEYSTORE_FILE=/Users/you/keystores/recipe-release.jks
    //   RECIPE_KEYSTORE_PASSWORD=...
    //   RECIPE_KEY_ALIAS=recipe
    //   RECIPE_KEY_PASSWORD=...
    //
    // Losing this keystore is unrecoverable: the app can never be updated again, only uninstalled
    // and reinstalled. Back it up somewhere that is not this machine.
    val keystorePath = findProperty("RECIPE_KEYSTORE_FILE") as String?
    val hasSigning = !keystorePath.isNullOrBlank() && file(keystorePath).exists()

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = findProperty("RECIPE_KEYSTORE_PASSWORD") as String?
                keyAlias = findProperty("RECIPE_KEY_ALIAS") as String?
                keyPassword = findProperty("RECIPE_KEY_PASSWORD") as String?
            }
        }
    }

    buildTypes {
        // Cleartext HTTP is only allowed in debug (local backend). Release talks https only.
        getByName("debug") {
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        getByName("release") {
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // Fail loudly rather than emitting an unsigned APK that looks fine until Android refuses to
    // install it. An unsigned release build is not a warning-level problem; it is a broken artifact.
    tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
        doFirst {
            check(hasSigning) {
                "Release signing is not configured. Set RECIPE_KEYSTORE_FILE, " +
                    "RECIPE_KEYSTORE_PASSWORD, RECIPE_KEY_ALIAS and RECIPE_KEY_PASSWORD in " +
                    "~/.gradle/gradle.properties, pointing at an existing keystore. " +
                    "See androidApp/build.gradle.kts for the exact keys."
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        jvmToolchain(21)
    }
}
dependencies {
    implementation(libs.androidx.window)
}
