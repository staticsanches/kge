package dev.staticsanches.kge.engine.input

/**
 * A keyboard key, identified by the platform's native key set; a platform-only
 * key stays reachable from platform code.
 *
 * The vocabulary both platforms share is exposed through companion extensions
 * ([escape], [a], [enter], [oem1], …). The final entry is the fallback a
 * platform reports for a key it cannot map.
 */
expect enum class KeyboardKey {
    ;

    companion object
}

/**
 * The intersection of the platform key sets: every entry maps to a real native
 * [KeyboardKey] on each platform through [keyboardKey].
 */
internal enum class KeyVocabulary {
    A,
    B,
    C,
    D,
    E,
    F,
    G,
    H,
    I,
    J,
    K,
    L,
    M,
    N,
    O,
    P,
    Q,
    R,
    S,
    T,
    U,
    V,
    W,
    X,
    Y,
    Z,
    K0,
    K1,
    K2,
    K3,
    K4,
    K5,
    K6,
    K7,
    K8,
    K9,
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
    Up,
    Down,
    Left,
    Right,
    Space,
    Tab,
    Shift,
    Ctrl,
    Insert,
    Delete,
    Home,
    End,
    PageUp,
    PageDown,
    Backspace,
    Escape,
    Enter,
    Pause,
    ScrollLock,
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
    NumpadMultiply,
    NumpadDivide,
    NumpadAdd,
    NumpadSubtract,
    NumpadDecimal,
    Period,
    Equals,
    Comma,
    Minus,
    Oem1,
    Oem2,
    Oem3,
    Oem4,
    Oem5,
    Oem6,
    Oem7,
    CapsLock,
}

/**
 * [vocabulary] mapped to this platform's native [KeyboardKey]; exhaustive, so a
 * vocabulary addition fails the platform builds until it is mapped.
 */
internal expect fun keyboardKey(vocabulary: KeyVocabulary): KeyboardKey

val KeyboardKey.Companion.a: KeyboardKey get() = keyboardKey(KeyVocabulary.A)
val KeyboardKey.Companion.b: KeyboardKey get() = keyboardKey(KeyVocabulary.B)
val KeyboardKey.Companion.c: KeyboardKey get() = keyboardKey(KeyVocabulary.C)
val KeyboardKey.Companion.d: KeyboardKey get() = keyboardKey(KeyVocabulary.D)
val KeyboardKey.Companion.e: KeyboardKey get() = keyboardKey(KeyVocabulary.E)
val KeyboardKey.Companion.f: KeyboardKey get() = keyboardKey(KeyVocabulary.F)
val KeyboardKey.Companion.g: KeyboardKey get() = keyboardKey(KeyVocabulary.G)
val KeyboardKey.Companion.h: KeyboardKey get() = keyboardKey(KeyVocabulary.H)
val KeyboardKey.Companion.i: KeyboardKey get() = keyboardKey(KeyVocabulary.I)
val KeyboardKey.Companion.j: KeyboardKey get() = keyboardKey(KeyVocabulary.J)
val KeyboardKey.Companion.k: KeyboardKey get() = keyboardKey(KeyVocabulary.K)
val KeyboardKey.Companion.l: KeyboardKey get() = keyboardKey(KeyVocabulary.L)
val KeyboardKey.Companion.m: KeyboardKey get() = keyboardKey(KeyVocabulary.M)
val KeyboardKey.Companion.n: KeyboardKey get() = keyboardKey(KeyVocabulary.N)
val KeyboardKey.Companion.o: KeyboardKey get() = keyboardKey(KeyVocabulary.O)
val KeyboardKey.Companion.p: KeyboardKey get() = keyboardKey(KeyVocabulary.P)
val KeyboardKey.Companion.q: KeyboardKey get() = keyboardKey(KeyVocabulary.Q)
val KeyboardKey.Companion.r: KeyboardKey get() = keyboardKey(KeyVocabulary.R)
val KeyboardKey.Companion.s: KeyboardKey get() = keyboardKey(KeyVocabulary.S)
val KeyboardKey.Companion.t: KeyboardKey get() = keyboardKey(KeyVocabulary.T)
val KeyboardKey.Companion.u: KeyboardKey get() = keyboardKey(KeyVocabulary.U)
val KeyboardKey.Companion.v: KeyboardKey get() = keyboardKey(KeyVocabulary.V)
val KeyboardKey.Companion.w: KeyboardKey get() = keyboardKey(KeyVocabulary.W)
val KeyboardKey.Companion.x: KeyboardKey get() = keyboardKey(KeyVocabulary.X)
val KeyboardKey.Companion.y: KeyboardKey get() = keyboardKey(KeyVocabulary.Y)
val KeyboardKey.Companion.z: KeyboardKey get() = keyboardKey(KeyVocabulary.Z)

/** The top-row digit `0` (the numpad zero is [numpad0]). */
val KeyboardKey.Companion.k0: KeyboardKey get() = keyboardKey(KeyVocabulary.K0)
val KeyboardKey.Companion.k1: KeyboardKey get() = keyboardKey(KeyVocabulary.K1)
val KeyboardKey.Companion.k2: KeyboardKey get() = keyboardKey(KeyVocabulary.K2)
val KeyboardKey.Companion.k3: KeyboardKey get() = keyboardKey(KeyVocabulary.K3)
val KeyboardKey.Companion.k4: KeyboardKey get() = keyboardKey(KeyVocabulary.K4)
val KeyboardKey.Companion.k5: KeyboardKey get() = keyboardKey(KeyVocabulary.K5)
val KeyboardKey.Companion.k6: KeyboardKey get() = keyboardKey(KeyVocabulary.K6)
val KeyboardKey.Companion.k7: KeyboardKey get() = keyboardKey(KeyVocabulary.K7)
val KeyboardKey.Companion.k8: KeyboardKey get() = keyboardKey(KeyVocabulary.K8)
val KeyboardKey.Companion.k9: KeyboardKey get() = keyboardKey(KeyVocabulary.K9)

val KeyboardKey.Companion.f1: KeyboardKey get() = keyboardKey(KeyVocabulary.F1)
val KeyboardKey.Companion.f2: KeyboardKey get() = keyboardKey(KeyVocabulary.F2)
val KeyboardKey.Companion.f3: KeyboardKey get() = keyboardKey(KeyVocabulary.F3)
val KeyboardKey.Companion.f4: KeyboardKey get() = keyboardKey(KeyVocabulary.F4)
val KeyboardKey.Companion.f5: KeyboardKey get() = keyboardKey(KeyVocabulary.F5)
val KeyboardKey.Companion.f6: KeyboardKey get() = keyboardKey(KeyVocabulary.F6)
val KeyboardKey.Companion.f7: KeyboardKey get() = keyboardKey(KeyVocabulary.F7)
val KeyboardKey.Companion.f8: KeyboardKey get() = keyboardKey(KeyVocabulary.F8)
val KeyboardKey.Companion.f9: KeyboardKey get() = keyboardKey(KeyVocabulary.F9)
val KeyboardKey.Companion.f10: KeyboardKey get() = keyboardKey(KeyVocabulary.F10)
val KeyboardKey.Companion.f11: KeyboardKey get() = keyboardKey(KeyVocabulary.F11)
val KeyboardKey.Companion.f12: KeyboardKey get() = keyboardKey(KeyVocabulary.F12)

val KeyboardKey.Companion.up: KeyboardKey get() = keyboardKey(KeyVocabulary.Up)
val KeyboardKey.Companion.down: KeyboardKey get() = keyboardKey(KeyVocabulary.Down)
val KeyboardKey.Companion.left: KeyboardKey get() = keyboardKey(KeyVocabulary.Left)
val KeyboardKey.Companion.right: KeyboardKey get() = keyboardKey(KeyVocabulary.Right)

val KeyboardKey.Companion.space: KeyboardKey get() = keyboardKey(KeyVocabulary.Space)
val KeyboardKey.Companion.tab: KeyboardKey get() = keyboardKey(KeyVocabulary.Tab)

/** The left-hand Shift; the right-hand variant is platform-only. */
val KeyboardKey.Companion.shift: KeyboardKey get() = keyboardKey(KeyVocabulary.Shift)

/** The left-hand Control; the right-hand variant is platform-only. */
val KeyboardKey.Companion.ctrl: KeyboardKey get() = keyboardKey(KeyVocabulary.Ctrl)
val KeyboardKey.Companion.insert: KeyboardKey get() = keyboardKey(KeyVocabulary.Insert)
val KeyboardKey.Companion.delete: KeyboardKey get() = keyboardKey(KeyVocabulary.Delete)
val KeyboardKey.Companion.home: KeyboardKey get() = keyboardKey(KeyVocabulary.Home)
val KeyboardKey.Companion.end: KeyboardKey get() = keyboardKey(KeyVocabulary.End)
val KeyboardKey.Companion.pageUp: KeyboardKey get() = keyboardKey(KeyVocabulary.PageUp)
val KeyboardKey.Companion.pageDown: KeyboardKey get() = keyboardKey(KeyVocabulary.PageDown)
val KeyboardKey.Companion.backspace: KeyboardKey get() = keyboardKey(KeyVocabulary.Backspace)
val KeyboardKey.Companion.escape: KeyboardKey get() = keyboardKey(KeyVocabulary.Escape)
val KeyboardKey.Companion.enter: KeyboardKey get() = keyboardKey(KeyVocabulary.Enter)
val KeyboardKey.Companion.pause: KeyboardKey get() = keyboardKey(KeyVocabulary.Pause)
val KeyboardKey.Companion.scrollLock: KeyboardKey get() = keyboardKey(KeyVocabulary.ScrollLock)
val KeyboardKey.Companion.capsLock: KeyboardKey get() = keyboardKey(KeyVocabulary.CapsLock)

/** The numpad digit `0` (the top-row zero is [k0]). */
val KeyboardKey.Companion.numpad0: KeyboardKey get() = keyboardKey(KeyVocabulary.Numpad0)
val KeyboardKey.Companion.numpad1: KeyboardKey get() = keyboardKey(KeyVocabulary.Numpad1)
val KeyboardKey.Companion.numpad2: KeyboardKey get() = keyboardKey(KeyVocabulary.Numpad2)
val KeyboardKey.Companion.numpad3: KeyboardKey get() = keyboardKey(KeyVocabulary.Numpad3)
val KeyboardKey.Companion.numpad4: KeyboardKey get() = keyboardKey(KeyVocabulary.Numpad4)
val KeyboardKey.Companion.numpad5: KeyboardKey get() = keyboardKey(KeyVocabulary.Numpad5)
val KeyboardKey.Companion.numpad6: KeyboardKey get() = keyboardKey(KeyVocabulary.Numpad6)
val KeyboardKey.Companion.numpad7: KeyboardKey get() = keyboardKey(KeyVocabulary.Numpad7)
val KeyboardKey.Companion.numpad8: KeyboardKey get() = keyboardKey(KeyVocabulary.Numpad8)
val KeyboardKey.Companion.numpad9: KeyboardKey get() = keyboardKey(KeyVocabulary.Numpad9)
val KeyboardKey.Companion.numpadMultiply: KeyboardKey get() = keyboardKey(KeyVocabulary.NumpadMultiply)
val KeyboardKey.Companion.numpadDivide: KeyboardKey get() = keyboardKey(KeyVocabulary.NumpadDivide)
val KeyboardKey.Companion.numpadAdd: KeyboardKey get() = keyboardKey(KeyVocabulary.NumpadAdd)
val KeyboardKey.Companion.numpadSubtract: KeyboardKey get() = keyboardKey(KeyVocabulary.NumpadSubtract)
val KeyboardKey.Companion.numpadDecimal: KeyboardKey get() = keyboardKey(KeyVocabulary.NumpadDecimal)

val KeyboardKey.Companion.period: KeyboardKey get() = keyboardKey(KeyVocabulary.Period)
val KeyboardKey.Companion.equals: KeyboardKey get() = keyboardKey(KeyVocabulary.Equals)
val KeyboardKey.Companion.comma: KeyboardKey get() = keyboardKey(KeyVocabulary.Comma)
val KeyboardKey.Companion.minus: KeyboardKey get() = keyboardKey(KeyVocabulary.Minus)

/** The `;:` key. */
val KeyboardKey.Companion.oem1: KeyboardKey get() = keyboardKey(KeyVocabulary.Oem1)

/** The `/?` key. */
val KeyboardKey.Companion.oem2: KeyboardKey get() = keyboardKey(KeyVocabulary.Oem2)

/** The `` `~ `` key. */
val KeyboardKey.Companion.oem3: KeyboardKey get() = keyboardKey(KeyVocabulary.Oem3)

/** The `[{` key. */
val KeyboardKey.Companion.oem4: KeyboardKey get() = keyboardKey(KeyVocabulary.Oem4)

/** The `\|` key. */
val KeyboardKey.Companion.oem5: KeyboardKey get() = keyboardKey(KeyVocabulary.Oem5)

/** The `]}` key. */
val KeyboardKey.Companion.oem6: KeyboardKey get() = keyboardKey(KeyVocabulary.Oem6)

/** The `'"` key. */
val KeyboardKey.Companion.oem7: KeyboardKey get() = keyboardKey(KeyVocabulary.Oem7)
