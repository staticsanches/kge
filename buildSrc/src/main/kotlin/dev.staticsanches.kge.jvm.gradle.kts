plugins {
	kotlin("jvm")
	`maven-publish`
}

group = "dev.staticsanches.kge"
version = "0.0.1-SNAPSHOT"

repositories {
	mavenCentral()
}

dependencies {
	testImplementation(kotlin("test"))
}

tasks.test {
	useJUnitPlatform()
}

kotlin {
	jvmToolchain(11)
}
