package dev.staticsanches.kge.resource

/**
 * The action that releases a resource held by a [KGEResource] — e.g. deleting
 * a GL object or freeing native memory. The engine runs it exactly once per
 * resource, on [ResourceWrapper.close], and reports it as a leak when the
 * wrapper is collected unclosed.
 */
interface KGECleanAction {
    operator fun invoke()
}

/** Creates a [KGECleanAction] from the given block. */
inline fun KGECleanAction(crossinline cleanAction: () -> Unit): KGECleanAction =
    object : KGECleanAction {
        override fun invoke() = cleanAction()
    }
