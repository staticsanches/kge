## 2026-09-11 — State (E3): `C8` dissolved into `C10`; time/state touch-point findings

The roadmap carried a standalone `C8` "state" concept between `S6` and `C9`,
with `WithKGEState`, window composition, pure state machines, and a
platform-coupled `TimeState` clock. At the touch-point the claims were examined
against `olcPixelGameEngine` v2.30 and `main` (evidence, not mandate). The owner
decided **not** to ship a standalone state concept: E3 is folded into the `C10`
engine concept, where the loop/window is the real consumer.

### Decision (owner)

- **`C8` is dissolved.** No standalone state concept; its substance (time and
  the rest of E3) is handled at the `C10` touch-point. The effective order
  becomes `S6` → `C9` (renderer/GL/decals, R5→R4→R3) → `C10` (engine: E1
  loop/window + E2 addons + E3 state + E4 KeyCode/InputAction) → `R6`.
- **Driver — the consumer lens (roadmap lens #1).** No part of `C8` has a real
  consumer before the loop/window exists: the loop advances time, owns the
  frame, and is what reads the screen dimensions/state. Building the state API
  first would ship a provisional API that a later concept must break.

### Rationale (verified against the sources, 2026-09-11)

- **`WithKGEState` is a `main` artifact, not an olc concept.** olc has no state
  object: fields live directly on `PixelGameEngine` (e.g. `vScreenSize`,
  `fLastElapsed`, `nPixelMode`, `vLayers`, `nTargetLayer`;
  `olcPixelGameEngine.h:1593-1634`). `main`'s `WithKGEState` is a god-interface
  implemented by `Window` and forwarded by `WindowDependentAddon`, mixing
  concerns the new roadmap already reassigned: `pixelMode`→R1,
  `decalMode`/`decalStructure`/`suspendTextureTransfer`/`layers`/
  `targetLayerIndex`/`drawTarget`→R3/R4/R5, `fontSheet`/`tabSizeInSpaces`→R6,
  `inputState`→E4, `mainResource`/`dimensionState`→E1/E6/R4. Only `timeState`
  is genuinely "state".
- **`TimeState` in `main` has a defect and a coupling.** `TimeStateJVM` uses
  `glfwGetTime()` (seconds) while `TimeStateJS` uses `Date.now()`
  (milliseconds): `elapsedTime` is not the same unit per platform, and the KDoc
  states the opposite of the code. FPS is computed inside the clock (via an
  `fpsUpdater` callback) with per-platform magic constants (`>=1.0` vs
  `>=1000`), whereas olc computes FPS in the core loop
  (`olcPixelGameEngine.h:4928-4938`) and exposes `elapsedTime` in seconds
  (`:4787-4791`, `:2692`).
- **`DimensionState` is window/GL, not "state".** It carries `windowPixelSize`
  (HiDPI), `windowSizeInPixels`, `viewportSize`/`viewportPosition` and
  `recalculateViewport()` — the last mirrors olc `olc_UpdateViewport`
  (aspect letterbox, `:4517-4549`) — all of which need the real window and GL
  viewport (E1/E6/R4/R5). The pure viewport math already has an owner: the R2
  `Viewport` type.
- **"Pure state machines" has no source.** Neither `olcPixelGameEngine` nor
  `main` contains a state-machine type. The phrase entered the roadmap without
  evidence; the plausible referents (loop lifecycle `bAtomActive`/`bPaused`,
  addon enable/disable) belong to E1.

### Carry-forward notes for the `C10` touch-point (not decided here)

- **One time unit on every platform** (parity floor): olc's seconds; `main`'s
  seconds-vs-milliseconds split is rejected.
- **FPS belongs to the loop/frame accounting**, not to the time source.
- **The platform need is a monotonic `now()`**; whether that is a plain
  `expect`/`actual` or a `KGEOverridable` service (deterministic tests, virtual
  time) is a `C10` decision.
- **`DimensionState` splits to E6 (window) / R4 (GL viewport)**; the pure
  letterbox math refines the R2 `Viewport` family.
- **`WithKGEState` is re-derived from the real consumers** at `C10`; it is not
  a port of `main`'s interface.
- **`C10` is large** (E1+E2+E3+E4); the touch-point should consider splitting it
  into sub-sessions.
