package dev.staticsanches.kge.resource

/**
 * The per-resource lifecycle handle returned by [KGELeakDetector.register]:
 * the engine's view of one registered resource, holding the close/leak state
 * machine for it.
 */
interface KGECleanable {
    /** True once the resource is either closed or reported as leaked. */
    val cleaned: Boolean

    /** Closes the resource: runs the release action exactly once. */
    fun clean()
}
