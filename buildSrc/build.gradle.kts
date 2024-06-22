plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.jvm.plugin)
    implementation(libs.ktlint.plugin)
}
