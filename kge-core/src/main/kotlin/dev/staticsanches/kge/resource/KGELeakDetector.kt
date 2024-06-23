@file:Suppress("unused")

package dev.staticsanches.kge.resource

import dev.staticsanches.kge.utils.invokeForAll
import java.lang.ref.Cleaner
import java.lang.ref.Cleaner.Cleanable

typealias KGECleanAction = () -> Unit

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
}

/**
 * Infix function to allow combination of [KGECleanAction]s.
 */
infix fun KGECleanAction.andThen(other: KGECleanAction): KGECleanAction {
    if (this is KGECombinedCleanAction) {
        if (other is KGECombinedCleanAction) {
            actions.addAll(other.actions)
        } else {
            actions.add(other)
        }
        return this
    }
    if (other is KGECombinedCleanAction) {
        return other.apply { actions.add(0, this@andThen) }
    }
    return KGECombinedCleanAction(mutableListOf(this, other))
}

private class KGECombinedCleanAction(
    val actions: MutableList<KGECleanAction>,
) : KGECleanAction {
    override fun invoke() = actions.invokeForAll { it() }
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
            System.err.println("$objRepresentation was not cleaned and is potentially leaking its resources")
            action = null
        }
    }
}
