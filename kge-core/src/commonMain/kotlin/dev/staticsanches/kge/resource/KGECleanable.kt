package dev.staticsanches.kge.resource

/** The close/leak handle for one resource registered with [KGELeakDetector]. */
interface KGECleanable {
    /** True once the resource is either closed or reported as leaked. */
    val cleaned: Boolean

    /** Closes the resource: runs the release action exactly once. */
    fun clean()
}
