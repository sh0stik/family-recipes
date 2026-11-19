plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    js(IR) {
        moduleName = "composeApp"
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
                devServer = (devServer ?: org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.DevServer()).apply {
                    port = 8081
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(project(":shared"))
        }
    }
}

// No source maps in the production bundle (jsBrowserDistribution, what goes to the website): they
// were published next to composeApp.js and handed anyone the app's own Kotlin source. Development
// builds (jsBrowserRun, jsBrowserDevelopmentWebpack) keep them for debugging.
tasks.named<org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack>("jsBrowserProductionWebpack") {
    sourceMaps = false
}

// Neither the webpack output folder nor the distribution folder is emptied between builds, so maps
// from earlier builds would otherwise keep being copied and uploaded.
tasks.named<Copy>("jsBrowserDistribution") {
    exclude("**/*.map")
    doFirst {
        destinationDir.walkTopDown().filter { it.isFile && it.extension == "map" }.forEach { it.delete() }
    }
}