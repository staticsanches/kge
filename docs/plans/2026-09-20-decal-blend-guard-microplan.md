# Decal blend-mode guard micro-plan

**Date:** 2026-09-20. Touch-point decisions in
`docs/plans/2026-09-20-decal-blend-guard-touchpoint.md`; the measured basis and
the lever verdicts are in `docs/decisions/phase-1/32-benchmark-renderer-levers.md`.
Round `#33`, independent of `R6` round C.

## Working mode

- **No subagents** for implementation; doubts resolved in-session. One step =
  compare → test (red) → implement (green on jvm + js + wasmJs) → mark.
- **Oracle = olc v2.30 + `main`.** The guard compares the **mode**, resets in
  `PrepareDrawing`, and leaves `disable(CULL_FACE)`/`bindTexture` unconditional —
  `Renderer_OGL33::SetDecalMode` (`olcPixelGameEngine.h:6191-6206`), `PrepareDrawing`
  (`:6169-6188`), `DrawDecal` (`:6228-6253`); `main`'s `BaseRenderer.decalMode`
  (`git show main:kge-core/src/commonMain/kotlin/dev/staticsanches/kge/renderer/BaseRenderer.kt`).
- **This is a parity correction, not an optimization.** The lever is inside the
  ±5% run band on both platforms (`#32` §3.3) — do not re-litigate it as speed,
  and do not repeat the measurement.
- **Hard invariant:** the GL call sequence is unchanged for every **emission**;
  only a repeat of the current mode drops the `blendFunc`. Geometry, upload and
  primitive mapping are untouched.

## Resume tracker

- [ ] **1. The mirror + the guard** (`BuiltInQuad.decalMode`, `prepareDrawing`
      re-arm, guarded `drawDecal`)
- [ ] **2. Update the three recording expectations**
- [ ] **3. Frame-level pin in the engine render step**
- [ ] **4. Gate + two-axis review + decisions-log close + commit**

## Contract

- **State (touch-point shape B):** the mirror lives in the bundle the renderer
  already resolves per draw — `BuiltInQuad`, an `internal` per-context type the
  scope owns — **not** on `DefaultRenderer` (the 2026-09-12 ruling rejected state
  in a fixed engine service) and **not** in a sibling scope holder (that would add
  a map lookup per decal, on a 3828-decal frame). `var decalMode: Decal.Mode =
  Decal.Mode.NORMAL`.
- **`prepareDrawing`** keeps its unconditional `enable(BLEND)` +
  `blendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA)` (olc `:6171`/`:6173`) and then
  re-arms the mirror to `NORMAL` (olc `:6172`). No GL call, so the existing
  `prepareDrawing` sequence test is unchanged.
- **`drawDecal`** keeps `disable(CULL_FACE)` → blend → `applyTexture()` order
  (olc `:6230`/`:6231`/`:6235`) and guards only the blend:
  `if (quad.decalMode != instance.mode) { blendFunc(mode.toGLBlend()); quad.decalMode = instance.mode }`.
- **`drawLayerQuad` neither calls `blendFunc` nor touches the mirror** — it keeps
  the blend the frame's `prepareDrawing` set, exactly as olc's `DrawLayerQuad`
  (`:6209`) does not call `SetDecalMode`, so a layer quad still inherits the
  previous decal's blend.
- **Comparing the mode, not the pair, is required:** `NORMAL`↔`WIREFRAME` hold the
  same pair and are re-issued anyway, exactly as both references do.
- **Invalidation contract:** the renderer owns the blend state from
  `prepareDrawing` to the next one; a third-party `GL.blendFunc` inside a frame is
  out of contract (the same exposure olc has). Re-arming unconditionally each
  frame is what makes the mirror unable to go stale across a context recreation.
- **KDoc:** `Renderer`'s "stateless: it holds no GPU objects" and
  `DefaultRenderer`'s "with no state of its own" become "holds no GPU objects and
  no state of its own — its built-in resources **and the blend mode last applied**
  live in the engine-owned scope"; `BuiltInQuad` says it also mirrors that mode.
  No block grows past its current size.
- **Deliberate non-changes** (recorded so the review does not read them as
  omissions): no `disable(CULL_FACE)`/`bindTexture` dedupe (the `#3` round);
  `Decal.Mode.toGLBlend()` keeps returning a `Pair` on the emission path (0–1 per
  frame, against 3828 decals — inlining it into two `GLenum`s is a change to
  `GLMappings` for an unmeasurable gain); no `Renderer.setDecalMode` and no new
  public API; `Engine.decalMode` and its per-frame reset already match olc `:4881`
  and stay untouched.

## Steps

### 1. The mirror and the guard

- **Files:** `renderer/internal/BuiltInQuad.kt` (field + KDoc),
  `renderer/internal/DefaultRenderer.kt` (re-arm + guard + two KDocs),
  `renderer/Renderer.kt` (KDoc).
- **Tests (red first), in `commonTest/renderer/RendererDrawTest.kt`:**
  - `"a repeated decal mode issues no second blendFunc"` — `prepareDrawing` once,
    `drawLayerQuad` to grow the staging buffer, `recorder.clear()`, then **two**
    `drawDecal(..., NORMAL, ...)`: `calls.count { it.name == "blendFunc" }` is 0.
  - `"a decal mode change issues the olc pair exactly once"` — draw `NORMAL`
    (the re-armed state), clear, draw `ADDITIVE`, draw `NORMAL`:
    `calls.filter { it.name == "blendFunc" }.map { it.arguments }` is
    `[[SRC_ALPHA, ONE], [SRC_ALPHA, ONE_MINUS_SRC_ALPHA]]`.
  - `"the guard compares the mode, so wireframe re-issues the same pair"` — draw
    `NORMAL`, clear, draw `WIREFRAME`: exactly one `blendFunc`, with
    `[SRC_ALPHA, ONE_MINUS_SRC_ALPHA]`.
  - `"prepareDrawing re-arms the blend guard"` — draw `ADDITIVE`, clear,
    `prepareDrawing`, draw `NORMAL`: exactly one `blendFunc` in the interval (the
    frame's `NORMAL` pair from `prepareDrawing`), i.e. the reset is what stops the
    stale `ADDITIVE` from being reused and the `NORMAL` decal from re-issuing.
- **Red:** the repeated-mode test fails at 2 `blendFunc`s (expected 0), the re-arm
  test at 2 (expected 1) and the frame-level test at 4 (expected 1); the second
  and third already pass and stay as the transition/parity pins.

### 2. Update the three recording expectations

- `RendererDrawTest` `"every Decal.Mode maps to the exact olc blend pair"` becomes
  a **transition chain**: for each `Decal.Mode`, draw a decal with a *different*
  mode, `clear()`, then draw the mode under test and assert the recorded
  `blendFunc` list is exactly `[expected pair]` — stronger than the current
  "a blendFunc exists", and it makes `NORMAL`'s pair assertable at all.
- `RendererDrawTest` `"consecutive instances draw in order with no batching"`:
  drop the leading `"blendFunc"` from the expected sequence (its first instance is
  `NORMAL` right after `prepareDrawing`); the `ADDITIVE` instance keeps its own.
- `RendererSurfaceTest` `"the whole renderer surface is implemented"`: drop
  `"blendFunc"` from the expected sequence (its instance is `NORMAL`).
- `DecalIntegrationTest`: expected sequence unchanged (`ADDITIVE` after `NORMAL`);
  re-check its spec KDoc wording ("per-mode blend").
- `RendererTest` `"the built-in program and staging buffer are built once"`:
  unchanged (`blendFunc` count 2, both from `prepareDrawing`).

### 3. Frame-level pin

- **Test, in `commonTest/engine/EngineRenderStepTest.kt`:** a frame whose layer
  queues several `NORMAL` decals (mirroring the existing
  `"a queued decal instance is drawn after the layer quad"` case) records
  `calls.count { it.name == "blendFunc" } == 1` for the whole frame — the
  `prepareDrawing` one. This is the end-to-end statement of the guard.

### 4. Gate, review, close

- **Gate:** `tools/gradle build` green (all targets + ktlint + metadata/assemble).
  Visibility/API audit (the field is `internal`-scoped; no parameter added) and a
  resource audit (no allocation, no new resource).
- **Review:** two-axis (Standards + Spec, fresh sub-agents, no gate re-run) against
  the staged diff of this round only, with the base commit and the `tree:` hash;
  fix findings and re-review if the diff changes. The Spec axis carries the
  three-sources check: the guard, the reset and the unguarded neighbours against
  olc and `main`, plus the micro-plan's deliberate non-changes as *accepted*.
- **Close:** new decisions chunk `docs/decisions/phase-1/33-decal-blend-guard.md`
  (the finding, the shape-(B) rationale against the 2026-09-12 ruling, the
  measured band, the parity pin, the accepted exposure) + its row in
  `docs/decisions/phase-1.md`; AGENTS.md current-state line. Marker, then one
  commit — never stacked.

## Files

New: the micro-plan and the decisions chunk. Modified:
`kge-core/src/commonMain/.../renderer/internal/BuiltInQuad.kt`,
`.../renderer/internal/DefaultRenderer.kt`, `.../renderer/Renderer.kt`,
`kge-core/src/commonTest/.../renderer/RendererDrawTest.kt`,
`.../renderer/RendererSurfaceTest.kt`, `.../engine/EngineRenderStepTest.kt`,
`docs/decisions/phase-1.md`, `AGENTS.md`.

## Out of scope

- The `disable(CULL_FACE)`/`bindTexture` dedupe and any coalesced submission
  (the `#3` round, if adopted, in olc's reset-in-`prepareDrawing` shape).
- Retiring the `dedupeBlend` lever and the `renderer-blend` cell from the `#32`
  apparatus (a follow-up on that apparatus; the `#32` numbers were measured
  pre-core and stay recorded as such).
- `R6` round C (text) — this round lands first, as its own commit.
