@file:Suppress("unused")

package dev.staticsanches.kge.configuration

data object Configuration {
    var useOpenGL11: Boolean = System.getProperty("dev.staticsanches.kge.useOpenGL11").toBoolean()
}
