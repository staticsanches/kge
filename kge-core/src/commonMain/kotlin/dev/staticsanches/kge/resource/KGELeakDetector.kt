package dev.staticsanches.kge.resource

/**
 * Registers resources for leak detection.
 *
 * A resource is registered exactly once, at the moment it is created and
 * owned by a [KGEResource]. When the wrapper is collected without a prior
 * [KGECleanable.clean], [LeakReporterService] reports the leak; the exact
 * close/leak race semantics live in [KGEResourceCleanableState].
 */
object KGELeakDetector {
    /**
     * Registers [obj] for collection-based leak detection.
     *
     * The mechanism holds no strong references: [obj] must become collectable
     * when the owning wrapper becomes collectable, and [action] must not hold
     * a reference to [obj] (it would keep [obj] alive and the leak would never
     * be observed).
     */
    fun register(
        obj: Any,
        representation: String,
        action: KGECleanAction,
    ): KGECleanable = KGEResourceCleanable(obj, representation, action)
}

/**
 * The engine-side cleanable: common state machine plus the platform-registered
 * collection trigger ([registerCollectionTrigger]).
 */
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
 * An [KGECleanableHandle.unregister] is always accepted, but the delivery guarantee is
 * platform-shaped: on web it genuinely cancels the registration; on JVM it
 * runs the registration callback unconditionally, so a call after the state
 * was claimed (see [KGEResourceCleanableState]) reaches a no-op state machine
 * and delivers nothing. Callers must claim the resource state before
 * unregistering.
 */
internal expect fun registerCollectionTrigger(
    obj: Any,
    onCollected: () -> Unit,
): KGECleanableHandle
