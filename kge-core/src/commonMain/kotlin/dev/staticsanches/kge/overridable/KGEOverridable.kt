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
 * The engine defines the default implementation; the service's companion
 * object extends [KGEOverridable.Proxy] and acts as the stateless facade,
 * delegating per call to the current implementation. The mechanism owns one
 * process-wide registry of services and each service's current implementation
 * — it is not a generic DI registry; engine wiring between components is
 * plain object composition.
 */
@OptIn(ExperimentalAtomicApi::class)
interface KGEOverridable {
    /**
     * Base of a service facade companion: registers the service on first
     * touch, resolves the current implementation per call and exposes the
     * override entry point.
     *
     * The constructor is internal — services are engine-defined: consumers
     * override them, they do not declare new ones.
     */
    abstract class Proxy<O : KGEOverridable> internal constructor(
        private val serviceType: KClass<O>,
        /**
         * The engine-defined default of this service. Decorators delegate to
         * it to preserve the engine behavior — always the un-overridden
         * implementation, whatever is currently active.
         */
        val original: O,
    ) {
        private val current = AtomicReference(original)

        /**
         * Resolves the current implementation of this service.
         *
         * Resolution happens on every access, so an [override] affects the
         * next call without touching the facade.
         */
        protected val delegate: O
            get() = current.load()

        /**
         * Replaces the service's active implementation with [impl] for the
         * whole process. Every consumer observes the new behavior from the
         * next call on, there is no per-consumer scope, and a later
         * [override] supersedes the previous one (last-declared-wins). There
         * is no public undo; an override that does not delegate to [original]
         * silently replaces the engine default.
         */
        @KGESensitiveAPI
        fun override(impl: O): Unit = current.store(impl)

        internal fun clearOverride() = current.store(original)

        init {
            register(this)
        }

        companion object {
            private val proxies = AtomicReference<PersistentMap<KClass<*>, Proxy<*>>>(persistentHashMapOf())

            /**
             * Discards every override of every service and restores the
             * engine defaults. There is no per-service undo and no scoping —
             * this call is the sole path back, and it hits all services at
             * once. Call it only at lifecycle boundaries: the engine does so
             * on destroy; tests at teardown.
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
