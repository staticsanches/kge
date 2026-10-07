## 2026-10-07 — Engine console: the built-in command console and its text entry

The concept that follows the text API unification (`#46`) and precedes the
deferred rich-text concept. `main` carried neither a console nor a text entry, so
this is the port of an olc surface the restructure never brought over, not a
rewrite of existing behavior. Touch-point and micro-plan:
`docs/plans/2026-10-07-console-touchpoint.md` and
`docs/plans/2026-10-07-console-microplan.md`.

### Shipped

- **The text-input queue and both backends.** `TextInputEvent` is a sealed
  vocabulary of one `data class Character(codePoint)` and an `Edit` enum of the
  seven edit keys. `RawInput` gained a private ordered queue, the two
  `@KGESensitiveAPI` driver mutators `typedCharacter`/`pressedEdit`, and
  `internal drainTextInput()`. The JVM backend installs a `GLFWCharCallback` and
  enqueues an edit on `GLFW_PRESS` and `GLFW_REPEAT` (never on release); the web
  backend's existing keydown/keyup listener calls `applyTextKey(key, code,
  repeat)`, which enqueues a character only when `event.key` is exactly one code
  point and not a repeat. A file-private `singleCodePointOrNull` decodes the
  surrogate pair rather than assuming one code unit. No `js`/`external` accessor
  was needed: kotlin-wrappers exposes `key`, `repeat` and `code`.
- **`TextEntry`.** The editable line: `text`, `cursor`, `isEnabled`,
  `enable(text = "")` and `disable()`. The cursor is a code-unit index, but every
  movement and erase works on whole code points — LEFT/RIGHT step one code point,
  BACKSPACE erases the code point before the cursor, DELETE the one at it — so a
  cursor never lands inside a surrogate pair.
- **`Console`.** The transcript fed by the explicit `write` surface, the fixed
  `8x16`-cell grid derived from the viewport fit, the wrap-at-width and scroll
  rules, `clear`, the command history with its navigation, both Enter paths, and
  the optional frame-time suspension.
- **The engine's two steps.** The loop drains the platform's text input after the
  input latch and before `onUserUpdate`, routing characters and the four edit
  events to the entry while UP/DOWN/ENTER go to the console; after
  `onUserUpdate` it runs the console's own update and then its draw. With the
  console showing and its suspend flag set, the `Duration` handed to
  `onUserUpdate` is zero for that frame.
- **The drawing.** The transcript lines and the prompt are drawn with the core
  bitmap face at `8.fontPx` through the font's decal path; the shadow and the
  cursor are polygon decals over one console-owned `1x1` white sprite created
  lazily on the first draw and registered under the run's `ResourceScope` (two
  private keys, since a `Decal` does not own its `Sprite`), so no eager
  allocation was added to `Engine.start()` and no existing spec's resource
  accounting moved.
- **The public surface.** `Engine` grew by exactly `textEntry`, `console`,
  `onTextEntryComplete` and `onConsoleCommand` (the latter defaulting to `false`,
  olc's inert hook). No new `Has*` interface, no new `KGEOverridable` service, and
  no stdout capture.

### Decisions

- **D1 — text entry and the console are one concept.** The console cannot exist
  without an editable line, so shipping either alone would have forced a
  provisional API for the other. `show`/`hide`/`clear`/`write` and the entry's
  `enable`/`disable` are the whole surface; `hide()` is a deliberate addition
  over olc's key-only close.
- **D6 — the console uses the built-in 8px face, not `textFont`.** Its own lease
  of the core family's default face is private, so a game swapping `textFont` to a
  TTF face cannot change the console's metrics or invalidate its grid.
- **D7 — the fill resource is console-local and lazy.** The shadow and cursor
  needed a solid fill, which the decal services cannot produce without a texture
  (both take a non-null `Decal`). No public fill-rectangle API was invented; a
  decal-primitives concept is free to adopt the same trick later.
- **Two internal seams, not one console step.** `Console.update(viewport)` owns
  the exit key, the grid, the reset and the backlog; `Console.draw(...)` owns the
  drawing, and the engine calls them back to back, skipping the draw if the update
  closed the console. `bufferSnapshot()` is the single internal read accessor for
  the transcript and the grid, the mechanism U6's D4 established.
- **`handleEdit` takes the sealed event**, not the `Edit` enum, because Kotlin
  does not smart-cast the enum conditions inside the engine's routing `when`.
- **Defensive guards olc does not have**, pinned as behavior rather than left as
  UB: no character is typed when the grid height is zero, and the line count is
  coerced at zero.

### Divergences from olc (recorded, accepted)

| # | Divergence | Rationale |
|---|---|---|
| D2 | Platform character events instead of the single UK key→symbol table | Correct on any layout, and no per-platform table to maintain |
| D3 | No `ConsoleCaptureStdOut`; `Console.write` is the explicit sink | No common KMP equivalent (`System.setOut` is JVM-only; intercepting `console.log` is fragile) |
| D5 | Console decals are queued on layer 0 without mutating the selected draw target | olc's `SetDrawTarget(0)` leaves a side effect on the game's own selection |
| D8 | `Colors.DARK_CYAN` (`#008B8B`) where olc's literal is `(0,128,128)` | C4 settled the palette on CSS Color 4. The shadow's translucent blues are outside the palette and are reproduced as olc's own bytes: `0x00007F7F` top-left, `0x00003F7F` at the other three corners, because `PixelF` truncates `127.5 → 127` and `63.75 → 63` |
| D9 | The hidden console's backlog is bounded (drop-oldest) | olc's stream grows without limit while hidden |
| D10 | Cursor moves and erases by code point; the transcript stays ASCII 32..126 | olc indexes bytes and would split a multi-unit character; the grid and the bitmap face assume one cell per character |
| — | `applyTextKey` takes three primitive DOM fields instead of the event | Matches the file's existing adapter style; building a `KeyboardEvent` in a test needs `KeyboardEventInit`/`KeyCode` wrappers |
| — | Console font is a second lease of the core face | D6: independent of the swappable `textFont` |

`show()` also forces the exit key's edge bits in the frame's `InputState`, as
olc's `ConsoleShow` does, which is a deliberate side effect of a public call on
the game-visible snapshot: the press that opened the console cannot close it on
the next frame, while a non-exit key keeps its edges.

### Mid-round corrections

- **The wrap-by-one claim was wrong.** The touch-point and micro-plan first
  asserted that a line overflows the width by one character before wrapping. The
  extracted `TypeCharacter` appends, then increments, then tests
  `x >= size.x`, so a positive-width line holds exactly `grid.x` characters and
  the next character starts the next line; a line exceeds the width only when the
  width is not positive. Both documents were corrected in the round and pin 14
  asserts the code-derived behavior.
- **The expected-count arithmetic was wrong for the JVM.** Pin 35 lives in
  `webTest`, which compiles into js and wasmJs only, so the JVM target gains 36
  pins, not 37. The corrected expectations are 782 jvm and 816 js/wasmJs, and
  that is what the gate reports.

### Not in this round

`ConsoleCaptureStdOut` (D3), text selection, word-wise editing, IME composition
preview, a configurable grid or font, console commands of any kind, and any
public fill-rectangle API. Outside the console, the deferred rich-text concept
(roadmap, 2026-10-04) is what remains.

### Verification

`tools/gradle build` green on the tree (301 actionable tasks, exit 0): every
target's tests, ktlint, the metadata/kLIB compilation and `buildSrcCheck`. Counts
per module and target, from the gate's own result XMLs:

| module | jvm | js | wasmJs |
|---|---|---|---|
| `kge-core` | 782 (3 skips) | 816 | 816 |
| `kge-text-ttf` | 168 | 169 | 169 |
| `kge-benchmark` | 21 | — | 22 |
| `kge-font-roboto` | 10 | 10 | 10 |
| `kge-test-support` | 8 | 9 | 9 |
| `buildSrc` | 27 | — | — |

`kge-core` moved from 746 jvm / 781 js / 781 wasmJs to 782/816/816 — the 36 pins
the JVM target runs and the 35 that js and wasmJs run, with the three skips
unchanged. Every other module is unchanged, no golden reference moved, and no
target reports zero tests (chunk `35`). The 37 pins are 3 in
`TextInputQueueTest`, 7 in `TextEntryTest`, 9 in `ConsoleBufferTest`, 5 in
`ConsoleDrawTest`, 8 in `EngineConsoleTest` and 2 in `ConsoleHistoryTest` (all
`commonTest`, all three targets), 2 in `TextInputJvmTest` (jvm) and one added to
`WebDriverInputTest` (js and wasmJs); spec files went 106 → 113 on the JVM and
104 → 110 on the web targets.
