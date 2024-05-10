plugins {
	id("dev.staticsanches.kge.jvm")
	id("org.jetbrains.kotlin.plugin.allopen") version libs.versions.kotlin
}

dependencies {
	implementation(platform(libs.lwjgl.bom))
	implementation(libs.bundles.lwjgl)

	testRuntimeOnly(libs.bundles.lwjgl) {
		artifact {
			classifier = LWJGLUtils.lwjglNatives
		}
	}
}

allOpen {
	annotation("dev.staticsanches.kge.annotations.KGEAllOpen")
}
