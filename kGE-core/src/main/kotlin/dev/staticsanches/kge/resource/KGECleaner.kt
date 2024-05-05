@file:Suppress("unused")

package dev.staticsanches.kge.resource

import dev.staticsanches.kge.utils.invokeForAll
import java.lang.ref.Cleaner
import java.lang.ref.Cleaner.Cleanable

typealias KGECleanAction = () -> Unit

/**
 * Wrapper around [Cleaner] to help in the process of cleaning resources. Can help in resource leak detection.
 */
object KGECleaner {

	private val cleaner = Cleaner.create()

	/**
	 * @see [Cleaner.register]
	 */
	fun register(obj: Any, action: Runnable): Cleanable = cleaner.register(obj, action)

	/**
	 * Register a leak detector to the [obj].
	 *
	 * To properly work, [action] MUST NOT hold a reference to [obj].
	 */
	fun registerLeakDetector(obj: Any, objRepresentation: String, action: KGECleanAction): Cleanable =
		KGELeakDetector(obj, objRepresentation, action)

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

private class KGECombinedCleanAction(val actions: MutableList<KGECleanAction>) : KGECleanAction {

	override fun invoke() = actions.invokeForAll { it() }

}

private class KGELeakDetector(
	obj: Any, private val objRepresentation: String, @Volatile private var action: KGECleanAction?
) : Cleanable, Runnable {

	private val selfCleanable = KGECleaner.register(obj, this)

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
