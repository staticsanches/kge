plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp.gradle)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotest.plugin)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    js(IR) {
        compilerOptions {
            moduleKind.set(org.jetbrains.kotlin.gradle.dsl.JsModuleKind.MODULE_ES)
        }
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
    }
    wasmJs {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
    }

    val lwjglNatives = lwjglNativesClassifier()

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotest.framework)
            implementation(libs.kotest.assertions)
        }
        webMain.dependencies {
            implementation(npm("harfbuzzjs", libs.versions.harfbuzzjs.get()))
            implementation(
                npm(
                    "@zkl2333/freetype-wasm",
                    libs.versions.freetype.wasm
                        .get(),
                ),
            )
        }
        webTest.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
            implementation(project.dependencies.platform(libs.lwjgl.bom))
            implementation(libs.lwjgl.core)
            implementation(libs.lwjgl.harfbuzz)
            implementation(libs.lwjgl.freetype)
            runtimeOnly(libs.lwjgl.core.get()) {
                artifact {
                    classifier = lwjglNatives
                }
            }
            runtimeOnly(libs.lwjgl.harfbuzz.get()) {
                artifact {
                    classifier = lwjglNatives
                }
            }
            runtimeOnly(libs.lwjgl.freetype.get()) {
                artifact {
                    classifier = lwjglNatives
                }
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

ktlint {
    filter {
        exclude { element -> element.file.invariantSeparatorsPath.contains("/build/generated/") }
    }
}

tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
    exclude { element -> element.file.invariantSeparatorsPath.contains("/build/generated/") }
}
