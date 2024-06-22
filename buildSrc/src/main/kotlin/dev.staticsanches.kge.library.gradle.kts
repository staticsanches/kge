plugins {
    `maven-publish`
    `java-library`
}

group = "dev.staticsanches"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name = "Kotlin Game Engine"
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
                    url = "https://github.com/staticsanches/kge.git"
                }
            }
        }
    }
}

tasks.javadoc {
    if (JavaVersion.current().isJava9Compatible) {
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }
}
