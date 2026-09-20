@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp.gradle)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotest.plugin)
}

kotlin {
    // The benchmark runs on the two shipped backends: the JVM (GLFW/OpenGL) and
    // the wasmJs browser (WebGL2). It is an application, not part of the
    // library, so it lives in its own module and depends on the published core.
    jvm()

    wasmJs {
        browser {
            testTask {
                useKarma {
                    useChromeHeadlessNoSandbox()
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":kge-core"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotest.framework)
            implementation(libs.kotest.assertions)
        }
        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
        }
        wasmJsMain.dependencies {
            implementation(libs.kotlin.browser)
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// The multiplatform JVM target registers `jvmRun`, but its main class wiring is
// not reachable from the DSL; a plain JavaExec over the main compilation is
// explicit. No `-XstartOnFirstThread` on macOS: the core installs the GLFW
// `glfw_async` library there, which drives the event loop on the process main
// thread; combining it with the first-thread JVM flag traps (SIGTRAP).
val jvmMainCompilation = kotlin.jvm().compilations.getByName("main")

tasks.register<JavaExec>("benchmarkJvm") {
    group = "application"
    description = "Runs the JVM FPS benchmark sweep and prints the result table."
    dependsOn(jvmMainCompilation.compileTaskProvider)
    mainClass.set("dev.staticsanches.kge.benchmark.MainKt")
    classpath = jvmMainCompilation.output.allOutputs + jvmMainCompilation.runtimeDependencyFiles
}

ktlint {
    filter {
        // KSP-generated test discovery code (io.kotest.framework.runtime) is
        // third-party generated — not ours to lint.
        exclude { element -> element.file.invariantSeparatorsPath.contains("/build/generated/") }
    }
}

// ktlint-gradle wires the extension filter only into the check tasks; the
// format tasks need the same exclusion per task.
tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
    exclude { element -> element.file.invariantSeparatorsPath.contains("/build/generated/") }
}
