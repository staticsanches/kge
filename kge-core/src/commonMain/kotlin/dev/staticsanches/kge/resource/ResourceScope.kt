package dev.staticsanches.kge.resource

/**
 * Owns a group of resources keyed by a typed [Key] and closes them in reverse
 * registration order (LIFO), so dependencies must be registered before their
 * dependents.
 *
 * The scope is single-threaded: it drives each resource's release on the
 * registering thread, so it must be safely published (populated before the
 * using thread starts). It holds no GPU context; a resource that needs one
 * makes it current in its own [close].
 */
class ResourceScope : KGEResource {
    /**
     * The key a resource is registered under. Created and owned by the
     * registrant and matched by identity; the type parameter ties it to the
     * resource's type, so `get` returns the concrete type without a cast.
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
