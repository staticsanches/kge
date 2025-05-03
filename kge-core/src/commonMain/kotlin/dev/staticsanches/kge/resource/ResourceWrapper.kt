package dev.staticsanches.kge.resource

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import kotlin.concurrent.Volatile

interface ResourceWrapper<R> : KGEResource {
    val resource: R

    operator fun invoke(): R = resource

    companion object {
        @KGESensitiveAPI
        inline operator fun <R> invoke(
            representationProvider: (R) -> String,
            resourceProvider: () -> R,
            resourceCleanerProvider: (R) -> KGECleanAction,
        ): ResourceWrapper<R> =
            resourceProvider().let { ResourceWrapper(representationProvider(it), it, resourceCleanerProvider(it)) }

        @KGESensitiveAPI
        operator fun <R> invoke(
            representation: String,
            resource: R,
            action: KGECleanAction,
        ): ResourceWrapper<R> = DefaultResourceWrapper(representation, resource, action)
    }
}

class DefaultResourceWrapper<R>
    @KGESensitiveAPI
    constructor(
        private val representation: String,
        resource: R,
        cleanAction: KGECleanAction,
    ) : ResourceWrapper<R> {
        override val resource: R
            get() =
                internalResource
                    ?: throw IllegalStateException("$representation has already been released and can not be used")

        @Volatile
        private var internalResource: R? = resource
        private val cleanable: KGECleanable = KGELeakDetector.register(this, representation, cleanAction)

        override fun close() {
            internalResource = null
            cleanable.clean()
        }

        override fun toString(): String = if (cleanable.cleaned) "$representation (released)" else representation
    }
