package dev.staticsanches.kge.resource

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * The race-safe state of one registered resource. Close and collection are the
 * two exit paths: whoever claims the state first wins, the other becomes a
 * no-op. [clean] runs the release action exactly once; [onCollected] reports
 * the leak instead.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class KGEResourceCleanableState(
    private val representation: String,
    action: KGECleanAction,
) {
    private val actionRef = AtomicReference<KGECleanAction?>(action)

    /** True once the resource is either closed or reported as leaked. */
    val cleaned: Boolean
        get() = actionRef.load() == null

    /** Runs the release action exactly once, when the close wins the race. */
    fun clean() {
        actionRef.exchange(null)?.invoke()
    }

    /** Collection observed: reports the leak exactly once, unless [clean] won. */
    fun onCollected() {
        if (actionRef.exchange(null) == null) return
        LeakReporterService.report(representation)
    }
}
