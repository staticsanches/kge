package dev.staticsanches.kge.resource

/**
 * Releases the resource held by a [KGEResource] — e.g. deleting a GL object or
 * freeing native memory. Run at most once per resource: if the owning wrapper
 * is collected unclosed, the release is reported as a leak instead.
 */
interface KGECleanAction {
    operator fun invoke()
}

inline fun KGECleanAction(crossinline cleanAction: () -> Unit): KGECleanAction =
    object : KGECleanAction {
        override fun invoke() = cleanAction()
    }
