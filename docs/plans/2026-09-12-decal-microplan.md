# Decal micro-plan (interactive execution)

**Date:** 2026-09-12. Touch-point decisions in
`docs/plans/2026-09-12-decal-touchpoint.md`. Concept: `Decal` (the GPU texture
resource), `DecalPatch`, `DecalInstance`, the `Decal.Mode`/`Structure`/`Filter`/
`Wrap` types and the stateless common draw services producing instances. Third of
the three concepts (after the GL layer and the renderer).

## Working mode

- **No subagents.** One step = test (red) → implement (green on jvm+js+wasmJs) →
  mark. Doubts resolved in-session with the owner.
- **Oracle = olc v2.30 + `main`.** The geometry (clip-space, quantization, UV
  epsilon, per-vertex tint) and the blend/primitive mapping follow olc; the type
  shapes follow `main` where it does not diverge. The recording `GLService`/
  `Renderer` is the test oracle.
- **Stateless.** No global state and no defaults on `mode`/`structure`; the draw
  services take the viewport size explicitly (the engine supplies it later).

## Contract (touch-point)

- **`Decal`** = T1 resource: created from a `Sprite` (RGBA) with the upload in the
  constructor; does **not** own the `Sprite` (keeps a reference for `update()`
  CPU→GPU and `updateSprite()` GPU→CPU); owns the `Texture`; non-null.
- **`DecalInstance`**: non-null `decal`, per-vertex `pos`/`uv`/`tint`, `mode`,
  `structure`; vertex count derived from the lists; no `w`/depth. The contract
  between the draw services and the renderer.
- **Nested types:** `Decal.Mode { NORMAL, ADDITIVE, MULTIPLICATIVE, STENCIL,
  ILLUMINATE, WIREFRAME }`, `Decal.Structure { LINE, FAN, STRIP, LIST }`,
  `Decal.Filter { NEAREST, LINEAR }`, `Decal.Wrap { CLAMP_TO_EDGE, REPEAT }` — all
  closed to the oracle's scope, required, no defaults.
- **Draw services** (`KGEOverridable`, portable defaults producing
  `DecalInstance`s): `drawDecal(pos, decal, scale, tint, mode, structure,
  viewport)`, `drawPartialDecal(pos, decal, sourcePos, sourceSize, scale, tint,
  mode, structure, viewport)`, `drawPolygonDecal(decal, verts, uvs, tints, mode,
  structure, viewport)`; plus `DecalPatch` + `drawDecal(pos, patch, scale, mode,
  structure, viewport)`.
- **3D note:** untextured geometry is out of scope here (belongs to the future
  3D task type); the 2D `DecalInstance` stays non-null and no blank decal is
  ported.

## Resume tracker

- [x] **0.** `Decal.Mode`/`Structure`/`Filter`/`Wrap` + `DecalInstance`
- [x] **1.** `Decal` resource (create/update/readback/close)
- [x] **2.** Draw services: `drawDecal`, `drawPartialDecal`, `drawPolygonDecal`
- [x] **3.** `DecalPatch` + `drawDecal(pos, patch, scale)`
- [x] **4.** Integration: the renderer draws instances (recording + real smoke)
- [x] **5.** Gate + review + decisions-log close

## Steps

### 0. Types + `DecalInstance`

- **Tests:** the enum sets are complete and closed; a `DecalInstance` built from
  vertex lists exposes the derived count; fields are immutable.
- **Files:** `commonMain/.../renderer/decal/Decal.kt` (enums),
  `.../renderer/decal/DecalInstance.kt`.
- **Decided:** nested enums closed to the oracle's scope; non-null decal; no
  `points`/`w`/depth.

### 1. `Decal` resource

- **Tests:** `Decal(sprite, filter, wrap)` records `createTexture` + the initial
  upload; `update()` re-uploads from the referenced `Sprite`; `updateSprite()`
  reads back; `close()` deletes the texture once (idempotent), use-after-close
  fails fast; a failure between create and construct frees the texture
  (`letClosingIfFailed`); the `Sprite` is not closed with the `Decal`.
- **Files:** `commonMain/.../renderer/decal/Decal.kt`.
- **Decided:** creation goes through the overridable `Renderer` (no
  `DecalService`); no `Renderable` yet.

### 2. Draw services

- **Compare:** olc `DrawDecal`/`DrawPartialDecal` (`DecalInstance` construction,
  screen-space conversion, per-vertex tint) and `main`'s
  `DrawDecalService`/`DrawPartialDecalService`.
- **Tests:** `drawDecal` builds 4 vertices with the exact clip-space math and
  uniform tint; `drawPartialDecal` applies the UV sub-rect with the `0.0001`
  epsilon and the pixel quantization; `drawPolygonDecal` builds N vertices with
  per-vertex UV/tint; `mode`/`structure` are carried into the instance; the
  services are overridable (extension-contract test).
- **Files:** `commonMain/.../renderer/decal/service/`.
- **Decided:** geometry is common and stateless; the viewport size is a
  parameter; `mode`/`structure` are required.

### 3. `DecalPatch`

- **Tests:** `Decal.patch(pos, size)` and `Decal.patch(bl, tl, tr, br)` build the
  4 coords; `drawDecal(pos, patch, scale, mode, structure, viewport)` builds the
  polygon instance; parity with olc `Patch`/`DrawDecal(patch)`.
- **Files:** `.../renderer/decal/DecalPatch.kt` + the draw form.
- **Decided:** `DecalPatch` = `decal` + 4 coords.

### 4. Integration

- **Tests:** an end-to-end recording test — `Decal` from a `Sprite`, `drawDecal`,
  then `Renderer.drawDecal(instance)` records the expected GL sequence
  (create/update texture, bind, blend, draw). Real-GL smoke renders a decal to an
  FBO and reads a pixel back (JVM hidden GLFW / web WebGL2; macOS skips JVM).
- **Decided:** no batching this stage.

### 5. Gate + review + close

- **Gate:** `./gradlew build --rerun-tasks` green. Leak audit of every allocate/
  close path and construction-failure branch; no-parameter-without-observable
  effect audit. Two-axis review; decisions-log close entry.

## Files

New: `renderer/decal/Decal.kt`, `.../DecalInstance.kt`, `.../DecalPatch.kt`,
`.../service/` draw services; `commonTest` geometry/lifecycle tests;
`jvmTest`/`webTest` integration smoke.

Out of scope: the `multiDrawArrays` optimization (future, benchmark-gated,
possibly an alternative default `Renderer`), 3D/untextured geometry (own type),
`Renderable`, rotation/warp outside the polygon/patch, `SpritePatch`
(`FillTexturedPolygon`, its own concept).
