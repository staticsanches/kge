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

    sourceSets {
        commonMain.dependencies {
            // The public signatures name Pixmap and Sprite, so a consumer
            // compiling against this module needs core on its classpath.
            api(project(":kge-core"))
        }
        webMain.dependencies {
            // The web handle factories fabricate kotlin-wrappers `web.gl` DOM
            // handles; the test source sets do not inherit them.
            implementation(libs.kotlin.browser)
        }
        jvmMain.dependencies {
            // The real-GL test device drives a hidden GLFW window and its GL 3.3
            // context; the consumer's test runtime supplies the natives through
            // kge-core, so compile-only is enough.
            compileOnly(project.dependencies.platform(libs.lwjgl.bom))
            compileOnly(libs.lwjgl.core)
            compileOnly(libs.lwjgl.opengl)
            compileOnly(libs.lwjgl.glfw)
        }
        commonTest.dependencies {
            implementation(libs.kotest.framework)
            implementation(libs.kotest.assertions)
        }
        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

ktlint {
    filter {
        // KSP-generated test discovery code (io.kotest.framework.runtime) under
        // build/generated/ is third-party generated — not ours to lint.
        exclude { element -> element.file.invariantSeparatorsPath.contains("/build/generated/") }
    }
}

// ktlint-gradle 14.2.0 wires the extension filter only into the check tasks;
// the format tasks need the same exclusion per task (both are PatternFilterable).
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
