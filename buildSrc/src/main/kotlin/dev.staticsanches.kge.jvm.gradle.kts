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
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
	useJUnitPlatform()
}

kotlin {
	jvmToolchain(11)
}

tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask::class.java) {
	compilerOptions {
		freeCompilerArgs.add("-Xcontext-receivers")
	}
}

tasks.named("compileTestKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask::class.java) {
	compilerOptions {
		freeCompilerArgs.add("-Xcontext-receivers")
	}
}
