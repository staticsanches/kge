package dev.staticsanches.kge.engine

import dev.staticsanches.kge.engine.input.InputState

/** A carrier of the current input snapshot. */
interface HasInput {
    /** The input snapshot of the current frame. */
    val input: InputState
}
