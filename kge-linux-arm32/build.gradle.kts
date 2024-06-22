plugins {
    id("dev.staticsanches.kge.library")
}

dependencies {
    api("dev.staticsanches:kge-core:${project.version}")

    implementation(platform(libs.lwjgl.bom))
    runtimeOnly(libs.bundles.lwjgl) {
        artifact {
            classifier = "natives-linux-arm32"
        }
    }
}
