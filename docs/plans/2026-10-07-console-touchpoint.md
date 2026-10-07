# Engine console (text entry + built-in command console): touch-point

**Status: pending owner confirmation, 2026-10-07.**

The first concept after the text API unification (`#46`) and before rich text.
`main` has neither text entry nor a console, so this is a port of an olc surface
the restructure never carried over, not a rewrite of existing behavior.

Material: olcPixelGameEngine v2.30 `olcPixelGameEngine.h` (the lines cited per
fact below), the current `kge-core` sources, decisions chunks `25` (input),
`27` (addons), `29` (bitmap text), `41`–`46` (text unification).
Baseline: `ade5dd1`.

## Problem

The engine can draw text and read keys, but it cannot turn keystrokes into
characters and has no way to show the application its own output or to accept a
typed command. olc solves both with one surface: a built-in console over an
editable single-line entry. The two are one concept — the console *is* the text
entry plus a buffer and a hook — so this round takes both.

## Verified facts (this touch-point)

Parity claims below were extracted from the header verbatim and audited; the
quotient of the extraction and the audit is what is asserted here.

1. **The console API is six names plus one hook** (declarations at
   `:1533-1538`, `:1332-1333`): `ConsoleShow(exitKey, suspendTime)`,
   `IsConsoleShowing`, `ConsoleClear`, `ConsoleOut`, `ConsoleCaptureStdOut`,
   `OnConsoleCommand`. Text entry adds five (`:1541-1544`, `:1331`):
   `TextEntryEnable(enable, text = "")`, `TextEntryGetString`,
   `TextEntryGetCursor`, `IsTextEntryEnabled`, `OnTextEntryComplete`.
2. **`ConsoleShow` is idempotent and re-enables text entry** (`:4262-4274`): a
   second call while showing returns immediately; otherwise it stores the exit
   key, records the suspend flag, calls `TextEntryEnable(true)` with no text, and
   forces the exit key's held/pressed bits false and its released bit true.
   Because `TextEntryEnable`'s second argument defaults to `""` (`:1541`), the
   entry starts empty with the cursor at 0 (`:4365-4378`).
3. **Disabling text entry only clears the flag** (`:4365-4378`): the stored
   string and cursor survive.
4. **Enabling stores the given text and puts the cursor at its end**
   (`:4367-4370`).
5. **The exit key is polled in the console's own update, not in `ConsoleShow`**
   (`:4291-4297`): a press edge disables text entry, clears the suspend flag,
   hides the console and returns before any drawing.
6. **The grid is derived every frame** (`:4299-4301`): character scale
   `(1,2) / (viewSize * invScreenSize)` and size `viewSize / (8,16) - (2,4)`.
   Both derive from the real view size, not from the game's screen size.
7. **A grid-height change resets the console** (`:4303-4309`): the cursor is
   zeroed, the lines are cleared and re-sized. The width is not compared.
8. **Output is consumed only while the console is showing** (`:4333-4337`),
   inside `UpdateConsole`; while hidden the stream accumulates.
9. **Character handling** (`:4311-4331`): only `c >= 32 && c < 127` is appended
   and advances the cursor by one; a `\n` **or** a cursor that reached the width
   moves to the next line at column 0; passing the last line clamps to the last
   line, shifts every line up by one and clears the last. The wrap test runs
   **after** the increment, so a positive-width line holds exactly the grid width
   and the next character starts the next line; a line exceeds the width only
   when that width is not positive. Corrected during the round — an earlier draft
   claimed a wrap-by-one overflow for positive widths, which the code does not do.
10. **`ConsoleClear` clears only the line buffer** (`:4276-4279`) — not the
    cursor. The next `UpdateConsole` sees a height mismatch (fact 7) and resets,
    so the effect is only observable when output arrives in the same frame.
11. **Text entry consumes a per-frame press cache** (`:4395-4400`) of raw
    keycodes translated by a key→symbol table, not platform character events.
    The table is a single UK layout (`:4994-5070`, `OLC_KEYBOARD_UK`) with the
    quirks: Ctrl+letter yields the unmodified letter, Alt is never passed, and
    Shift+Enter yields `"\n "`.
12. **The edit operations** (`:4395-4492`): either arrow moves the cursor one
    position, bounded at 0 and at the string length; backspace erases before the
    cursor only when the cursor is positive; delete erases at the cursor only
    when the cursor is inside the string; a one-character symbol is inserted at
    the cursor and advances it.
13. **History** (`:4416-4445`, `:4451-4458`): up moves back one entry when not
    at the beginning and loads it; down moves forward one and clears the entry
    when it passes the end; Enter pushes the entry **only when the hook returns
    true**, and resets the iterator to the end.
14. **Enter has two paths** (`:4448-4463`): with the console showing it echoes
    `">" + entry + "\n"` to the output stream, calls the hook, optionally records
    history and clears the entry; otherwise it calls `OnTextEntryComplete` and
    disables text entry.
15. **The default hooks are inert** (`:4496-4497`): `OnConsoleCommand` returns
    false, so an un-overridden console records no history.
16. **The suspend flag zeroes the frame time** (`:4794-4795`), and
    `UpdateConsole` runs after the user update and before the frame is composed
    (`:4868-4872`).
17. **The console draws through non-virtual methods.** `DrawString`,
    `DrawStringDecal`, `FillRectDecal` and `GradientFillRectDecal`
    (`:1464`, `:1493`, `:1497`, `:1499`) carry no `virtual` — a corrected
    reading, since an earlier draft assumed `DrawString` was overridable. The
    only virtual members in this area are `OnConsoleCommand`/`OnTextEntryComplete`
    (`:1331-1333`). The console is therefore not an extension point for drawing,
    and its look is fixed.
18. **The console's own drawing** (`:4339-4350`): a gradient-filled rectangle
    over the screen as a shadow, every buffer line in white at the character
    scale, the cursor as a filled `DARK_CYAN` rectangle, and the prompt as
    `">"` plus the entry text in `YELLOW`.
19. **olc's palette differs from `Colors`.** olc `DARK_CYAN` is `(0,128,128)`
    (`:996`) where CSS Color 4 `DARK_CYAN` is `#008B8B` = `(0,139,139)`;
    `YELLOW` is `(255,255,0)` in both.

### KGE-side facts

20. **Input has no character path.** `RawInput` carries key-down bits (line 14),
    pointer (54), wheel, modifiers and focus (66), and `InputTracker.latch`
    derives the per-frame edges from those bit sets; neither carries a character
    or a press list. The JVM backend installs a `GLFWKeyCallback`, a mouse, a
    cursor, a scroll and a focus callback, and no character callback
    (`DriverServiceJvm.kt:98-113`); the web backend listens to `keydown`/`keyup`
    and derives only the key code (`DriverServiceWeb.kt:132-146`).
21. **The engine loop has no hook** between latching input and `onUserUpdate`,
    or between it and the render (`Engine.runLoop:267-270`). The callback order
    is fixed: poll → latch → `onUserUpdate` → `renderFrame`.
22. **Layer composition draws layer 0 last** (`Engine.renderFrame:303`, the
    reverse index walk), and a layer's queued decal instances are drawn with it
    (line 317), so the console composited as layer-0 decals lands on top of
    every other layer.
23. **Decals already clip.** `drawTextDecal` resolves glyphs against the window
    screen size, and the decal draw transform applies the viewport, so a window
    smaller than the grid needs no separate clipping.
24. **There is no solid-fill decal primitive.** `DrawDecalService.drawDecal`
    (line 30) and `DrawPolygonDecalService.drawPolygonDecal` (line 28) both
    require a non-null `Decal`, and neither `kge-core` nor `main` has a
    `FillRectDecal`/`GradientFillRectDecal` equivalent, nor a blank/default
    white decal. olc reaches the same effect through a null decal, which its
    renderer resolves to an internal blank texture (`DrawExplicitDecal`,
    `:3577`).
25. **`SpriteService.create` is the surface seam** (line 25) and allocates
    through the current `BufferService`, so an engine-owned 1x1 white sprite is
    created like any other resource and is covered by the leak machinery.
26. **The built-in font is reachable after startup.** `Engine` creates
    `KGECoreFontService.createResources(scope)` (`Engine.kt:198`) and keeps the
    family in `coreFontFamily` (line 140); its `defaultFace.font(scope,
    8.fontPx)` (line 200) is the bitmap face at the olc cell size.

## Decisions (recommended; pending owner confirmation)

### D1 — text entry and the console are one concept, shipped whole

Both surfaces land in this round. olc's console cannot exist without the entry
(the console is entry + buffer + hook), and shipping the console alone would
force a provisional private entry API that this concept would then have to
break. The public names are Kotlin-idiomatic, not transliterations: the entry is
a small stateful object with `text`, `cursor`, `isEnabled`, `enable(text)` and
`disable()`, and the console is one with `show(exitKey, suspendTime)`, `hide()`,
`isShowing`, `clear()`, `write(...)` and `isSuspended`.

### D2 — characters come from platform character events, not a key-symbol table

`RawInput` grows a character queue and a key-press queue; the JVM backend fills
the former from a `GLFWCharCallback` and the latter from `GLFWKeyCallback`
presses; the web backend fills the former from `KeyboardEvent.key` on printable
`keydown` and the latter from its existing key event. This is a deliberate
divergence from olc's single UK table (fact 11): the table hard-codes one layout,
while the platform event is layout-correct and needs no per-platform table. The
edit keys keep coming from `KeyboardKey`, and the two queues are drained into one
ordered event stream per frame, so a character and an edit cannot reorder.

### D3 — no `ConsoleCaptureStdOut` (`ConsoleOut` is an explicit sink)

Divergence from olc (fact 1). There is no common KMP equivalent: the JVM path is
`System.setOut`, the web path would mean intercepting `console.log`. The console
gets an explicit write surface instead. Recorded as an accepted divergence, not
a deferred item.

### D4 — the engine owns the console step; no new public hook interface

The loop gains two internal steps: the text-entry/console input step after the
input latch and before `onUserUpdate`, and the console step after
`onUserUpdate` and before the frame composition. Both mirror olc (facts 5, 16)
without widening the public surface, and the time suspension applies to the
`Duration` handed to `onUserUpdate` for that frame (fact 16).

### D5 — the console draws into layer 0 without stealing the user's draw target

Divergence from olc, which calls `SetDrawTarget(0)` and leaves it changed
(`:4868-4872`). The console queues its decals into layer 0 directly and never
mutates the engine's selected draw target, so a user who selected another layer
keeps it across console frames. Composition order still puts the console on top
(fact 22).

### D6 — the console uses the built-in bitmap face, not `textFont`

The 8x16 cell is the fixed bitmap cell (fact 20). The console takes the core
family's default face at `8.fontPx` directly, so a user swapping `textFont` to a
TTF face never changes the console's metrics or invalidity. This is a recorded
choice, not a parity claim: olc's console is simply not configurable (fact 17).

### D7 — the shadow and cursor fills are console-local, behind no new public API

The console creates one `1x1` white sprite as the fill texture once per run
(fact 25) and builds the shadow and the cursor as polygon decals with per-vertex
tint, which is exactly what a gradient fill is (fact 18, fact 24). No
`fillRectDecal`-style API is added: a decal primitives concept is free to adopt
the same trick later, and this round must not invent a public surface for it.
The engine allocates one extra resource; it is registered with the scope like
every other.

### D8 — colors are CSS Color 4, and `DARK_CYAN` is a recorded divergence

The console uses `Colors.WHITE`, `Colors.YELLOW` (identical to olc, fact 19) and
`Colors.DARK_CYAN` (fact 19: `#008B8B` against olc's `(0,128,128)`, a 11/255
green-and-blue difference). Importing olc's literals would contradict the C4
decision that colors are CSS Color 4; the difference is one shade on a debug
overlay, and it is recorded rather than reproduced. The shadow's translucent
blues are outside this: they exist in no palette, so olc's own bytes are
reproduced exactly — `0x00007F7F` at the top-left and `0x00003F7F` at the other
three corners, since olc's `PixelF` truncates `0.5 * 255 = 127.5` to `127` and
`0.25 * 255 = 63.75` to `63` (`:1975-1978`).

### D9 — the output buffer keeps a bounded backlog while hidden

Divergence from olc, whose stream grows without bound while the console is
hidden and then parses everything at once (fact 8). Output is appended to a
character buffer with a bounded cap; the buffer is parsed into lines only while
the console is showing, so a hidden console still cannot grow without limit. The
cap and the drop policy (drop oldest) are micro-plan detail.

### D10 — entry text is a `String` with a code-unit cursor; the transcript is ASCII

The console transcript stays ASCII 32..126 as in olc (fact 9), because the
bitmap face and the fixed grid assume one cell per character. Text entry accepts
a full code point, so a pasted or IME-composed character is not split; a code
point above the transcript's range is accepted into the entry but drawn as the
font's own fallback, and the difference is recorded. This keeps the entry honest
without overloading the console grid.

## Divergences from olc (summary)

| # | Divergence | Why |
|---|---|---|
| D2 | Platform character events instead of one UK key table | Correct on any layout; no per-platform table to maintain |
| D3 | No stdout capture; explicit write surface | No common KMP equivalent |
| D5 | Layer-0 decals without mutating the user's draw target | The user's selected layer is their state, not the console's |
| D8 | `Colors.DARK_CYAN` instead of olc's literal | C4 settled on CSS Color 4 |
| D9 | Bounded backlog while hidden | Unbounded growth is an olc bug, not behavior to reproduce |

Everything else is parity: the idempotent show, the exit-key reset, the
grid-size and reset rules, the scroll rule, the wrap-at-width rule (fact 9), the
five edit operations, the history rules, both Enter paths, the inert default
hook, and the drawing layout and colors of fact 18.

## What the micro-plan will own

- the exact public shape and KDoc of the entry and console objects (D1);
- the event vocabulary, the queue drain point and the repeat policy (D2);
- the write surface's signature and the backlog cap (D3, D9);
- where the white fill sprite is created and how the polygon-decal tint list is
  built (D7);
- the test seams: a scripted input feeder for the engine tests, and the
  black-box pins for entry, history, grid, scroll and composition order;
- the code-point rule at the entry's edge (D10).

## Round split

One round, because the concept is one; the steps are ordered so each is
independently green: (1) the event queue and both backends, (2) the entry and
its public surface, (3) the console buffer, hook and drawing, (4) the engine
loop's two steps and the composition pin. If the entry alone exceeds the round
budget, the split point is after step (2) — the console has no meaning without
the entry, so the entry cannot be the second half.

## Gate

`tools/gradle build` once, before the two review axes. Test counts are read per
module and target, and the web target is forced with `--rerun-tasks` if its
counts are short (chunks `35`, `38`). No golden reference is expected to move:
the console draws through the decal path, and the new fill sprite is a normal
resource.
