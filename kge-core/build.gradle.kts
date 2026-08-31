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
