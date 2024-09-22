package dev.staticsanches.kge.engine.extension

import dev.staticsanches.kge.engine.KotlinGameEngine

/**
 * Kotlin Game Engine Extension - Permits access to KGE functions from extension.
 */
interface KGEX {
    fun onBeforeUserCreate() = Unit

    fun onAfterUserCreate() = Unit

    /**
     * Called every frame before [KotlinGameEngine.onUserUpdate].
     *
     * @return true if [KotlinGameEngine.onUserUpdate] should be blocked.
     */
    fun onBeforeUserUpdate(): Boolean = false

    fun onAfterUserUpdate() = Unit

    fun onBeforeUserDestroy() = Unit

    fun onAfterUserDestroy() = Unit
}
