package dev.staticsanches.kge.resource

/**
 * The engine-owned owner of a group of resources, keyed by a typed [Key].
 *
 * The scope itself is a [KGEResource], so the engine can hold it on the same
 * lifecycle path as the resources it owns and close it once at teardown.
 * Resources are closed in reverse registration order (LIFO): a resource
 * registered later may depend on an earlier one, so dependencies must be
 * registered before their dependents.
 *
 * A scope is single-threaded — GL is context/thread affine, and its close
 * drives each resource's release on the registering thread. It must be safely
 * published (created and populated before the thread that uses it starts), so
 * no synchronization is needed. The scope holds no GPU context of its own; a
 * resource that needs a current context makes it current in its own [close].
 */
class ResourceScope : KGEResource {
    /**
     * The marker interface a resource key implements.
     *
     * A key is created by the registrant that owns the resource and matched by
     * identity; its type parameter ties it to that resource's type, so `get`
     * returns the concrete type without a cast at the call site.
     */
    interface Key<T : KGEResource>

    private val resources = mutableMapOf<Key<*>, KGEResource>()
    private var closed = false

    /**
     * Stores [resource] under [key] and returns it. Fails fast when the key is
     * already registered or when the scope is closed.
     */
    fun <T : KGEResource> register(
        key: Key<T>,
        resource: T,
    ): T {
        check(!closed) { "ResourceScope is closed" }
        require(!resources.containsKey(key)) { "Resource key is already registered" }
        resources[key] = resource
        return resource
    }

    /**
     * Returns the resource registered under [key]. Fails fast when the key is
     * absent or when the scope is closed.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : KGEResource> get(key: Key<T>): T {
        check(!closed) { "ResourceScope is closed" }
        val resource = resources[key] ?: throw IllegalStateException("No resource registered for key")
        return resource as T
    }

    /**
     * Closes every registered resource in reverse registration order, then
     * clears the scope. Idempotent: a second call is a no-op. If a resource's
     * close throws, the remaining resources are still closed, the first
     * throwable is rethrown, and the later ones are added to it as suppressed.
     */
    override fun close() {
        if (closed) return
        closed = true

        var failure: Throwable? = null
        for (resource in resources.values.toList().asReversed()) {
            try {
                resource.close()
            } catch (e: Throwable) {
                val first = failure
                if (first == null) failure = e else first.addSuppressed(e)
            }
        }
        resources.clear()

        failure?.let { throw it }
    }
}
