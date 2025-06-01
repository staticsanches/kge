@file:Suppress("ktlint:standard:filename", "unused")

package dev.staticsanches.kge.engine.addon

actual interface CallbacksAddon {
    /**
     * Called once on application startup, use to load your resources.
     */
    suspend fun onUserCreate() = Unit

    /**
     * Called every frame.
     *
     * @return if the game should continue running.
     */
    suspend fun onUserUpdate(): Boolean = true

    /**
     * Called once on application termination, so you can clean the loaded resources.
     *
     * @return if the shutdown process should continue.
     */
    suspend fun onUserDestroy(): Boolean = true
}
