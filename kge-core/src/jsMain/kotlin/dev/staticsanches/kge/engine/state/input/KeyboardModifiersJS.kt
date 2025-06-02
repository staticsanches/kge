@file:Suppress("ktlint:standard:filename", "unused")

package dev.staticsanches.kge.engine.state.input

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import web.keyboard.ModifierKeyCode.Companion.CapsLock
import web.keyboard.ModifierKeyCode.Companion.NumLock
import web.uievents.KeyboardEvent

actual value class KeyboardModifiers
    @KGESensitiveAPI
    constructor(
        private val mods: Int,
    ) {
        constructor(
            altKey: Boolean,
            ctrlKey: Boolean,
            metaKey: Boolean,
            shiftKey: Boolean,
            capsLockKey: Boolean,
            numLockKey: Boolean,
        ) : this(
            mods(
                altKey = altKey,
                ctrlKey = ctrlKey,
                metaKey = metaKey,
                shiftKey = shiftKey,
                capsLockKey = capsLockKey,
                numLockKey = numLockKey,
            ),
        )

        constructor(event: KeyboardEvent) : this(
            mods(
                altKey = event.altKey,
                ctrlKey = event.ctrlKey,
                metaKey = event.metaKey,
                shiftKey = event.shiftKey,
                capsLockKey = event.getModifierState(CapsLock),
                numLockKey = event.getModifierState(NumLock),
            ),
        )

        val altKey: Boolean
            get() = mods and ALT_KEY_MASK > 0
        val ctrlKey: Boolean
            get() = mods and CTRL_KEY_MASK > 0
        val metaKey: Boolean
            get() = mods and META_KEY_MASK > 0
        val shiftKey: Boolean
            get() = mods and SHIFT_KEY_MASK > 0
        val capsLockKey: Boolean
            get() = mods and CAPS_LOCK_KEY_MASK > 0
        val numLockKey: Boolean
            get() = mods and NUM_LOCK_KEY_MASK > 0

        companion object {
            const val ALT_KEY_MASK = 0b000001
            const val CTRL_KEY_MASK = 0b000010
            const val META_KEY_MASK = 0b000100
            const val SHIFT_KEY_MASK = 0b001000
            const val CAPS_LOCK_KEY_MASK = 0b010000
            const val NUM_LOCK_KEY_MASK = 0b100000

            private fun mods(
                altKey: Boolean,
                ctrlKey: Boolean,
                metaKey: Boolean,
                shiftKey: Boolean,
                capsLockKey: Boolean,
                numLockKey: Boolean,
            ): Int {
                var mods = 0
                if (altKey) mods = mods or ALT_KEY_MASK
                if (ctrlKey) mods = mods or CTRL_KEY_MASK
                if (metaKey) mods = mods or META_KEY_MASK
                if (shiftKey) mods = mods or SHIFT_KEY_MASK
                if (capsLockKey) mods = mods or CAPS_LOCK_KEY_MASK
                if (numLockKey) mods = mods or NUM_LOCK_KEY_MASK
                return mods
            }
        }
    }
