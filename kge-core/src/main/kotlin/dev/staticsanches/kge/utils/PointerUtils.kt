@file:Suppress("unused")

package dev.staticsanches.kge.utils

internal inline fun <reified T> Long.pointerRepresentation(): String = pointerRepresentation(T::class.java.simpleName)

internal fun Long.pointerRepresentation(type: String): String = String.format("%s pointer [0x%X]", type, this)
