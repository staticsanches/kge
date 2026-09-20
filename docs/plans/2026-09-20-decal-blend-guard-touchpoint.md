# Decal blend-mode guard — touch-point material

**Date:** 2026-09-20. Pre-touch-point material for a small renderer parity
correction, found while measuring the renderer optimization levers (the
`RendererBlend` benchmark cell of `kge-benchmark`). Decisions closed in
discussion are recorded here; the micro-plan is TDD, written after.

## The finding

`DefaultRenderer.drawDecal` issues `glBlendFunc` for **every** decal instance:

```kotlin
val (source, destination) = instance.mode.toGLBlend()
GL.blendFunc(source, destination)
```

Both references guard that call on a mode change. No rationale for dropping the
guard is recorded anywhere (`docs/decisions/phase-1/22-renderer-gl-decal.md`
never mentions the blend mode or the mode guard). By the repo rule this is a
finding on two counts at once: **divergence from olc** and **regression against a
`main` solution**.

## The three sources

### olc v2.30 — two fields, one guarded

- The **engine** carries `DecalMode nDecalMode` (`:1628`), set by
  `PixelGameEngine::SetDecalMode` (`:3479`) — a plain assignment, no GL call —
  and reset to `NORMAL` once per frame at `:4881`.
- The **GL33 renderer** carries its own `olc::DecalMode nDecalMode = -1`
  (`:5862`). `PrepareDrawing()` (`:6169`) resets it to `NORMAL` (`:6172`) right
  after an unconditional `glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)`
  (`:6173`), and `Renderer_OGL33::SetDecalMode` (`:6191`) is the guard:
  `if (mode != nDecalMode) { switch (mode) { … glBlendFunc … }; nDecalMode = mode; }`.
- `DrawDecal` (`:6228`) is `glDisable(GL_CULL_FACE)` → `SetDecalMode(decal.mode)`
  → `glBindTexture(…)` — the first and third **unconditional**, the blend
  **guarded**. `nDecalMode` is read again only to pick the wireframe primitive
  (`:6248`).
- `DrawLayerQuad` (`:6209`) does not touch the blend mode: a layer quad inherits
  whatever the previous decal left. The guard does not change that.

So the guard is per **mode**, not per blend pair: `NORMAL` → `WIREFRAME` and back
re-issue an identical pair, because the enum values differ. That is olc's
behavior and is preserved.

### `main` (evidence, not mandate) — the same guard, over a mode-switching field

`BaseRenderer` held a `private var decalMode: Decal.Mode?` whose setter issued
`GL.blendFunc` only on change, with `null` meaning "the GL state is unknown,
force reassignment"; `prepareDrawing` set it `null` then `NORMAL` to force it.
`drawDecals` set it once per **run** of compatible instances (its
`multiDrawArrays` batching), so there the guard was already amortized over a
batch. `disable(GL_CULL_FACE)` and `bindTexture` were unguarded there too — like
olc.

### KGE — the engine half is already correct, the renderer half was lost

- `Engine.decalMode` (`Engine.kt:78`) is olc's engine field: it is what
  `DrawDecalAddon` and the decal string path stamp into `DecalInstance.mode`, and
  `renderFrame` resets it to `NORMAL` at the frame start (`Engine.kt:263`) exactly
  as olc does at `:4881`. **No change needed on this side.**
- `DefaultRenderer.prepareDrawing` (`:30`) does issue the frame blend state
  unconditionally (`enable(BLEND)` + `blendFunc(NORMAL)`) — olc's `:6171`/`:6173`.
  What is missing is olc's `nDecalMode = NORMAL` (`:6172`): there is no mirror to
  re-arm, so there is nothing for `drawDecal` to compare against.
- `DefaultRenderer`'s KDoc states the renderer is stateless and "holds no GPU
  objects".

## Measured: this is a parity round, not a performance round

640×360, 3828 glyphs/frame, uncapped, real GPU, compared against the pass-through
decorator (same wrapper, no lever) because the wrapper's own cost varies ~±5%
between runs. Full tables, method and traps:
`docs/plans/2026-09-20-renderer-lever-measurements.md` (recorded as `#32`).

| cell | JVM real GL | web real GPU |
|---|---|---|
| baseline | 6.83 ms | 8.07 ms |
| pass-through (control) | 6.56 ms | 7.82 ms |
| **blend guard only** | 6.39 ms → −2.6% (inside the band) | 7.77 ms → −0.6% (inside the band) |
| guard + `disable`/`bindTexture` dedupe | 5.01 ms → **−23.6%** | 6.67 ms → **−14.7%** |

The guard alone is not measurable on either platform: both deltas sit inside the
~±5% run band. The motive for this round is therefore **parity**, and the numbers
above are recorded as the honest expected benefit so the round is not later
re-litigated as an optimization. The remaining ~20%/15% is the
`disable(CULL_FACE)` + `bindTexture` dedupe, which **neither reference does** and
which stays out of this round (see scope, below).

## Decisions closed

1. **Round identity.** An independent small round with its own commit, before
   round C of the text work resumes. It touches renderer internals only; no
   public API change; no interaction with `kge-text-ttf`.

2. **What is guarded.** The blend mode only, comparing `Decal.Mode` values (olc
   and `main` both compare the mode). Consequence pinned by a test:
   `NORMAL`↔`WIREFRAME` re-issues the identical pair. `disable(CULL_FACE)` and
   `bindTexture` stay unconditional — as in both references.

3. **Where the mirror lives.** The guard needs one remembered value, so the
   question is only *where*, and the answer must survive the recorded ruling of
   2026-09-12 ("a fixed engine service must not carry mutable state … a solution
   with state in a service is not acceptable"; that ruling rejected the built-in
   **GPU objects** held in a lazy process-global field, and its stated reasons are
   context staleness and a teardown race). Two shapes:

   - **(A) a `private var` on `DefaultRenderer`.** Exact reference parity (olc's
     renderer field, `main`'s property). Defensible under a narrow reading of the
     ruling — a `Decal.Mode` is not a GPU object, and `prepareDrawing` re-issues
     the blend state *and* re-arms the mirror unconditionally before any decal of
     the frame, so a recreated context is repaired before the first draw. It does,
     however, contradict the ruling's literal wording.
   - **(B) a mirror owned by the resource scope**, i.e. stored on the built-in
     bundle `DefaultRenderer` already resolves per draw (`scope.get(QuadKey)`),
     alongside the quad whose context it describes. The **mechanism** is still
     olc's (reset in `prepareDrawing`, guard on change); only the storage differs,
     and it differs for the reason the owner already ruled: the scope *is* the
     per-context ownership boundary, so the value is created with the context,
     dies with it, and cannot go stale across a context recreation or leak into a
     second run. There is precedent for mutable run state in the scope
     (`LayersKey : ResourceScope.Key<LayerStack>`, `Engine.kt:169`).

   **Decided: (B).** It preserves the observable behavior of both references
   exactly, costs nothing on the hot path (the bundle is already resolved, no new
   key or lookup), and does not require arguing against an owner ruling — it
   satisfies it. (A) was the alternative if reference-exact storage were valued
   over that consistency. The micro-plan picks whether the field sits on the
   existing built-in bundle or on a sibling holder registered in the same scope;
   that is a naming detail, not a behavioral one.

4. **Invalidation contract.** After `prepareDrawing`, the renderer owns the blend
   state until the next `prepareDrawing`. A third-party `GL.blendFunc` inside a
   frame is outside the contract — the same exposure olc has, whose
   `nDecalMode` is likewise not invalidated by direct GL use. Recorded as
   accepted, not as a defect, and stated in the KDoc of what changes.

5. **The renderer's statelessness claim is re-worded, not abandoned.** The
   current KDoc's "holds no GPU objects" stays true under (B) and becomes the
   precise statement of the rule: per-context GPU objects and per-context draw
   state live in the scope; the renderer holds neither.

6. **Log placement.** A new chunk `33-decal-blend-guard.md` with its index row
   (chunk 22 stays append-only history; the finding is recorded as what it is —
   a divergence found after C9 closed). The measured table and the rejected
   options go in that entry.

## Scope boundary (explicitly out)

- `disable(GL.CULL_FACE)` and `bindTexture` dedupe. Neither olc nor `main`
  guards them; a texture mirror is additionally invalidated by the public
  `Texture.apply()` path and by the upload/readback binds that happen outside
  `drawDecal`. If adopted, it belongs to the `#3` (coalesced submission /
  flush-hook) round, in the same reset-per-frame shape rather than as a general
  cache.
- Vertex-upload (`#2`) and batching (`#3`/`#6`) work. Measured and settled
  separately: per-draw `bufferSubData` is catastrophic (992 ms/frame JVM,
  266 ms/frame web) and is not a candidate.
- The `Engine.decalMode` field, `DrawDecalAddon` and the text decal path: already
  parity, untouched.

## Test inventory

The recording oracle stays; its expectations move from "a call is present" to
"a call happens on a transition".

| test | change |
|---|---|
| `RendererDrawTest` "every Decal.Mode maps to the exact olc blend pair" | becomes a **transition chain**: each mode is asserted after a *different* predecessor, because the first `NORMAL` decal after `prepareDrawing` legitimately emits nothing |
| `RendererDrawTest` "consecutive instances draw in order with no batching" | the leading `NORMAL` instance no longer emits `blendFunc`; the `ADDITIVE` one does |
| `RendererSurfaceTest` "the whole renderer surface is implemented" | drop `blendFunc` from the `NORMAL` decal's expected sequence |
| `DecalIntegrationTest` "a decal is created, drawn … end to end" | unchanged (`ADDITIVE` after `NORMAL`); the spec KDoc's "per-mode blend" wording is re-checked |
| `RendererTest` "the built-in program and staging buffer are built once" | unchanged (`prepareDrawing` still issues `blendFunc` unconditionally, count 2) |

New tests:

1. **A repeated mode emits nothing.** Two `NORMAL` decals right after
   `prepareDrawing`: zero `blendFunc` calls in the interval.
2. **A mode change emits exactly one pair.** `NORMAL` → `ADDITIVE` → `NORMAL`
   in one frame: exactly two `blendFunc` calls, each with the olc pair.
3. **`prepareDrawing` re-arms.** After a frame ending on `ADDITIVE`,
   `prepareDrawing` still emits `enable(BLEND)` + `blendFunc(NORMAL)`, and the
   next `NORMAL` decal emits nothing. This is the frame-reset pin and the reason
   the mirror cannot go stale across frames (it is also what makes shape (B)'s
   scope ownership observable).
4. **The guard compares the mode, not the pair.** `WIREFRAME` after `NORMAL`
   re-issues the identical pair — the deliberate parity pin.
5. **End to end**, in `EngineRenderStepTest`: a frame whose layer queues several
   `NORMAL` decals records exactly one `blendFunc` for the whole frame (the
   `prepareDrawing` one).

## Risks and accepted exposures

- **A skipped call is invisible to the call-sequence oracle.** The guard's
  contract is only observable through a *negative* assertion (test 1) plus the
  frame-reset pin (test 3), so a wrongly skipped `blendFunc` cannot appear as a
  missing entry in an expected sequence. The real-GL smoke paths
  (`DecalSmokeTest`, `WebDecalSmokeTest`) draw one `NORMAL` decal per frame and
  therefore exercise the skip branch only; the transition branch rests on the
  recording tests. Accepted residual, not a defect.
- **Multi-context.** The web GL layer is already process-wide single-context
  (`glContext` is installed process-wide by the device, `GLContext.kt:15`), and
  the JVM default likewise resolves one `GLService`; a per-process mirror
  therefore introduces no sharing the GL layer does not already have. Shape (B)
  removes the question entirely.
- **No allocation, no new resource, no new public API.** The visibility ladder is
  respected: `private`/`internal` only.

## Benchmark relationship (out-of-round, recorded here)

The apparatus landed in its own round (`#32`). Its
`BatchGLCalls(dedupeBlend = true)` and the `RendererBlend` / `RendererDedupe` /
`RendererBatched` workloads measure exactly this lever through a GL-seam
decorator, and the blend part becomes redundant once the guard is in core. Note
the decorator guards on the **pair** while the guard this round adds compares the
**mode**, so it is strictly stronger; that difference is not why its numbers are
a lower bound — the lower bound comes from the decorator sitting *below*
`Renderer` — and neither may be quoted as this round's expected gain. Retiring
the blend lever is a follow-up on the `#32` apparatus, not part of this round.

## Framing for the micro-plan

Red → green per feature: (1) the repeated-mode/transition tests for `drawDecal`,
(2) the `prepareDrawing` re-arm test, (3) the frame-level end-to-end pin, then the
three existing expectations. Gate `./gradlew build` (or `tools/gradle build`)
green; the close runs the two-axis review (Standards + Spec, the Spec axis
carrying the three-sources check: olc parity, no unjustified regression against
`main`, Kotlin realization) because the commit touches `docs/decisions/` and the
review-gate plugin requires the marker. One commit for the round; the working
agent commits and does not push.

## Closed at the touch-point

1. **Shape (B)** — the mirror is owned by the resource scope, not held on the
   renderer. It satisfies the 2026-09-12 ruling instead of arguing against it,
   and costs no extra lookup.
2. The measurement apparatus is **committed and documented first**, as its own
   round (`#32`), before this one.
3. The `dedupeBlend` lever and the `RendererBlend` workload are **not** retired
   here; that is a follow-up on the `#32` apparatus, so the pre-change numbers
   stay reproducible alongside the guard.
