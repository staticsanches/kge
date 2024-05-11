package dev.staticsanches.kge.engine.extension

import dev.staticsanches.kge.engine.KotlinGameEngine
import dev.staticsanches.kge.engine.window.Window


/**
 * Kotlin Game Engine Extension - Permits access to KGE functions from extension.
 */
interface KGEX {

	context(Window)
	fun onBeforeUserCreate() = Unit

	context(Window)
	fun onAfterUserCreate() = Unit

	/**
	 * Called every frame before [KotlinGameEngine.onUserUpdate].
	 *
	 * @return true if [KotlinGameEngine.onUserUpdate] should be blocked.
	 */
	context(Window)
	fun onBeforeUserUpdate(): Boolean = false

	context(Window)
	fun onAfterUserUpdate() = Unit

	context(Window)
	fun onBeforeUserDestroy() = Unit

	context(Window)
	fun onAfterUserDestroy() = Unit

}
