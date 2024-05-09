plugins {
	id("dev.staticsanches.kge.jvm")
	id("org.jetbrains.kotlin.plugin.allopen") version libs.versions.kotlin
}

allOpen {
	annotation("dev.staticsanches.kge.annotations.KGEAllOpen")
}
