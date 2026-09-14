# Engine input micro-plan (C10b, interactive execution)

**Date:** 2026-09-13. Base touch-point: `docs/plans/2026-09-13-c10-engine-touchpoint.md`
(decision 7) and the `C10a` close (`docs/decisions/phase-1/24-engine-c10a.md`).
Second of the three engine sessions (`C10a` loop/window/time → **`C10b` input**
→ `C10c` addons). This delivers the input model: the common `KeyboardKey`, the
`ButtonState` edges, mouse, modifiers, focus, the platform mapping and the web
resize/input wiring. The addon roles/mixins and layer/content drawing are
`C10c`; an ordered event list and text entry are out of scope (decision 7).

## Working mode

- **No subagents.** One step = test (red) → implement (green on jvm+js+wasmJs) →
  mark. Doubts resolved in-session with the owner.
- **Oracle.** A common `RecordingDriver` (implements `Driver`, scripts raw input
  and frame lifecycle) plus a pure `InputTracker` unit test; the real backends
  are smoke-tested (JVM hidden GLFW window, web canvas).
- **Confinement.** Input is latched on the engine thread inside the frame loop
  (olc's `olc_CoreUpdate` position), never off it.

## Touch-point decisions closed (owner, 2026-09-13)

1. **`KeyboardKey` is an entry-less `expect enum` with the native members per
   platform** (JVM = GLFW names `KEY_A`…, web = W3C `code` names `KeyA`…), as
   `main` already had. **Supersedes** decision 7's "single common `KeyCode` (not
   `main`'s expect enum)": the owner wants each platform to keep its full native
   key set.
2. **The common intersection vocabulary is exposed through the companion.** A
   single platform mapping backs **companion extension properties** on
   `KeyboardKey`, named in **Kotlin lowercase style** derived from olc's `Key`
   (`KeyboardKey.escape`, `KeyboardKey.a`, `KeyboardKey.k0`, `KeyboardKey.up`,
   `KeyboardKey.space`, `KeyboardKey.oem1`, …). The mapping is an internal
   common vocabulary enum + `internal expect fun keyboardKey(vocab): KeyboardKey`
   with an exhaustive `when` per platform (a missing mapping is a compile error).
   Common code writes `KeyboardKey.escape`; each platform maps it to its own
   member. The vocabulary is the **intersection** (keys both platforms can
   produce with a real value); platform-only keys stay accessible only in
   platform code.
3. **`ButtonState`** — the app-facing triple `pressed`/`released`/`held` (olc
   `HWButton`, `:1029`). A value type, not a class.
4. **Edges latch once per frame** (olc `ScanHardware`, `:4802-4820`): rising
   transition → `pressed` + `held`; falling → `released`; key-repeat keeps
   `held`, never re-fires `pressed`.
5. **Mouse in pixel/screen space** (olc parity): the window position is mapped
   through the same letterbox viewport to `0..screenWidth/Height`, clamped (olc
   `olc_UpdateMouse`, `:4575-4596`). Buttons `LEFT`/`RIGHT`/`MIDDLE` (olc
   `Mouse`, `:1018-1022`); the wheel is a per-frame delta (olc
   `olc_UpdateMouseWheel` `:4570` + latch `:4828-4830`).
6. **Modifiers are a `Modifiers` snapshot** (shift/ctrl/alt/super/capsLock/
   numLock) translated by each platform from its event mods. One common value
   type with a common bit layout — **not** `main`'s `expect value class` (whose
   masks were platform-private).
7. **Focus loss clears held keys and synthesizes releases** (divergence, owner):
   olc only flags `bHasInputFocus` (`:2647`, `:4608-4611`) and leaves keys stuck;
   clearing the raw state makes the next latch emit `released`, fixing the
   sticky-key defect. Recorded as an accepted divergence.
8. **`InputAction`/`Press`/`Release`/`Repeat` are dropped**: the `ButtonState`
   model is strictly richer than `main`'s last-event enum, and no ordered event
   list ships this round (decision 7).
9. **Web resize is in scope** (carry-forward from `C10a`): a window `resize`
   listener updates the logical size and the canvas backing store; the mouse
   maps through the new size.

## Contract

- **`KeyboardKey`** — entry-less `expect enum` + platform members; companion
  extensions for the common intersection vocabulary (decision 2).
- **`ButtonState`** — read-only value type: `pressed`, `released`, `held`.
- **`MouseButton`** — common enum `LEFT`/`RIGHT`/`MIDDLE`.
- **`Modifiers`** — common value type: `shift`, `ctrl`, `alt`, `superKey`,
  `capsLock`, `numLock`.
- **`RawInput`** — the mutable platform state a `Driver` fills from its
  callbacks: key-down and mouse-button-down bits, window position, wheel delta,
  modifiers, focused. Mutators are the driver seam (sensitive API); the engine
  reads it, never writes it.
- **`InputTracker`** (internal, common, pure) — given `RawInput` + the viewport
  fit, latches `ButtonState` bitsets and the mouse in pixel space; sizes the
  bitsets from `KeyboardKey.entries.size`.
- **`InputState`** — the read-only per-frame snapshot the engine publishes:
  `key(KeyboardKey): ButtonState`, `mouseButton(MouseButton): ButtonState`,
  `mousePosition: Int2D` (pixel space), `mouseWheel: Int`, `modifiers:
  Modifiers`, `focused: Boolean`. Exposed on `Engine` (`val input: InputState`);
  the `HasInput` role wrapping it is `C10c`.

## Kotlin constraints (verified 2026-09-13, compiled on jvm + js)

- `expect enum class KeyboardKey` **must have the same entries as its actual**;
  to allow divergent native members the expect enum must be **entry-less** (as
  `main` had it). `entries`/`ordinal` resolve per platform at runtime, so a
  `KeyboardKey.entries.size`-sized array indexed by `ordinal` is portable; only a
  fixed cross-platform numeric code is not.
- `expect val` is **not** allowed as a companion member without a backing field
  (`Modifier 'expect' is not applicable to 'member property without backing
  field or delegate'`). Hence the companion extensions are backed by a single
  `internal expect fun` per platform (exhaustive `when`), which sidesteps the
  per-member `expect` restriction and gives compile-time mapping completeness.
- Prefer `IntArray` bitfields over `LongArray` for the bitsets: on the `js`
  target `Long` is an emulated boxed two-`Int` class (C10 record, log #24).

## Loop wiring (olc `olc_CoreUpdate` parity)

```
elapsed = accumulator.tick(Time.elapsed())
driver.pollEvents()                 // platform callbacks fill driver.input
input.latch(driver.input, fit)      // ScanHardware + mouse mapping, once per frame
if (!onUserUpdate(elapsed)) active = false
renderFrame(...)
```

The fit is computed/cached in `renderFrame`; the latch must read the cached fit
(or compute it first) to map the mouse consistently with the drawn frame.

## Resume tracker

- [x] **0.** Contract skeleton + `RawInput` (the `RecordingDriver` scripting landed with step 5)
- [x] **1.** `KeyboardKey` expect enum + platform members + mapping tables + common vocabulary
- [x] **2.** `ButtonState` + `MouseButton` + `Modifiers` value types
- [x] **3.** `InputTracker` edges (pure: pressed/released/held, repeat, focus clear)
- [x] **4.** Mouse mapping to pixel space through the viewport fit + wheel latch
- [x] **5.** `RawInput` on `Driver` + JVM GLFW callbacks + web DOM listeners
- [x] **6.** `InputState` published on `Engine`; wire `latch` into the loop
- [x] **7.** Web resize/input mapping (carry-forward)
- [x] **8.** Real-backend smoke: the production drivers register the listeners and
  tear them down (JVM GLFW callbacks freed on close; web disposers removed);
  synthetic event injection is not portable, so the adapter mapping is pinned by
  unit tests instead
- [x] **9.** Gate + two-axis review + decisions-log close

## Steps

### 0. Contract skeleton + test double

- **Tests:** `RecordingDriver` exposes a `RawInput` whose key/mouse bits, wheel
  and focus can be scripted between frames.
- **Decided:** `RawInput` is the driver seam (mutators sensitive API); the
  engine owns the tracker and the snapshot.

### 1. `KeyboardKey` + vocabulary

- **Tests:** the JVM mapping resolves a GLFW code to a native member and the web
  mapping resolves a W3C `code`; the vocabulary is exactly the **intersection**
  (a test asserts each vocabulary key maps to a non-`UNKNOWN`/non-`Unidentified`
  member on its platform); `entries.size`-sized indexing round-trips.
- **Files:** `commonMain/.../engine/input/KeyboardKey.kt` (+ vocabulary),
  `jvmMain` GLFW actuals/mapping, `webMain` W3C actuals/mapping.
- **Decided:** vocabulary naming is **full Kotlin lowerCamelCase words**
  derived from olc's `Key`: `a`…`z`, `k0`…`k9`, `f1`…`f12`, `up`/`down`/`left`/
  `right`, `space`, `tab`, `shift`, `ctrl`, `insert`/`delete` (olc `INS`/`DEL`),
  `home`/`end`, `pageUp`/`pageDown`, `backspace` (olc `BACK`), `escape`,
  `enter`, `pause`, `scrollLock`, `numpad0`…`numpad9`,
  `numpadMultiply`/`numpadDivide`/`numpadAdd`/`numpadSubtract`/`numpadDecimal`,
  `period`, `equals`, `comma`, `minus`, `oem1`…`oem7`, `capsLock`. `return` is
  **absent**: olc maps main-Enter, Return and keypad-Enter all to `Key::ENTER`
  (`:6799`, `:7124`, `:7703`), and `Key::RETURN` is a dead member. Other known
  platform-only (excluded) keys: `oem8` (no W3C mapping); additions/removals of
  this list are a finding if the mapping tables disagree.

### 2. Value types

- **Tests:** `ButtonState` exposes the triple without allocation (declared-type
  return); `Modifiers` reads each flag; `MouseButton` has the olc values.
- **Decided:** common `Modifiers` bit layout (not `expect`); `superKey` avoids
  the Kotlin `super` keyword.

### 3. `InputTracker` edges

- **Tests (pure):** down → `pressed` + `held`; stay → `held` only; up →
  `released`; down-up-down across frames → two `pressed` edges; a repeat event
  (still down) never re-fires `pressed`; focus loss emits `released` for every
  held key and clears `held`.
- **Decided:** edges are computed once per `latch`, not in the callbacks.

### 4. Mouse mapping

- **Tests (pure):** a window point maps through a known letterbox to the screen
  pixel; out-of-view clamps to `0..screen-1`; same aspect as olc `olc_UpdateMouse`;
  the wheel delta is the accumulated value for one frame and resets the next.
- **Decided:** input in pixel/screen space (decision 5); the physical→logical
  scale uses `framebufferSize/windowSize`.

### 5. `RawInput` on `Driver` + backends

- **Tests:** `RecordingDriver` satisfies the new member; the real backends are
  smoke-tested in step 8.
- **Files:** `commonMain/.../engine/input/RawInput.kt`, `Driver.kt`; `jvmMain`
  GLFW key/mouse/cursor/scroll/focus callbacks; `webMain` canvas/window DOM
  listeners.
- **Decided:** `pollEvents()` is the refresh point on JVM; on web the DOM queue
  is already filled and `pollEvents()` stays a no-op.

### 6. `InputState` on `Engine`

- **Tests (recording driver):** after `pollEvents` the loop latches input before
  `onUserUpdate`; the callback observes the frame's `ButtonState`; the snapshot
  is stable during the frame.
- **Decided:** exposed as `Engine.input`; the `HasInput` role is `C10c`.

### 7. Web resize

- **Tests:** a scripted size change updates `windowSize()` and re-scales the
  canvas backing store; the mouse maps through the new size.
- **Decided:** the listener is registered by the web driver and removed on
  `close` (resource discipline).

### 8. Real-backend smoke

- **Tests:** on JVM (hidden GLFW window) and web (canvas), script input through
  the real backend path and assert the engine observes it; macOS skips the JVM
  real-GL path as in `C10a`.
- **Decided:** smoke only — no cross-backend pixel-exactness.

### 9. Gate + review + close

- **Gate:** `./gradlew build --rerun-tasks` green (ktlint via `check`). Resource
  audit of the new callbacks/listeners (registered → removed on `close`); every
  public parameter has an observable effect; narrowest visibility for the
  tracker/vocabulary internals. Two-axis review (Standards + Spec, three sources
  of truth), decisions-log close entry.

## Files

New: `commonMain/.../engine/input/{KeyboardKey,ButtonState,MouseButton,Modifiers,
RawInput,InputTracker,InputState}.kt`, `jvmMain`/`webMain` key+mouse mappings and
driver callbacks, `commonTest` tracker/mapping tests, real-backend smoke in
`jvmTest`/`webTest`. Edited: `Driver.kt`, `Engine.kt`, `RecordingDriver.kt`.

Out of scope: addon roles/mixins (`C10c`); layers/content (`C10c`); ordered
event list; text entry/`GetKeySymbol` (R6); touch/gamepad; drag-and-drop.

## Open items (micro-plan, owner if reached)

- None. The `RawInput` masks are `IntArray` bitfields (decision 7's bitsets;
  `Long` is avoided on the `js` target per log #24).
