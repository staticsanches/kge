package dev.staticsanches.kge.resource

import io.github.oshai.kotlinlogging.KotlinLogging
import java.lang.ref.Cleaner
import java.lang.ref.Cleaner.Cleanable

private val logger = KotlinLogging.logger {}

interface KGECleanable : Cleanable {
    val cleaned: Boolean
}

/**
 * Wrapper around a [Cleaner] to help in the process of detecting resource leak.
 */
object KGELeakDetector {
    private val cleaner = Cleaner.create()

    /**
     * Register a leak detector to the [obj].
     *
     * To properly work, [action] MUST NOT hold a reference to [obj].
     */
    fun register(
        obj: Any,
        objRepresentation: String,
        action: KGECleanAction,
    ): KGECleanable = LeakDetectorAction(obj, objRepresentation, action, cleaner)

    internal fun leakMessage(objRepresentation: String) =
        "$objRepresentation was not cleaned and is potentially leaking its resources"
}

private class LeakDetectorAction(
    obj: Any,
    private val objRepresentation: String,
    @Volatile private var action: KGECleanAction?,
    cleaner: Cleaner,
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
            logger.error { KGELeakDetector.leakMessage(objRepresentation) }
            action = null
        }
    }
}
