import org.jetbrains.kotlin.gradle.targets.js.yarn.yarn

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    js {
        browser {
            commonWebpackConfig {
                outputFileName = "bundle.js"
                devtool = "source-map"
                devServer?.open = false
            }
        }
        binaries.executable()
        yarn.ignoreScripts = false
    }

    sourceSets {
        jsMain.dependencies {
            api(projects.kgeCore)

            implementation(kotlinWrappers.browser)
            implementation(libs.kotlin.logging)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
