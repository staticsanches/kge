@file:Suppress("ktlint:standard:filename", "unused")

package dev.staticsanches.kge.engine.state.input

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import org.lwjgl.glfw.GLFW

@JvmInline
actual value class KeyboardModifiers
    @KGESensitiveAPI
    constructor(
        private val mods: Int,
    ) {
        val shiftHeldDown: Boolean
            get() = mods and GLFW.GLFW_MOD_SHIFT > 0

        val controlHeldDown: Boolean
            get() = mods and GLFW.GLFW_MOD_CONTROL > 0

        val altHeldDown: Boolean
            get() = mods and GLFW.GLFW_MOD_ALT > 0

        val superHeldDown: Boolean
            get() = mods and GLFW.GLFW_MOD_SUPER > 0

        val capsLockEnabled: Boolean
            get() = mods and GLFW.GLFW_MOD_CAPS_LOCK > 0

        val numLockEnabled: Boolean
            get() = mods and GLFW.GLFW_MOD_NUM_LOCK > 0
    }
