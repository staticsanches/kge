## 2026-09-13 — Engine C10b (input): touch-point + close

The second of the three `C10` sessions (`C10a` loop/window/time → **`C10b`
input** → `C10c` addons); it closes with its own gate and log entry. Design
material: `docs/plans/2026-09-13-c10b-input-microplan.md` (written just-in-time)
over the `C10` touch-point decision 7. It delivers the common `KeyboardKey`,
`ButtonState`, the mouse, the modifiers, focus, the `RawInput` driver seam, the
common `InputTracker`, the `InputState` on the engine, and the JVM GLFW / web DOM
backends (with the web resize path). The addon roles/mixins, layers/content and
text are `C10c`/`R6`.

### Keyboard model (supersedes decision 7's "common `KeyCode`")

- **`KeyboardKey` is an entry-less `expect enum`** with the native members per
  platform (JVM = GLFW names, web = W3C `code` names), as `main` already had.
  This **supersedes** the C10 touch-point decision 7 ("a single common `KeyCode`,
  not `main`'s expect enum"): the owner keeps each platform's full native key
  set. Verified Kotlin constraint: an `expect enum` must have the **same entries
  as its actual**, so divergent native sets require an entry-less expect enum;
  `entries`/`ordinal` resolve per platform, so a `KeyboardKey.entries.size`-sized
  indexed array is portable.
- **The common vocabulary is the intersection, exposed as companion extension
  properties** (`KeyboardKey.escape`, `KeyboardKey.a`, `KeyboardKey.k0`, …), in
  full Kotlin lowerCamelCase derived from olc's `Key`. They are backed by an
  internal `KeyVocabulary` enum + `internal expect fun keyboardKey(vocab)` with
  an exhaustive `when` per platform (a missing mapping is a compile error). This
  was chosen because `expect val` is **not** allowed as a companion member
  without a backing field (`Modifier 'expect' is not applicable…`); the single
  expect function sidesteps the restriction. Cost recorded: an app in another
  package imports each vocabulary key extension it uses (or the package star).
- `return`/`oem8` are absent from the vocabulary: olc maps main-Enter, Return and
  keypad-Enter all to `Key::ENTER` (`olcPixelGameEngine.h:6799`, `:7124`,
  `:7703`) and `Key::RETURN` is dead; `oem8` has no W3C mapping.

### Edge state and tracking

- **`ButtonState`** is the olc `HWButton` triple (`pressed`/`released`/`held`,
  `:1029`) as a `@JvmInline value class`.
- **`InputTracker`** (internal, common) latches it once per frame with olc's
  `ScanHardware` algorithm (`:4802-4820`): a rising transition sets `pressed` +
  `held`, a falling one `released`; a repeat (still down) never re-fires
  `pressed`. Masks are `IntArray` bitfields (`PackedBits`) — decision 7's bitsets
  with `Long` avoided on the `js` target (C10 record, log #24).
- **Mouse in pixel/screen space** (olc parity): the window point is scaled to
  framebuffer pixels, offset and rescaled through the letterbox to the screen,
  clamped `0..screen-1` (olc `olc_UpdateMouse`, `:4575-4596`). Buttons
  `LEFT`/`RIGHT`/`MIDDLE` (olc `Mouse`, `:1018-1022`); the wheel is the frame's
  accumulated delta, consumed by the latch (olc `:4570`, `:4828-4830`).
- **`Modifiers` is one common snapshot** (shift/ctrl/alt/superKey/capsLock/
  numLock) translated by each platform from its event mods — not `main`'s
  `expect value class` whose masks were platform-private.
- **`InputAction`/`Press`/`Release`/`Repeat` are dropped**: the `ButtonState`
  model is strictly richer than `main`'s last-event enum, and no ordered event
  list ships this round (decision 7).

### Driver seam and engine wiring

- **`Driver` exposes `input: RawInput`** — the mutable platform state its
  callbacks fill (`@KGESensitiveAPI` setters). The JVM backend creates the GLFW
  callbacks explicitly and **retains them** (`glfwSet*Callback` returns the
  *previous* callback, so the lambda overload leaked them) and frees them on
  close; the web backend registers DOM listeners and keeps a disposer per
  listener, all removed on close. Both install inside the creation guard, so a
  failure tears the window/canvas down with the callbacks already installed.
- **The wheel is positive-up on both backends**: GLFW reports the offset
  positive away from the user, the DOM `deltaY` positive down — the web adapter
  negates it, as olc's emscripten backend does (`:7865`).
- **The engine latches once per frame**, after `pollEvents` and before
  `onUserUpdate`, so the callback observes the frame's `InputState`. The fit is
  computed at the frame start and reused by the latch and the render step.
- **`Engine.input: InputState`** is the read-only per-frame snapshot
  (`key`/`mouseButton`/`mousePosition`/`mouseWheel`/`modifiers`/`focused`); the
  `HasInput` role wrapping it is `C10c`.
- **Web resize (carry-forward from `C10a`)**: the owned default canvas re-fits
  the viewport preserving the configured aspect (`fitCanvasSize`); a caller-owned
  canvas follows its CSS client size. The backing store tracks
  `devicePixelRatio`; the mouse maps through the new size.

### Divergences and limitations

- **Focus loss releases held keys/mouse buttons** (owner): olc only flags
  `bHasInputFocus` (`:2647`, `:4608-4611`) and leaves keys stuck. The backend
  clears the raw bits on blur, so the next latch emits `released` — accepted
  divergence fixing the sticky-key defect.
- The JVM left/right modifiers are native-only; the vocabulary `shift`/`ctrl`
  map to the left keys and `Modifiers` carries the either-side state.
- **Web events are not `preventDefault()`-ed** (olc's emscripten backend returns
  `EM_TRUE`, `:7858`/`:7867`/`:7929`): browser defaults (F-keys, wheel scroll,
  middle-click autoscroll) stay with the host page; a game that needs
  suppression adds its own listener. Accepted divergence.
- **The web backend maps the DOM `code` directly** and does not reproduce olc's
  emscripten `numPadActive` remap (`:7827-7849`): with Num Lock off the browser
  reports `Home`/arrows for the numpad while GLFW reports the `KP_*` keys — the
  platform-native behavior is accepted.
- Synthetic DOM events are not portable to construct across `js`/`wasmJs`
  (the `@JsPlainObject` builder is unavailable), so the web adapter mapping is
  pinned by unit tests over the primitives; event delivery is exercised by the
  real-backend smoke (listener registration/teardown).

### Review and gate

Two-axis review (Standards + Spec, fresh sub-agents; reports
`.opencode/reviews/c10b-input-{standards,spec}.md`) over the staged diff. Round
1 (both FAIL): the GLFW callbacks were created through the lambda overload whose
return is the *previous* callback, so they were never retained/freed (the
reviewers' shared Critical/Important finding); the web wheel sign was the
opposite of the JVM/olc; `RawInput.reset` and `MouseButton.code` were unused
public API; the JVM mapping/adapter and web wheel/move were untested; two test
hygiene minors. Fixed in one round (explicit `GLFW*Callback.create(...).set`,
wheel negation, dead API removed, `KeyboardKeyJvmTest` + adapter tests). Scoped
re-review clean. Gate: `./gradlew build --rerun-tasks` green (JVM + js browser +
wasmJs browser + ktlint + assemble/metadata).

### Carry-forward

- `C10c` (addons): the ISP role interfaces (`HasTime`/`HasWindow`/`HasInput`/…),
  the addon mixins, layers/content drawing.
- Text entry / key symbols are `R6`; an ordered event list is intentionally not
  shipped.
- The `js` `Duration`/`Long` hot-path constraint stands (benchmark-gated).
