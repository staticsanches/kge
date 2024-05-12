package dev.staticsanches.kge.engine.window.state

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.input.KeyboardKeyState
import dev.staticsanches.kge.input.KeyboardModifiers

class InputState {
    val keyboardKeyState: KeyboardKeyState = KeyboardKeyState()
    var keyboardModifiers: KeyboardModifiers = KeyboardModifiers(0)
        @KGESensitiveAPI set
}
