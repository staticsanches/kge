plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    api(projects.kgeCore)

    implementation(platform(libs.lwjgl.bom))
    implementation(libs.bundles.lwjgl)
    runtimeOnly(libs.bundles.lwjgl) {
        artifact {
            classifier = "natives-macos"
        }
    }
}
