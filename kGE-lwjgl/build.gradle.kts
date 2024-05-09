plugins {
	id("dev.staticsanches.kge.jvm")
}

dependencies {
	api(project(":kGE-core"))

	implementation(platform(libs.lwjgl.bom))
	implementation(libs.bundles.lwjgl)

	testRuntimeOnly(libs.bundles.lwjgl) {
		artifact {
			classifier = LWJGLUtils.lwjglNatives
		}
	}
}
