package dev.staticsanches.kge.resource

import dev.staticsanches.kge.resource.KGELeakDetector.logLeak
import java.lang.ref.Cleaner

internal actual fun KGECleanable(
    obj: Any,
    objRepresentation: String,
    action: KGECleanAction,
): KGECleanable = LeakDetectorAction(obj, objRepresentation, action)

private class LeakDetectorAction(
    obj: Any,
    private val objRepresentation: String,
    @Volatile private var action: KGECleanAction?,
) : KGECleanable,
    Runnable {
    private val selfCleanable = cleaner.register(obj, this)

    override val cleaned: Boolean
        get() = action == null

    override fun clean() {
        if (action == null) {
            return // already cleaned
        }
        synchronized(this) {
            val action = action ?: return // already cleaned
            this.action = null
            selfCleanable.clean()
            action()
        }
    }

    override fun run() {
        if (action != null) {
            logLeak(objRepresentation)
            action = null
        }
    }

    companion object {
        private val cleaner = Cleaner.create()
    }
}
