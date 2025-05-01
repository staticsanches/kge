rootProject.name = "kge"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenLocal()
        mavenCentral()
    }

    versionCatalogs {
        create("kotlinWrappers") {
            val wrappersVersion = "2025.3.20"
            from("org.jetbrains.kotlin-wrappers:kotlin-wrappers-catalog:$wrappersVersion")
        }
    }
}

include("kge-core")
include("kge-example-js", "kge-example-jvm")
include("kge-linux", "kge-linux-arm32", "kge-linux-arm64")
include("kge-macos", "kge-macos-arm64")
include("kge-windows", "kge-windows-arm64", "kge-windows-x86")
