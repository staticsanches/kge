@file:Suppress("ktlint:standard:filename")

package dev.staticsanches.kge.engine.input

actual enum class KeyboardKey {
    // [3. Keyboard Event code Value Tables](https://www.w3.org/TR/uievents-code/#code-value-tables)
    // [3.1. Alphanumeric Section](https://www.w3.org/TR/uievents-code/#key-alphanumeric-section)
    // [3.1.1. Writing System Keys](https://www.w3.org/TR/uievents-code/#key-alphanumeric-writing-system)
    Backquote,
    Backslash,
    BracketLeft,
    BracketRight,
    Comma,
    Digit0,
    Digit1,
    Digit2,
    Digit3,
    Digit4,
    Digit5,
    Digit6,
    Digit7,
    Digit8,
    Digit9,
    Equal,
    IntlBackslash,
    IntlRo,
    IntlYen,
    KeyA,
    KeyB,
    KeyC,
    KeyD,
    KeyE,
    KeyF,
    KeyG,
    KeyH,
    KeyI,
    KeyJ,
    KeyK,
    KeyL,
    KeyM,
    KeyN,
    KeyO,
    KeyP,
    KeyQ,
    KeyR,
    KeyS,
    KeyT,
    KeyU,
    KeyV,
    KeyW,
    KeyX,
    KeyY,
    KeyZ,
    Minus,
    Period,
    Quote,
    Semicolon,
    Slash,

    // [3.1.2. Functional Keys](https://www.w3.org/TR/uievents-code/#key-alphanumeric-functional)
    AltLeft,
    AltRight,
    Backspace,
    CapsLock,
    ContextMenu,
    ControlLeft,
    ControlRight,
    Enter,
    MetaLeft,
    MetaRight,
    ShiftLeft,
    ShiftRight,
    Space,
    Tab,

    // Japanese and Korean
    Convert,
    KanaMode,
    Lang1,
    Lang2,
    Lang3,
    Lang4,
    Lang5,
    NonConvert,

    // [3.2. Control Pad Section](https://www.w3.org/TR/uievents-code/#key-controlpad-section)
    Delete,
    End,
    Help,
    Home,
    Insert,
    PageDown,
    PageUp,

    // [3.3. Arrow Pad Section](https://www.w3.org/TR/uievents-code/#key-arrowpad-section)
    ArrowDown,
    ArrowLeft,
    ArrowRight,
    ArrowUp,

    // [3.4. Numpad Section](https://www.w3.org/TR/uievents-code/#key-numpad-section)
    NumLock,
    Numpad0,
    Numpad1,
    Numpad2,
    Numpad3,
    Numpad4,
    Numpad5,
    Numpad6,
    Numpad7,
    Numpad8,
    Numpad9,
    NumpadAdd,
    NumpadBackspace,
    NumpadClear,
    NumpadClearEntry,
    NumpadComma,
    NumpadDecimal,
    NumpadDivide,
    NumpadEnter,
    NumpadEqual,
    NumpadHash,
    NumpadMemoryAdd,
    NumpadMemoryClear,
    NumpadMemoryRecall,
    NumpadMemoryStore,
    NumpadMemorySubtract,
    NumpadMultiply,
    NumpadParenLeft,
    NumpadParenRight,
    NumpadStar,
    NumpadSubtract,

    // [3.5. Function Section](https://www.w3.org/TR/uievents-code/#key-function-section)
    Escape,
    F1,
    F2,
    F3,
    F4,
    F5,
    F6,
    F7,
    F8,
    F9,
    F10,
    F11,
    F12,
    Fn,
    FnLock,
    PrintScreen,
    ScrollLock,
    Pause,

    // [3.6. Media Keys](https://www.w3.org/TR/uievents-code/#key-media)
    BrowserBack,
    BrowserFavorites,
    BrowserForward,
    BrowserHome,
    BrowserRefresh,
    BrowserSearch,
    BrowserStop,
    Eject,
    LaunchApp1,
    LaunchApp2,
    LaunchMail,
    MediaPlayPause,
    MediaSelect,
    MediaStop,
    MediaTrackNext,
    MediaTrackPrevious,
    Power,
    Sleep,
    AudioVolumeDown,
    AudioVolumeMute,
    AudioVolumeUp,
    WakeUp,

    // [3.7. Legacy, Non-Standard and Special Keys](https://www.w3.org/TR/uievents-code/#key-legacy)
    // Legacy modifier keys
    Hyper,
    Super,
    Turbo,

    // Legacy process control keys.
    Abort,
    Resume,
    Suspend,

    // Legacy editing keys
    Again,
    Copy,
    Cut,
    Find,
    Open,
    Paste,
    Props,
    Select,
    Undo,

    // International keyboards
    Hiragana,
    Katakana,

    // Fallback: must stay the last entry — the vocabulary maps away from it.
    Unidentified,
    ;

    actual companion object {
        /**
         * The native key for a W3C `code`; the main and numpad Enter both
         * resolve to [Enter] (olc collapse), and an unknown code to
         * [Unidentified].
         */
        operator fun get(code: String): KeyboardKey =
            when (code) {
                "NumpadEnter" -> Enter
                else ->
                    try {
                        valueOf(code)
                    } catch (e: IllegalArgumentException) {
                        Unidentified
                    }
            }
    }
}

/**
 * Left-hand modifiers back [KeyVocabulary.Shift] and [KeyVocabulary.Ctrl]; the
 * right-hand variants are platform-only and reach common code through the
 * modifier snapshot.
 */
internal actual fun keyboardKey(vocabulary: KeyVocabulary): KeyboardKey =
    when (vocabulary) {
        KeyVocabulary.A -> KeyboardKey.KeyA
        KeyVocabulary.B -> KeyboardKey.KeyB
        KeyVocabulary.C -> KeyboardKey.KeyC
        KeyVocabulary.D -> KeyboardKey.KeyD
        KeyVocabulary.E -> KeyboardKey.KeyE
        KeyVocabulary.F -> KeyboardKey.KeyF
        KeyVocabulary.G -> KeyboardKey.KeyG
        KeyVocabulary.H -> KeyboardKey.KeyH
        KeyVocabulary.I -> KeyboardKey.KeyI
        KeyVocabulary.J -> KeyboardKey.KeyJ
        KeyVocabulary.K -> KeyboardKey.KeyK
        KeyVocabulary.L -> KeyboardKey.KeyL
        KeyVocabulary.M -> KeyboardKey.KeyM
        KeyVocabulary.N -> KeyboardKey.KeyN
        KeyVocabulary.O -> KeyboardKey.KeyO
        KeyVocabulary.P -> KeyboardKey.KeyP
        KeyVocabulary.Q -> KeyboardKey.KeyQ
        KeyVocabulary.R -> KeyboardKey.KeyR
        KeyVocabulary.S -> KeyboardKey.KeyS
        KeyVocabulary.T -> KeyboardKey.KeyT
        KeyVocabulary.U -> KeyboardKey.KeyU
        KeyVocabulary.V -> KeyboardKey.KeyV
        KeyVocabulary.W -> KeyboardKey.KeyW
        KeyVocabulary.X -> KeyboardKey.KeyX
        KeyVocabulary.Y -> KeyboardKey.KeyY
        KeyVocabulary.Z -> KeyboardKey.KeyZ
        KeyVocabulary.K0 -> KeyboardKey.Digit0
        KeyVocabulary.K1 -> KeyboardKey.Digit1
        KeyVocabulary.K2 -> KeyboardKey.Digit2
        KeyVocabulary.K3 -> KeyboardKey.Digit3
        KeyVocabulary.K4 -> KeyboardKey.Digit4
        KeyVocabulary.K5 -> KeyboardKey.Digit5
        KeyVocabulary.K6 -> KeyboardKey.Digit6
        KeyVocabulary.K7 -> KeyboardKey.Digit7
        KeyVocabulary.K8 -> KeyboardKey.Digit8
        KeyVocabulary.K9 -> KeyboardKey.Digit9
        KeyVocabulary.F1 -> KeyboardKey.F1
        KeyVocabulary.F2 -> KeyboardKey.F2
        KeyVocabulary.F3 -> KeyboardKey.F3
        KeyVocabulary.F4 -> KeyboardKey.F4
        KeyVocabulary.F5 -> KeyboardKey.F5
        KeyVocabulary.F6 -> KeyboardKey.F6
        KeyVocabulary.F7 -> KeyboardKey.F7
        KeyVocabulary.F8 -> KeyboardKey.F8
        KeyVocabulary.F9 -> KeyboardKey.F9
        KeyVocabulary.F10 -> KeyboardKey.F10
        KeyVocabulary.F11 -> KeyboardKey.F11
        KeyVocabulary.F12 -> KeyboardKey.F12
        KeyVocabulary.Up -> KeyboardKey.ArrowUp
        KeyVocabulary.Down -> KeyboardKey.ArrowDown
        KeyVocabulary.Left -> KeyboardKey.ArrowLeft
        KeyVocabulary.Right -> KeyboardKey.ArrowRight
        KeyVocabulary.Space -> KeyboardKey.Space
        KeyVocabulary.Tab -> KeyboardKey.Tab
        KeyVocabulary.Shift -> KeyboardKey.ShiftLeft
        KeyVocabulary.Ctrl -> KeyboardKey.ControlLeft
        KeyVocabulary.Insert -> KeyboardKey.Insert
        KeyVocabulary.Delete -> KeyboardKey.Delete
        KeyVocabulary.Home -> KeyboardKey.Home
        KeyVocabulary.End -> KeyboardKey.End
        KeyVocabulary.PageUp -> KeyboardKey.PageUp
        KeyVocabulary.PageDown -> KeyboardKey.PageDown
        KeyVocabulary.Backspace -> KeyboardKey.Backspace
        KeyVocabulary.Escape -> KeyboardKey.Escape
        KeyVocabulary.Enter -> KeyboardKey.Enter
        KeyVocabulary.Pause -> KeyboardKey.Pause
        KeyVocabulary.ScrollLock -> KeyboardKey.ScrollLock
        KeyVocabulary.Numpad0 -> KeyboardKey.Numpad0
        KeyVocabulary.Numpad1 -> KeyboardKey.Numpad1
        KeyVocabulary.Numpad2 -> KeyboardKey.Numpad2
        KeyVocabulary.Numpad3 -> KeyboardKey.Numpad3
        KeyVocabulary.Numpad4 -> KeyboardKey.Numpad4
        KeyVocabulary.Numpad5 -> KeyboardKey.Numpad5
        KeyVocabulary.Numpad6 -> KeyboardKey.Numpad6
        KeyVocabulary.Numpad7 -> KeyboardKey.Numpad7
        KeyVocabulary.Numpad8 -> KeyboardKey.Numpad8
        KeyVocabulary.Numpad9 -> KeyboardKey.Numpad9
        KeyVocabulary.NumpadMultiply -> KeyboardKey.NumpadMultiply
        KeyVocabulary.NumpadDivide -> KeyboardKey.NumpadDivide
        KeyVocabulary.NumpadAdd -> KeyboardKey.NumpadAdd
        KeyVocabulary.NumpadSubtract -> KeyboardKey.NumpadSubtract
        KeyVocabulary.NumpadDecimal -> KeyboardKey.NumpadDecimal
        KeyVocabulary.Period -> KeyboardKey.Period
        KeyVocabulary.Equals -> KeyboardKey.Equal
        KeyVocabulary.Comma -> KeyboardKey.Comma
        KeyVocabulary.Minus -> KeyboardKey.Minus
        KeyVocabulary.Oem1 -> KeyboardKey.Semicolon
        KeyVocabulary.Oem2 -> KeyboardKey.Slash
        KeyVocabulary.Oem3 -> KeyboardKey.Backquote
        KeyVocabulary.Oem4 -> KeyboardKey.BracketLeft
        KeyVocabulary.Oem5 -> KeyboardKey.Backslash
        KeyVocabulary.Oem6 -> KeyboardKey.BracketRight
        KeyVocabulary.Oem7 -> KeyboardKey.Quote
        KeyVocabulary.CapsLock -> KeyboardKey.CapsLock
    }
