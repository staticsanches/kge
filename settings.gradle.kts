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
            val wrappersVersion = "2025.5.1"
            from("org.jetbrains.kotlin-wrappers:kotlin-wrappers-catalog:$wrappersVersion")
        }
    }
}

include("kge-core")
include("kge-example-js", "kge-example-jvm")
include(
    "kge-natives",
    "kge-natives:kge-linux",
    "kge-natives:kge-linux-arm32",
    "kge-natives:kge-linux-arm64",
    "kge-natives:kge-macos",
    "kge-natives:kge-macos-arm64",
    "kge-natives:kge-windows",
    "kge-natives:kge-windows-arm64",
    "kge-natives:kge-windows-x86",
)
