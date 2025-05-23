import org.jetbrains.kotlin.gradle.targets.js.yarn.yarn

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    `maven-publish`
}

kotlin {
    js {
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
            implementation(libs.kotlin.logging)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        jsMain.dependencies {
            implementation(kotlinWrappers.browser)
            implementation(libs.kotlinx.coroutines.core)

            implementation(npm("pngjs", "7.0.0"))
        }
        jsTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }

        jvmMain.dependencies {
            implementation(libs.slf4j)

            implementation(project.dependencies.platform(libs.lwjgl.bom))
            implementation(libs.bundles.lwjgl)
        }
        jvmTest.dependencies {
            implementation(libs.logback.classic)

            val name = System.getProperty("os.name")!!
            val arch = System.getProperty("os.arch")!!
            val lwjglNatives =
                when {
                    arrayOf("Linux", "SunOS", "Unit").any { name.startsWith(it) } ->
                        if (arrayOf("arm", "aarch64").any { arch.startsWith(it) }) {
                            "natives-linux${
                                if (arch.contains("64") || arch.startsWith("armv8")) {
                                    "-arm64"
                                } else {
                                    "-arm32"
                                }
                            }"
                        } else {
                            "natives-linux"
                        }

                    arrayOf("Mac OS X", "Darwin").any { name.startsWith(it) } ->
                        "natives-macos${if (arch.startsWith("aarch64")) "-arm64" else ""}"

                    arrayOf("Windows").any { name.startsWith(it) } ->
                        if (arch.contains("64")) {
                            "natives-windows${if (arch.startsWith("aarch64")) "-arm64" else ""}"
                        } else {
                            "natives-windows-x86"
                        }

                    else -> throw Error("Unrecognized or unsupported platform. Please set \"lwjglNatives\" manually")
                }

            libs.bundles.lwjgl.get().forEach {
                runtimeOnly(it) {
                    artifact {
                        classifier = lwjglNatives
                    }
                }
            }
        }
    }

    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                    freeCompilerArgs.add("-opt-in=dev.staticsanches.kge.annotations.KGESensitiveAPI")
                }
            }
        }
    }
}
