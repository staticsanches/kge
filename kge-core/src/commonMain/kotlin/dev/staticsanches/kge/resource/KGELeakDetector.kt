package dev.staticsanches.kge.resource

/**
 * Registers resources for collection-based leak detection. A resource is
 * registered once, when its owning wrapper is created; if the wrapper is
 * collected without [KGECleanable.clean], [LeakReporterService] reports it.
 */
object KGELeakDetector {
    /**
     * Registers [obj] for collection-based leak detection.
     *
     * The mechanism holds no strong references: [action] must not capture
     * [obj], or the leak would never be observed.
     */
    fun register(
        obj: Any,
        representation: String,
        action: KGECleanAction,
    ): KGECleanable = KGEResourceCleanable(obj, representation, action)
}

/** Engine-side [KGECleanable]: the state machine plus a platform collection trigger. */
internal class KGEResourceCleanable(
    obj: Any,
    representation: String,
    action: KGECleanAction,
) : KGECleanable {
    private val state = KGEResourceCleanableState(representation, action)
    private val trigger = registerCollectionTrigger(obj, state::onCollected)

    override val cleaned: Boolean
        get() = state.cleaned

    // Load-bearing order: the state is claimed BEFORE the platform
    // unregister. On JVM an unregister actually runs the registered
    // collection trigger (Cleaner.Cleanable.clean() invokes the action
    // unconditionally), so without the claim every close would report a
    // spurious leak and lose the release action.
    override fun clean() {
        state.clean()
        trigger.unregister()
    }

    /** Deterministic test seam: fires the platform collection trigger. */
    internal fun onCollectionObserved() = state.onCollected()
}

/** The platform registration of one collection trigger. */
internal interface KGECleanableHandle {
    fun unregister()
}

/**
 * Registers [onCollected] to run when the host collects [obj] — a
 * `java.lang.ref.Cleaner` cleanup on JVM, a FinalizationRegistry callback on
 * the web targets.
 *
 * [KGECleanableHandle.unregister] is always accepted, but delivery is
 * platform-shaped: on web it cancels the registration; on JVM it runs the
 * callback unconditionally. Callers must claim the resource state before
 * unregistering.
 */
internal expect fun registerCollectionTrigger(
    obj: Any,
    onCollected: () -> Unit,
): KGECleanableHandle
