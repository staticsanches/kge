plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
}

allprojects {
    group = "dev.staticsanches.kge"
    version = "0.1.0"

    if (name != "kge-natives" && parent?.name != "kge-natives") {
        apply(plugin = "org.jlleitschuh.gradle.ktlint")
        configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
            debug = true
            verbose = true
            reporters {
                reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
                reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
                reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.HTML)
            }
        }
    }

    if (name == "kge-core" || parent?.name == "kge-natives") {
        apply(plugin = "com.vanniktech.maven.publish")
        configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            pom {
                name.set("Kotlin Game Engine (kge)")
                description.set("Inspired by olcPixelGame - written in Kotlin")
                url.set("https://staticsanches.github.io/kge/")

                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://github.com/staticsanches/kge/blob/main/LICENSE.md?raw=true")
                        distribution.set("repo")
                    }
                    license {
                        name.set("3rd Party")
                        url.set("https://github.com/staticsanches/kge/blob/main/LICENSE-3RD-PARTY.md?raw=true")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("staticsanches")
                        name.set("Felipe Sanches")
                        email.set("staticsanches@gmail.com")
                    }
                }

                scm {
                    url.set("https://github.com/staticsanches/kge")
                }
            }

            publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
            signAllPublications()
        }
    }
}
