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
    }

    sourceSets {
        jsMain.dependencies {
            api(projects.kgeCore)

            implementation(kotlinWrappers.browser)
            implementation(libs.kotlin.logging)
            implementation(libs.kotlinx.coroutines)
        }
    }
}
