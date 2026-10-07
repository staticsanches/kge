# Engine console (text entry + built-in command console) micro-plan

Base: `ade5dd1`. Touch-point:
`docs/plans/2026-10-07-console-touchpoint.md` (decisions D1–D10). Gate:
`tools/gradle build`, once, before the two review axes.

## What this round delivers

A text entry that turns platform character events into an editable line, and a
console over it: a transcript fed by an explicit write surface, a fixed 8x16
grid derived from the viewport, the five olc edit operations, command history,
two Enter paths, and the engine steps that run the entry before `onUserUpdate`
and the console after it, with the optional frame-time suspension. D2's
character events reach both backends; D3's stdout capture does not exist.

## Success criteria

1. Every verified fact and every decision of the touch-point has a named test in
   the pin inventory below — parity pins for facts 1–18, and an explicit pin for
   each recorded divergence (D2, D5, D8, D9) plus the surface assertion that
   covers D3 and D10: D3 has nothing to execute, so it is pinned by the absence
   itself, and D10 by the cursor-unit pins 7–10.
2. `Engine`'s public surface grows by exactly: `textEntry`, `console`,
   `onTextEntryComplete`, `onConsoleCommand`. Nothing else is widened.
3. No existing spec's case count drops and no golden reference moves.
4. Gate green, with the counts read per module and target.

## Shape (fixed by the touch-point)

**Public surface.**

```kotlin
// dev.staticsanches.kge.engine.input
sealed interface TextInputEvent {
    /** A character the platform reported, as a Unicode code point. */
    data class Character(val codePoint: Int) : TextInputEvent

    enum class Edit : TextInputEvent { LEFT, RIGHT, BACKSPACE, DELETE, UP, DOWN, ENTER }
}

// dev.staticsanches.kge.engine.console
class TextEntry internal constructor() {
    val text: String
    val cursor: Int
    val isEnabled: Boolean
    fun enable(text: String = "")   // cursor at the end (fact 4)
    fun disable()                   // keeps the text (fact 3)
}

class Console internal constructor() {
    val isShowing: Boolean
    val isTimeSuspended: Boolean    // showing && the show's suspend flag (fact 16)
    fun show(exitKey: KeyboardKey, suspendTime: Boolean = true)
    fun hide()
    fun clear()
    fun write(text: String)
}
```

**Cursor units (D10).** `text` is a `String` and `cursor` is a code-unit index
into it, but every movement and every erase operates on whole code points:
LEFT and RIGHT move one code point, BACKSPACE erases the code point before the
cursor, DELETE erases the code point at it, and an insertion inserts one whole
code point. A cursor therefore never lands inside a surrogate pair, and a
movement or an erase can move the index by two units. This is a recorded
divergence for D10's sake — olc indexes bytes and would split a multi-unit
character.

`Engine` gains `val textEntry: TextEntry`, `val console: Console`,
`open suspend fun onTextEntryComplete(text: String) {}` and
`open suspend fun onConsoleCommand(command: String): Boolean = false`
(fact 15's inert default). Reads are free; every mutator requires the engine
thread, as `setDrawTarget` does. `hide()` is a deliberate addition over olc's
key-only close: the entry already exposes `disable()`, so the symmetric door
belongs to the surface. `isTimeSuspended` reads olc's `bConsoleSuspendTime`,
which is only ever true while showing (fact 16).

**Event routing.** The entry consumes characters and the four edit events LEFT,
RIGHT, BACKSPACE and DELETE. UP, DOWN and ENTER belong to the console, because
they act on its history and on its showing state (fact 13's history, fact 14's
two Enter paths): while the console is showing, UP and DOWN drive the history
into the entry, and ENTER takes the console's path; while it is hidden, ENTER
takes the entry's completion path and UP and DOWN do nothing. With no console
step reached at all — the entry enabled but the engine not showing a console —
the same hidden rule applies.

**Raw input.** `RawInput` gains an ordered event list with
`@KGESensitiveAPI fun typedCharacter(codePoint: Int)` and
`@KGESensitiveAPI fun pressedEdit(edit: TextInputEvent.Edit)`, plus an
`internal fun drainTextInput(): List<TextInputEvent>`. The engine drains it once
per frame *whether or not* the entry is enabled — olc clears its press cache
every frame, and D9's bound depends on it — and feeds it to the entry only when
the entry is enabled. A character and an edit therefore never reorder.

**Backends.** The JVM `GLFWCharCallback` calls `typedCharacter`; its existing
key callback additionally calls `pressedEdit` on `GLFW_PRESS` **and**
`GLFW_REPEAT` (holding an edit key repeats, as olc's cache does on the platforms
that auto-repeat) and never on release. The web `onKey` calls `typedCharacter`
for a keydown whose `key` is exactly one code point — `event.key` is `" "` for
space and a single character for a letter, while `"Enter"`, `"Shift"` and an
empty string are not characters — once, not on `event.repeat`, and calls
`pressedEdit` for the mapped codes on press and repeat. A keydown that is
enqueued as a character must not also be enqueued as an edit; the two sets are
disjoint by construction, since the edit codes are the non-character keys.

**Grid and scale (fact 6, in KGE terms).** With `fit` the viewport fit and
`screen` the game screen, the console's scale is
`Float2D(screen.x / fit.size.x, 2f * screen.y / fit.size.y)` and its grid is
`Int2D(fit.size.x / 8 - 2, fit.size.y / 16 - 4)` cells, both in framebuffer
pixels — olc's "based in real screen dimensions". Positions follow fact 18:
line `n` at `(1, 1 + n) * scale * 8`, the prompt at `(1, grid.y) * scale * 8`,
the cursor rect at `((cursor + 2) * scale.x * 8, grid.y * scale.y * 8)` sized
`(8, 8) * scale`.

**Drawing.** Glyph lines and the prompt go through `KGEFont.drawTextDecal` on the
core family's default face at `8.fontPx` (D6), which routes through
`DrawPartialDecalService` and so is observable. The shadow and the cursor are
polygon decals over one console-owned `1x1` white sprite created through
`SpriteService` and registered with the run's scope (D7); the shader multiplies
the sampled texel by the interpolated per-vertex colour
(`BuiltInQuad.kt:122`), so the four corner tints of fact 18 are a true gradient.
All console decals are queued on layer 0 without touching the selected draw
target (D5); layer 0 is composited last (fact 22). The two observation points are
therefore distinct and both public seams: **glyph quads** through
`DrawPartialDecalService`, the console's **own fills** through
`DrawPolygonDecalService`.

**Shadow tints (fact 18, exact).** olc writes the shadow as
`PixelF(0, 0, 0.5, 0.5)` at the top-left and `PixelF(0, 0, 0.25, 0.5)` at the
other three corners, and `PixelF` is `Pixel(uint8_t(r * 255), …)`, which
truncates toward zero: `0.5 * 255 = 127.5 → 127` and `0.25 * 255 = 63.75 → 63`.
The four vertex tints are therefore exactly `Pixel.rgba(0x00007F7Fu)` for the
top-left and `Pixel.rgba(0x00003F7Fu)` for the bottom-left, bottom-right and
top-right, with straight alpha. These are not `Colors` members and D8 does not
reach them: they are olc's own bytes, reproduced. The cursor and the prompt are
the palette members of D8.

**State and seams.** The transcript, the pending output backlog (bounded at 4096
code units, drop-oldest, D9), the grid, the cursor and the history are private to
`Console`. One `internal` accessor exposes the transcript and the grid for
`commonTest`, the mechanism U6's D4 established; the drawing is pinned black-box
through the overridable decal services instead.

## Pin inventory (the contract)

**`commonTest/.../engine/input/TextInputQueueTest.kt` (new)**

1. characters and edits drain in the order they were reported.
2. a drain empties the queue; a second drain is empty.
3. the engine drains with the entry disabled, so a long run cannot grow the
   queue (D9).

**`commonTest/.../engine/console/TextEntryTest.kt` (new)**

4. `enable(text)` stores the text and puts the cursor at its end; `enable()` with
   no argument starts empty at 0 (facts 2, 4).
5. `disable()` clears only the flag; the text and cursor survive (fact 3).
6. a character inserts at the cursor and advances it (fact 12).
7. LEFT and RIGHT move the cursor one **code point**, bounded at 0 and at the
   length (fact 12; the code-point rule is D10).
8. BACKSPACE erases the whole code point before the cursor, and does nothing at
   0 (fact 12, D10).
9. DELETE erases the whole code point at the cursor, and does nothing at the
   length (fact 12, D10).
10. a character beyond the BMP is inserted whole and the cursor moves by its
    code-unit count; a LEFT after it moves back over the whole character (D10).

**`commonTest/.../engine/console/ConsoleBufferTest.kt` (new)**

11. `write` text becomes transcript lines on the next console frame (fact 8).
12. only ASCII 32..126 enters the transcript; a tab and a control character do
    not (fact 9).
13. `\n` starts a new line (fact 9).
14. a positive-width line holds exactly `grid.x` characters and the next
    character starts the following line — the wrap test runs after the cursor
    advances (fact 9, as extracted).
15. passing the last line scrolls: every line shifts up and the last is cleared
    (fact 9).
16. a grid-height change clears the transcript and resets the cursor (fact 7).
17. `clear()` empties the transcript (fact 10).
18. `show()` on an already showing console changes nothing (fact 2).
19. the backlog is bounded while the console is hidden and keeps the newest
    output (D9).

**`commonTest/.../engine/console/ConsoleDrawTest.kt` (new)**

20. the shadow is one quad over the whole screen with the four corner tints
    `0x00007F7F` (top-left) and `0x00003F7F` (the other three), in that vertex
    order, over the `1x1` white sprite (fact 18, D7, D8).
21. the cursor quad sits at the entry's cursor cell, in `Colors.DARK_CYAN`, over
    a `1x1` sprite (facts 18, 19; D7).
22. the prompt line is drawn in `Colors.YELLOW` at the last grid line (fact 18).
23. every transcript line is drawn in `Colors.WHITE`, one quad per glyph (facts 18).
24. the console's decal instances are queued on layer 0 and drawn after the other
    layers' (facts 22, D5).

**`commonTest/.../engine/EngineConsoleTest.kt` (new)**

25. `show()` sets `isShowing`, and the exit key's press edge clears it (facts 2, 5).
26. the exit key's press that opened the console does not close it on the next
    frame (fact 2's forced release).
27. the console's own key handling does not consume the game's `InputState`: the
    game still observes the key's edges (fact 5's "the game keeps reading keys").
28. with `suspendTime` the elapsed handed to `onUserUpdate` is zero while
    showing, and untouched while hidden (fact 16).
29. Enter while showing calls `onConsoleCommand` with the entry text, clears the
    entry, and records history only when the hook returns true; with the hook
    un-overridden (fact 15's default of false) the command still runs, the entry
    still clears, and nothing enters the history (facts 13, 14).
30. Enter while the entry is enabled and the console is hidden calls
    `onTextEntryComplete` and disables the entry (fact 14).
31. the console never changes the selected draw target (D5).
32. showing and hiding across frames leaves the game's draw target and layer
    selection unchanged (D5).

**`jvmTest/.../engine/input/TextInputJvmTest.kt` (new)**

33. a GLFW character callback enqueues its code point.
34. a GLFW press and a GLFW repeat each enqueue the edit; a release does not.

**`webTest/.../engine/WebDriverInputTest.kt` (extend)**

35. a DOM keydown with a printable key enqueues that character; a keydown with a
    non-printable key does not; a repeat keydown enqueues the edit again.

**`commonTest/.../engine/console/ConsoleHistoryTest.kt` (new)**

36. after a recorded command, UP loads it and puts the cursor at its end; at the
    oldest entry UP is a no-op (fact 13).
37. DOWN moves toward the newest entry, and one step past the newest it clears
    the entry and puts the cursor at 0 (fact 13).

### The guards

Pins 3, 12, 19, 26, 27, 31 and 32 are negative guards: some pass on the base
commit and must keep passing, and they are what fails if the drain is skipped,
the transcript widens to all code points, the backlog grows unbounded, the exit
key is re-read, the game's input is consumed, or the draw target is mutated. The
reds at each step are the positive pins of that step.

## Steps

**Step 1 — the input event queue and both backends (pins 1–3, 33–35).** Add the
vocabulary and the queue to `RawInput`, drain it, and wire the two backends.
Green.

**Step 2 — `TextEntry` (pins 4–10).** The object, its editing rules and the
history-free part of the event application. Green.

**Step 3 — the engine's entry step (pins 25–27, 30, 31).** The two accessors, the
hooks, the drain-and-feed step before `onUserUpdate`, the Enter path when the
console is hidden, the exit key's forced release, and the draw-target guard. The
console exists as a showing flag with an empty transcript at this step, so pin 25
and 27 are reachable; pins 28–29, 32 wait for step 4. Green.

**Step 4 — the console buffer, the hook and the suspension (pins 11–19, 28, 29,
32, 36–37).** The transcript, the grid, the reset and scroll rules, `clear`, the
bounded backlog, the history and its navigation, the Enter path while showing,
and the frame-time suspension. Green.

**Step 5 — the console drawing (pins 20–24).** The white fill sprite in the scope,
the shadow and cursor polygon decals, the line and prompt draws, and the layer-0
queueing. Green.

## Expected counts

New pins: 3 (queue) + 7 (entry) + 9 (buffer) + 5 (draw) + 8 (engine) + 2 (jvm) +
1 (web) + 2 (history) = 37. `kge-core` currently reports 746 jvm / 781 js / 781
wasmJs. No target runs all 37: the JVM target gains the 36 that are not
`webTest`-only (746 → **782**, with the 3 skips unchanged), and js and wasmJs gain
the 35 that are not `jvmTest`-only (781 → **816** each). An earlier draft of this
section said the JVM gained all 37; pin 35 lives in `webTest`, which compiles into
js and wasmJs only. Every other module is unchanged. The gate's own result XMLs
are what confirm this, per chunk `35`; a short count is investigated, not
accepted.

## Out of this round

`ConsoleCaptureStdOut` (D3, recorded divergence), text selection, word-wise
editing, IME composition preview, a configurable grid or font, console commands
of any kind, and a `FillRectDecal`-style public fill API (D7 defers it to a decal
primitives concept).
