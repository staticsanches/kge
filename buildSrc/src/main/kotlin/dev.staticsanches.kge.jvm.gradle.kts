import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    kotlin("jvm")
    `maven-publish`
    id("org.jlleitschuh.gradle.ktlint")
}

group = "dev.staticsanches"
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

java {
    withJavadocJar()
    withSourcesJar()
}

tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask::class.java) {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")
        freeCompilerArgs.add("-opt-in=dev.staticsanches.kge.annotations.KGESensitiveAPI")
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

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name = "Kotlin Game Engine"
                url = "https://github.com/staticsanches/kge"
                packaging = "jar"
                developers {
                    developer {
                        id = "staticsanches"
                        name = "Felipe Sanches"
                        email = "staticsanches@gmail.com"
                        url = "https://github.com/staticsanches"
                    }
                }
                licenses {
                    license {
                        name = "MIT"
                        url = "https://github.com/staticsanches/kge?tab=MIT-1-ov-file#readme"
                        distribution = "repo"
                    }
                }
                scm {
                    connection = "scm:git:https://github.com/staticsanches/kge.git"
                    developerConnection = "scm:git:https://github.com/staticsanches/kge.git"
                    url = "https://github.com/staticsanches/kge.git"
                }
            }
        }
    }
}

tasks.javadoc {
    if (JavaVersion.current().isJava9Compatible) {
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }
}
