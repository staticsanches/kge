package dev.staticsanches.kge.resource

import java.lang.ref.Cleaner

/** JVM collection trigger: a `java.lang.ref.Cleaner` cleanup action. */
internal actual fun registerCollectionTrigger(
    obj: Any,
    onCollected: () -> Unit,
): KGECleanableHandle = JvmCollectionTrigger(obj, onCollected)

private class JvmCollectionTrigger(
    obj: Any,
    onCollected: () -> Unit,
) : KGECleanableHandle {
    private val cleanable = cleaner.register(obj) { onCollected() }

    override fun unregister() {
        cleanable.clean()
    }

    companion object {
        private val cleaner = Cleaner.create()
    }
}
