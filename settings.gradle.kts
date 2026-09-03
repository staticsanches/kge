pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories { mavenCentral() }
}
rootProject.name = "kge"
include("kge-core")
