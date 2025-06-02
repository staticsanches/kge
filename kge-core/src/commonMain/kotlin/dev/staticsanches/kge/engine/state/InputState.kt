@file:Suppress("unused")

package dev.staticsanches.kge.engine.state

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.state.input.KeyboardKeyState
import dev.staticsanches.kge.engine.state.input.KeyboardModifiers

class InputState {
    val keyboardKeyState: KeyboardKeyState = KeyboardKeyState()
    var keyboardModifiers: KeyboardModifiers = KeyboardModifiers(0)
        @KGESensitiveAPI set
}
