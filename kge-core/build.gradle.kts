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
    }
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    js(IR) {
        browser {
            testTask {}
        }
        nodejs()
    }
    wasmJs {
        browser {
            testTask {}
        }
        nodejs()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlin.logging)
            implementation(libs.kotlinx.collections.immutable)
        }
        webMain.dependencies {
            implementation(libs.kotlin.js)
        }
        jvmMain.dependencies {
            // kotlin-logging 8.0.4 (jvm variant) dropped the compile-scope
            // slf4j-api dependency: its JVM logger factory needs it at runtime,
            // so the engine declares it explicitly.
            implementation(libs.slf4j.api)
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
