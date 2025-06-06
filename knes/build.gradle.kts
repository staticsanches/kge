import org.jetbrains.kotlin.gradle.targets.js.yarn.yarn

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    `maven-publish`
}

kotlin {
    js(IR) {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        binaries.library()
        generateTypeScriptDefinitions()
        yarn.ignoreScripts = false
    }

    jvm()
    jvmToolchain(11)

    sourceSets {
        commonMain.dependencies {
            implementation(projects.kgeCore)
        }

        jsMain.dependencies {
            implementation(kotlinWrappers.browser)
            implementation(libs.kotlinx.coroutines.core)
        }

        jvmMain.dependencies {
        }
    }

    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                    compilerOptions.optIn.add("kotlin.ExperimentalStdlibApi")
                }
            }
        }
    }
}
