plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp.gradle)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotest.plugin)
}

kotlin {
    // Project-wide opt-in: engine code uses sensitive members without repeating
    // the annotation; external consumers still face the compile-time error.
    compilerOptions {
        optIn.add("dev.staticsanches.kge.annotations.KGESensitiveAPI")
        // ByteBuffer is an expect/actual class (JDK-NIO typealias on JVM,
        // TypedArray emulation on web) — a Beta feature (KT-61573) that the
        // compiler recommends silencing with this flag.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
            // java.nio.ByteBuffer is `sealed` since JDK 21; the expect/actual
            // modality check fails against the running JDK's metadata, so the
            // JVM API surface is taken from the JDK 11 release (plain abstract
            // class). Runtime stays the JDK 21 class.
            freeCompilerArgs.add("-Xjdk-release=11")
        }
    }
    // The web targets are browser-only: the engine runs in the browser and the
    // Karma/ChromeHeadless tasks are the web suites. Node is intentionally not
    // a target — browser-only capabilities such as createImageBitmap must stay
    // reachable and testable.
    js(IR) {
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

    val lwjglNatives =
        run {
            val osName = System.getProperty("os.name")!!
            val osArch = System.getProperty("os.arch")!!
            when {
                "FreeBSD" == osName -> {
                    "natives-freebsd"
                }

                arrayOf("Linux", "SunOS", "Unit").any { osName.startsWith(it) } -> {
                    if (arrayOf("arm", "aarch64").any { osArch.startsWith(it) }) {
                        "natives-linux${
                            if (osArch.contains("64") || osArch.startsWith("armv8")) {
                                "-arm64"
                            } else {
                                "-arm32"
                            }
                        }"
                    } else if (osArch.startsWith("ppc")) {
                        "natives-linux-ppc64le"
                    } else if (osArch.startsWith("riscv")) {
                        "natives-linux-riscv64"
                    } else {
                        "natives-linux"
                    }
                }

                arrayOf("Mac OS X", "Darwin").any { osName.startsWith(it) } -> {
                    "natives-macos${if (osArch.startsWith("aarch64")) "-arm64" else ""}"
                }

                arrayOf("Windows").any { osName.startsWith(it) } -> {
                    if (osArch.contains("64")) {
                        "natives-windows${if (osArch.startsWith("aarch64")) "-arm64" else ""}"
                    } else {
                        "natives-windows-x86"
                    }
                }

                else -> {
                    error("unsupported OS/arch for LWJGL natives: $osName/$osArch")
                }
            }
        }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlin.logging)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kotlinx.coroutines.core)
        }
        webMain.dependencies {
            implementation(libs.kotlin.js)
            implementation(libs.kotlinx.browser)
            // The GL handles and the WebGL2 backend are kotlin-wrappers
            // `web.gl` DOM types, not the kotlinx browser ones.
            implementation(libs.kotlin.browser)
        }
        jvmMain.dependencies {
            // kotlin-logging 8.0.4 (jvm variant) dropped its compile-scope
            // slf4j-api dependency, but its JVM logger factory needs it at
            // runtime; declare it explicitly.
            implementation(libs.slf4j.api)
            // BOM in `platform()` form supplies the versionless lwjgl-core and
            // lwjgl-stb (PNG via STB).
            implementation(project.dependencies.platform(libs.lwjgl.bom))
            implementation(libs.lwjgl.core)
            implementation(libs.lwjgl.stb)
            implementation(libs.lwjgl.opengl)
            // The device seam's JVM implementation drives a GLFW window's
            // context; the window itself is created by the owner (window
            // concept, later).
            implementation(libs.lwjgl.glfw)
            // Host natives go on the production runtime classpath so a downstream
            // JVM consumer can load LWJGL (the test source set inherits them).
            runtimeOnly(libs.lwjgl.core.get()) {
                artifact {
                    classifier = lwjglNatives
                }
            }
            runtimeOnly(libs.lwjgl.stb.get()) {
                artifact {
                    classifier = lwjglNatives
                }
            }
            runtimeOnly(libs.lwjgl.opengl.get()) {
                artifact {
                    classifier = lwjglNatives
                }
            }
            runtimeOnly(libs.lwjgl.glfw.get()) {
                artifact {
                    classifier = lwjglNatives
                }
            }
        }
        commonTest.dependencies {
            implementation(libs.kotest.framework)
            implementation(libs.kotest.assertions)
        }
        // The WebGL2 smoke probe uses kotlin-wrappers `web.*`; the test source
        // set does not inherit webMain's `implementation` deps.
        webTest.dependencies {
            implementation(libs.kotlin.browser)
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
