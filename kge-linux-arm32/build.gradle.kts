plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
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
            classifier = "natives-linux-arm32"
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("jvm") {
            from(components["java"])
        }
    }
}
