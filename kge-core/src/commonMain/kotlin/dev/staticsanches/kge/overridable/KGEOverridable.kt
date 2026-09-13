package dev.staticsanches.kge.overridable

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.update
import kotlin.reflect.KClass

/**
 * Marks an engine-defined behavior that a consumer may replace at runtime.
 *
 * The mechanism keeps one process-wide registry of active implementations —
 * it is not a general DI registry; engine wiring is plain object composition.
 */
@OptIn(ExperimentalAtomicApi::class)
interface KGEOverridable {
    /**
     * Base of a service facade companion. Its constructor is internal —
     * services are engine-defined: consumers override them, they do not
     * declare new ones.
     */
    abstract class Proxy<O : KGEOverridable> internal constructor(
        private val serviceType: KClass<O>,
        /**
         * The engine-defined default, always the un-overridden implementation.
         */
        val original: O,
    ) {
        private val current = AtomicReference(original)

        /** The current implementation, resolved on every access. */
        protected val delegate: O
            get() = current.load()

        /**
         * Replaces the active implementation for the whole process
         * (last-declared-wins, no per-consumer scope, no public undo). An
         * override that does not delegate to [original] silently drops the
         * engine default.
         */
        @KGESensitiveAPI
        fun override(impl: O): Unit = current.store(impl)

        private fun clearOverride() = current.store(original)

        init {
            register(this)
        }

        companion object {
            private val proxies = AtomicReference<PersistentMap<KClass<*>, Proxy<*>>>(persistentHashMapOf())

            /**
             * Restores every service's engine default and discards all
             * overrides — the sole path back. Call only at lifecycle
             * boundaries (engine destroy, test teardown).
             */
            @KGESensitiveAPI
            internal fun resetAll() {
                proxies.load().values.forEach { proxy -> proxy.clearOverride() }
            }

            private fun register(proxy: Proxy<*>) {
                proxies.update { services ->
                    require(proxy.serviceType !in services) {
                        "Service ${proxy.serviceType} is already registered."
                    }
                    services.putting(proxy.serviceType, proxy)
                }
            }
        }
    }
}
