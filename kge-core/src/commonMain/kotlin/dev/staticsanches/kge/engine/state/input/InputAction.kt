@file:Suppress("unused")

package dev.staticsanches.kge.engine.state.input

sealed interface InputAction

sealed interface PreviousKeyboardKeyAction

sealed interface PreviousMouseButtonAction

sealed interface KeyboardKeyAction :
    InputAction,
    PreviousKeyboardKeyAction

sealed interface MouseButtonAction :
    InputAction,
    PreviousMouseButtonAction

data object PressAction : KeyboardKeyAction, MouseButtonAction

data object ReleaseAction : KeyboardKeyAction, MouseButtonAction

data object RepeatAction : KeyboardKeyAction

data object UnknownAction : PreviousKeyboardKeyAction, PreviousMouseButtonAction
