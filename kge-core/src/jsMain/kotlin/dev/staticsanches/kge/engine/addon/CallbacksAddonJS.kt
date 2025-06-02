@file:Suppress("ktlint:standard:filename", "unused")

package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.engine.state.input.KeyboardKey
import dev.staticsanches.kge.engine.state.input.KeyboardKeyAction
import dev.staticsanches.kge.engine.state.input.KeyboardModifiers
import web.uievents.KeyboardEvent

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

    /**
     * Handle keyboard events.
     */
    suspend fun onKeyboardEvent(
        key: KeyboardKey,
        newAction: KeyboardKeyAction,
        newModifiers: KeyboardModifiers,
        event: KeyboardEvent,
    ) = Unit
}
