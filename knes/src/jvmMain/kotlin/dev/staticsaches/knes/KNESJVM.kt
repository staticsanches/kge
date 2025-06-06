@file:Suppress("ktlint:standard:filename")

package dev.staticsaches.knes

import dev.staticsaches.knes.emulator.NES
import dev.staticsaches.knes.utils.UInt16
import dev.staticsanches.kge.engine.KotlinGameEngine
import dev.staticsanches.kge.engine.state.input.KeyboardKey
import dev.staticsanches.kge.engine.state.input.KeyboardKeyAction
import dev.staticsanches.kge.engine.state.input.KeyboardModifiers

actual class KNES : KotlinGameEngine("NES emulator") {
    internal actual val nes: NES
        get() = internalNES ?: throw IllegalStateException("Engine is not running")
    private var internalNES: NES? = null
    internal actual val mapAsm: MutableMap<UInt16, String> = mutableMapOf()
    internal actual val spaceKey: KeyboardKey
        get() = KeyboardKey.KEY_SPACE
    internal actual val rKey: KeyboardKey
        get() = KeyboardKey.KEY_R
    internal actual val iKey: KeyboardKey
        get() = KeyboardKey.KEY_I
    internal actual val nKey: KeyboardKey
        get() = KeyboardKey.KEY_N
    internal actual val xKey: KeyboardKey
        get() = KeyboardKey.KEY_X

    fun run() =
        run {
            resizable = true
            keepAspectRatio = true
            screenWidth = 680
            screenHeight = 480
            pixelWidth = 2
            pixelHeight = 2
        }

    override fun onUserCreate() {
        internalNES = NES()
        internalOnUserCreate()
    }

    override fun onKeyEvent(
        key: KeyboardKey,
        newAction: KeyboardKeyAction,
        scancode: Int,
        newModifiers: KeyboardModifiers,
    ) = internalOnKeyEvent(key, newAction)

    override fun onUserUpdate(): Boolean = internalOnUserUpdate()

    override fun onUserDestroy(): Boolean {
        val nes = internalNES
        internalNES = null
        nes?.close()
        return true
    }
}
