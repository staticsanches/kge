package dev.staticsanches.kge.resource

import dev.staticsanches.kge.resource.KGELeakDetector.logLeak
import js.memory.FinalizationRegistry

internal actual fun KGECleanable(
    obj: Any,
    objRepresentation: String,
    action: KGECleanAction,
): KGECleanable = LeakDetectorAction(obj, objRepresentation, action)

private class LeakDetectorAction(
    obj: Any,
    objRepresentation: String,
    private var action: KGECleanAction?,
) : KGECleanable {
    init {
        registry.register(obj, objRepresentation, this)
    }

    override val cleaned: Boolean
        get() = action == null

    override fun clean() {
        val action = action ?: return // already cleaned
        this.action = null
        registry.unregister(this)
        action()
    }

    companion object {
        private val registry = FinalizationRegistry<String> { logLeak(it) }
    }
}
