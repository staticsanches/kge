import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    kotlin("jvm")
    `maven-publish`
    id("org.jlleitschuh.gradle.ktlint")
}

group = "dev.staticsanches.kge"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}

tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask::class.java) {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")
        freeCompilerArgs.add("-Xopt-in=dev.staticsanches.kge.annotations.KGESensitiveAPI")
    }
}

tasks.named("compileTestKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask::class.java) {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}

ktlint {
    debug = true
    verbose = true
    reporters {
        reporter(ReporterType.PLAIN)
        reporter(ReporterType.CHECKSTYLE)
        reporter(ReporterType.HTML)
    }
}
