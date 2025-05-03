package dev.staticsanches.kge.resource

import io.github.oshai.kotlinlogging.KotlinLogging

interface KGECleanable {
    val cleaned: Boolean

    fun clean()
}

object KGELeakDetector {
    /**
     * Register a leak detector to the [obj].
     *
     * To properly work, [action] MUST NOT hold a reference to [obj].
     */
    fun register(
        obj: Any,
        objRepresentation: String,
        action: KGECleanAction,
    ): KGECleanable = KGECleanable(obj, objRepresentation, action)

    internal fun logLeak(objRepresentation: String) = logger.error { leakMessage(objRepresentation) }

    internal fun leakMessage(objRepresentation: String) =
        "$objRepresentation was not cleaned and is potentially leaking its resources"

    private val logger = KotlinLogging.logger("KGELeakDetector")
}

internal expect fun KGECleanable(
    obj: Any,
    objRepresentation: String,
    action: KGECleanAction,
): KGECleanable
