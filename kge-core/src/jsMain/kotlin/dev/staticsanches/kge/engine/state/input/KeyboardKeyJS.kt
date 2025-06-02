@file:Suppress("ktlint:standard:filename", "unused")

package dev.staticsanches.kge.engine.state.input

import web.uievents.KeyboardEvent

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

    // Fallback
    Unidentified,

    ;

    companion object {
        operator fun get(code: String): KeyboardKey =
            try {
                valueOf(code)
            } catch (e: Exception) {
                Unidentified
            }

        operator fun get(event: KeyboardEvent): KeyboardKey = get(event.code.unsafeCast<String>())
    }
}
