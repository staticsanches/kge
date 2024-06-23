plugins {
    id("dev.staticsanches.kge.library")
}

dependencies {
    api(project(":kge-core"))

    implementation(platform(libs.lwjgl.bom))
    runtimeOnly(libs.bundles.lwjgl) {
        artifact {
            classifier = "natives-linux"
        }
    }
}
