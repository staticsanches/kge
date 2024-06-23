import com.vanniktech.maven.publish.SonatypeHost
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
    id("com.vanniktech.maven.publish")
    id("org.jlleitschuh.gradle.ktlint")
}

group = "dev.staticsanches"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
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

mavenPublishing {
    coordinates(
        groupId = project.group.toString(),
        artifactId = project.name,
        version = project.version.toString(),
    )

    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    signAllPublications()

    pom {
        name = "kotlin Game Engine"
        description = "Inspired by olcPixelGameEngine, written in kotlin using Lightweight Java Game Library (LWJGL)"
        url = "https://github.com/staticsanches/kge"

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
            url = "https://github.com/staticsanches/kge/tree/main"
        }
    }
}
