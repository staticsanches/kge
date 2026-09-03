package dev.staticsanches.kge.resource

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import kotlin.concurrent.Volatile
import kotlin.uuid.Uuid

/**
 * A [KGEResource] that owns one handle: the wrapper hides the native resource
 * behind a fail-fast accessor and registers it with [KGELeakDetector], so a
 * resource that is never [close]d is caught on collection.
 *
 * @param R the wrapped handle type — a buffer, a texture, decoded image data.
 */
interface ResourceWrapper<R> : KGEResource {
    /** The wrapped handle, or fail-fast when the wrapper was closed. */
    val resource: R

    /** The wrapper's identity, stable for its whole lifetime. */
    val uuid: Uuid

    /** True once the resource was closed or reported as leaked. */
    val cleaned: Boolean

    companion object {
        /**
         * Creates a wrapper owning [resource], released by [cleanAction] and
         * registered for leak detection under [representation].
         */
        @KGESensitiveAPI
        operator fun <R> invoke(
            representation: String,
            resource: R,
            cleanAction: KGECleanAction,
        ): ResourceWrapper<R> = DefaultResourceWrapper(representation, resource, cleanAction)
    }
}

private class DefaultResourceWrapper<R>(
    private val representation: String,
    resource: R,
    cleanAction: KGECleanAction,
) : ResourceWrapper<R> {
    override val uuid: Uuid = Uuid.random()

    @Volatile
    private var internalResource: R? = resource

    // Same module and same construction path: register() always returns the
    // internal KGEResourceCleanable for engine-owned wrappers.
    private val cleanable =
        KGELeakDetector.register(this, "$representation (uuid: $uuid)", cleanAction)
            as KGEResourceCleanable

    override val resource: R
        get() =
            internalResource
                ?: throw IllegalStateException(
                    "$representation has already been released and can not be used",
                )

    override val cleaned: Boolean
        get() = cleanable.cleaned

    override fun close() {
        internalResource = null
        cleanable.clean()
    }

    fun onCollectionObserved() = cleanable.onCollectionObserved()
}

/**
 * Fires the collection observation deterministically — the seam the platform
 * collection trigger funnels into. Internal: engine-owned wrappers only.
 */
internal fun ResourceWrapper<*>.onCollectionObserved() {
    require(this is DefaultResourceWrapper)
    this.onCollectionObserved()
}
