package dev.staticsanches.kge.input

import dev.staticsanches.kge.annotations.KGESensitiveAPI

/**
 * Stores the previous action captured by the keyboard callback.
 */
@JvmInline
value class KeyboardKeyState private constructor(private val state: Array<PreviousKeyboardKeyAction>) {
    constructor() : this(Array(KeyboardKey.entries.size) { UnknownAction })

    operator fun get(key: KeyboardKey): PreviousKeyboardKeyAction = state[key.ordinal]

    @KGESensitiveAPI
    operator fun set(
        key: KeyboardKey,
        action: PreviousKeyboardKeyAction,
    ) {
        state[key.ordinal] = action
    }
}
