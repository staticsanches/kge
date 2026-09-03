package dev.staticsanches.kge.resource

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * The state of one registered resource: whether it was closed and what
 * happened at the moment the wrapper's collection was observed.
 *
 * A resource has two exit paths: [clean] — explicit close, the action runs
 * exactly once; and [onCollected] — the platform's collection observation,
 * which reports the leak and never runs the action. Both are race-safe:
 * whoever claims the state first wins, and the other side becomes a no-op.
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

    /**
     * Collection was observed: [LeakReporterService.report] is called exactly
     * once — the report loses the race to [clean], never the other way around.
     */
    fun onCollected() {
        if (actionRef.exchange(null) == null) return
        LeakReporterService.report(representation)
    }
}
