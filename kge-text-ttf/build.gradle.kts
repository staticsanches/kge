plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp.gradle)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotest.plugin)
}

kotlin {
    // The native face is an expect/actual class (a Beta feature): the flag
    // silences the compiler's recommendation, as in kge-core.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

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
                    useChromeHeadlessNoSandbox()
                }
            }
        }
    }
    wasmJs {
        browser {
            testTask {
                useKarma {
                    useChromeHeadlessNoSandbox()
                }
            }
        }
    }

    val lwjglNatives = lwjglNativesClassifier()

    sourceSets {
        commonMain.dependencies {
            // The published axis maps are persistent maps behind the public Map
            // type, so this is not part of the module's ABI.
            implementation(libs.kotlinx.collections.immutable)
            // The public API names KGEResource and Float2D, so consumers
            // compiling against this module need them on their compile classpath.
            api(project(":kge-core"))
        }
        commonTest.dependencies {
            implementation(libs.kotest.framework)
            implementation(libs.kotest.assertions)
            implementation(project(":kge-font-roboto"))
            implementation(project(":kge-test-support"))
        }
        webMain.dependencies {
            // The `org.khronos.webgl` typed arrays the HarfBuzz payload copy
            // uses; on wasmJs they come from kotlinx-browser, not the stdlib.
            implementation(libs.kotlinx.browser)
            // The carrier names the `GLTexture` handle, whose web actual is a
            // kotlin-wrappers `web.gl` DOM type, not a kotlinx browser one.
            implementation(libs.kotlin.browser)
            implementation(libs.kotlinx.coroutines.core)
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
        jvmMain.dependencies {
            implementation(project.dependencies.platform(libs.lwjgl.bom))
            implementation(libs.lwjgl.core)
            implementation(libs.lwjgl.harfbuzz)
            implementation(libs.lwjgl.freetype)
            // Host natives go on the production runtime classpath so a
            // downstream JVM consumer can shape; the test source set inherits
            // them.
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
        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // macOS/AppKit requires GLFW (and GL context work) on the process's first
    // thread; without this the JVM GL smoke test aborts in glfwInit.
    if (System.getProperty("os.name").startsWith("Mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
}

ktlint {
    filter {
        exclude { element -> element.file.invariantSeparatorsPath.contains("/build/generated/") }
    }
}

tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
    exclude { element -> element.file.invariantSeparatorsPath.contains("/build/generated/") }
}

val generateGoldenImages =
    goldenImages(
        taskName = "generateGoldenImages",
        packageName = "dev.staticsanches.kge.golden",
        inputDir = layout.projectDirectory.dir("src/commonTest/golden"),
        outputDir = layout.buildDirectory.dir("generated/golden/commonTest/kotlin"),
    )

kotlin {
    sourceSets {
        commonTest {
            kotlin.srcDir(generateGoldenImages.flatMap { it.outputDir })
        }
    }
}
