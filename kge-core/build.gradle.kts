plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp.gradle)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotest.plugin)
}

kotlin {
    // Project-wide opt-in: the engine's own code and tests use the sensitive
    // members without repeating the annotation — external consumers still face
    // the compile-time opt-in error.
    compilerOptions {
        optIn.add("dev.staticsanches.kge.annotations.KGESensitiveAPI")
        // ByteBuffer is an expect/actual class (JDK-NIO typealias on JVM, TypedArray
        // emulation on web). That language feature is still Beta (KT-61573) and the
        // compiler's own recommendation is to silence it with this flag.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
            // java.nio.ByteBuffer is `sealed` since JDK 21; the typealias-actual
            // modality check (expect abstract vs sealed target) fails against the
            // running JDK's metadata, so the JVM API surface is taken from the JDK
            // 11 release where the class is a plain abstract class. Runtime stays
            // the JDK 21 class.
            freeCompilerArgs.add("-Xjdk-release=11")
        }
    }
    // The web targets are browser-only: the engine runs in the browser, and the
    // browser test tasks (ChromeHeadless via Karma) are the web suites. Node is
    // intentionally not a target — browser-only capabilities such as
    // createImageBitmap must be reachable and testable.
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

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlin.logging)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kotlinx.coroutines.core)
        }
        webMain.dependencies {
            implementation(libs.kotlin.js)
            implementation(libs.kotlinx.browser)
        }
        jvmMain.dependencies {
            // kotlin-logging 8.0.4 (jvm variant) dropped the compile-scope
            // slf4j-api dependency: its JVM logger factory needs it at runtime,
            // so the engine declares it explicitly.
            implementation(libs.slf4j.api)
            // Native memory via LWJGL: the BOM in the `platform()` form supplies
            // the versionless lwjgl-core and lwjgl-stb (PNG via STB).
            implementation(project.dependencies.platform(libs.lwjgl.bom))
            implementation(libs.lwjgl.core)
            implementation(libs.lwjgl.stb)
        }
        commonTest.dependencies {
            implementation(libs.kotest.framework)
            implementation(libs.kotest.assertions)
        }
        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)

            val osName = System.getProperty("os.name")!!
            val osArch = System.getProperty("os.arch")!!
            val lwjglNatives =
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
            runtimeOnly(libs.lwjgl.core.get()) {
                artifact {
                    classifier = lwjglNatives
                }
            }
            // STBImage/STBImageWrite are LWJGL bindings over a shared library
            // of their own (liblwjgl_stb); the lwjgl-core natives jar does not
            // carry it, so the STB natives artifact must be on the runtime
            // classpath too.
            runtimeOnly(libs.lwjgl.stb.get()) {
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
        // KSP-generated test discovery code (io.kotest.framework.runtime) lives under
        // build/generated/ and is third-party generated — not ours to lint.
        exclude { element -> element.file.invariantSeparatorsPath.contains("/build/generated/") }
    }
}

// ktlint-gradle 14.2.0 wires the extension filter above only into the check
// tasks; the format tasks (the pre-commit `ktlintFormat`) need the same
// exclusion per task — both task types implement PatternFilterable.
tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
    exclude { element -> element.file.invariantSeparatorsPath.contains("/build/generated/") }
}
