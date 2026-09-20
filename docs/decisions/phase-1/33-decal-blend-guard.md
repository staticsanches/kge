## 2026-09-20 — Decal blend-mode guard: the lost olc/`main` parity guard

`DefaultRenderer.drawDecal` issued `glBlendFunc` for **every** decal instance
(3828/frame in the text benchmark). Both references guard it on a mode change, and
no rationale for dropping it was recorded, so the divergence from olc (`:6191`
`SetDecalMode`, `:6172` `PrepareDrawing`) and the regression against `main`
(`BaseRenderer.decalMode`) were findings. Design material:
`docs/plans/2026-09-20-decal-blend-guard-touchpoint.md` (decisions) and
`...-microplan.md` (TDD steps). Round `#33`, independent of `R6` round C.

### Decision — parity, not speed

The lever measures **inside the ±5% run band on both platforms** (JVM −2.6%, web
−0.6%; `#32` §3.3). This round is therefore a parity correction; the numbers are
recorded so it is not later re-litigated as an optimization. The dedupe that does
pay (`disable(GL_CULL_FACE)` + `bindTexture`, −23.6% / −14.7%) needs the frame
boundary and stays with `#3`.

### Decision — where the mirror lives (touch-point shape B)

The guard needs one remembered value, so the only question is where. It lives in
the **per-context bundle the renderer already resolves per draw** — `BuiltInQuad`,
which the engine-owned `ResourceScope` closes — as
`var decalMode: Decal.Mode = Decal.Mode.NORMAL`. Rejected:

- **A field on `DefaultRenderer`.** Exact reference parity (olc's renderer field,
  `main`'s property), but it contradicts the 2026-09-12 ruling that a fixed engine
  service must not carry mutable state. The scope *is* the per-context ownership
  boundary that ruling established, so shape B satisfies it instead of arguing
  against it.
- **A sibling scope holder.** Correct but costs a map lookup per decal, on the
  3828-decal frame the guard exists for.

### Mechanism (olc's, verbatim)

- `prepareDrawing` keeps its unconditional `enable(BLEND)` +
  `blendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA)` (olc `:6171`/`:6173`) and then
  re-arms the mirror to `NORMAL` (olc `:6172`) — no GL call, so the frame's blend
  state and its mirror are re-established together before any decal, which is also
  what makes the mirror unable to go stale across a context recreation.
- `drawDecal` keeps `disable(CULL_FACE)` → blend → `applyTexture()` (olc
  `:6230`/`:6231`/`:6235`) and guards **only** the blend, comparing `Decal.Mode`.
  Comparing the **mode** rather than the blend pair is required: `NORMAL`↔
  `WIREFRAME` hold the same pair and both references re-issue it.
- `drawLayerQuad` neither calls `blendFunc` nor touches the mirror, so a layer
  quad still inherits the previous decal's blend — as olc's `DrawLayerQuad`
  (`:6209`) does by not calling `SetDecalMode`.
- **Accepted exposure:** the renderer owns the blend state from one
  `prepareDrawing` to the next; a third-party `GL.blendFunc` inside a frame is out
  of contract, the same exposure olc has (its `nDecalMode` is likewise not
  invalidated by direct GL use).
- **Deliberate non-changes:** no `disable`/`bindTexture` dedupe (`#3`);
  `Decal.Mode.toGLBlend()` keeps returning its `Pair` on the emission path (0–1
  per frame against 3828 decals — inlining it in `GLMappings` buys an
  unmeasurable gain); no `Renderer.setDecalMode`, no new public API; the engine's
  `decalMode` and its per-frame reset (olc `:4881`) already matched and are
  untouched.

### Tests

The recording oracle's expectation moves from "a call is present" to "a call
happens on a transition". New: a repeated mode issues **no** `blendFunc` (red:
2); `prepareDrawing` re-arms so a frame ending on `ADDITIVE` followed by a `NORMAL`
decal issues exactly one (red: 2); a mode change issues the olc pair exactly once;
`WIREFRAME` after `NORMAL` re-issues the identical pair (the mode-vs-pair pin);
and, frame-level, several same-mode decals share the frame's single `blendFunc`
(red: 4). Updated: the mode→pair sweep became a transition chain (it could not
assert `NORMAL`'s pair before), and the two sequences whose first instance is
`NORMAL` dropped their leading `blendFunc`. `DecalIntegrationTest` and
`RendererTest` are unchanged by design. The real-GL smoke tests draw one `NORMAL`
decal per frame, so they exercise the **skip** branch only; the mode-transition
branch rests on the recording tests, and a wrongly skipped call is invisible to
the recorder — accepted, because the guard's negatives are pinned and
`prepareDrawing` re-arms every frame.

### Out of scope / carry-forward

- Retiring the `dedupeBlend` lever and the `renderer-blend` cell from the `#32`
  apparatus (its blend part is now core behavior), as a follow-up on that
  apparatus — the `#32` numbers stay recorded as measured pre-core.
- `#3` coalesced submission, if adopted, takes up the `CULL_FACE`/`bindTexture`
  dedupe in this same reset-in-`prepareDrawing` shape rather than as a general
  cache.
