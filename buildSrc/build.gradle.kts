plugins {
    `kotlin-dsl`
    // mirrors the `ktlint` version in the root catalog, unreadable from buildSrc
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Tests must execute, never replay (see the root build script).
tasks.withType<AbstractTestTask>().configureEach {
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}
