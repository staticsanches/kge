# Decal vertex storage micro-plan (C9 correction, interactive execution)

**Date:** 2026-09-13. Pre-micro-plan material:
`docs/plans/2026-09-13-decal-vertices-proposal.md`. Targeted correction of the
closed `C9` decal geometry (decisions log `22-renderer-gl-decal.md`), not a
concept re-open. Runs in the same round as the tree's pending post-C9
adjustments and the `C10` plans; one commit.

## Problem

`DecalInstance` stores its vertex geometry as parallel `List<Float2D>`/
`List<Pixel>`. A `Pixel` in a generic position is boxed — the same
value-class non-optimized path removed from `Pixmap` on 2026-09-13 — every
vertex materializes two `Float2D` objects, and the `List`-typed API cannot
state immutability. `main`'s `DecalInstance` instead held a `VerticesInfo`
pull contract with no per-vertex collections: a regression against a `main`
Kotlin-level solution.

## Contract (decided)

- **`VerticesInfo`** (public, `renderer/decal`): `vertexCount` plus per-index
  pulls `x`/`y`/`u`/`v`/`tint`. `DecalInstance(decal, mode, structure,
  vertices)` holds it and delegates `vertexCount`; the parallel lists and the
  size validation go (`main` had none). No `z`/`w` (2D instance, log #22) and
  no `putAll(buffer)` (couples geometry to the byte layout; the renderer owns
  the layout).
- **`DecalPatch(decal, bl, tl, tr, br)`**: four named coordinates instead of
  `coords: List<Float2D>`; both `Decal.patch` factories stay.
- **Implementations are anonymous, behind factory functions.**
  `internal fun VerticesInfo.Companion.quad(posX, posY, dimX, dimY, u0, v0,
  u1, v1, tint)` (a `companion object` anchor — `internal` is not permitted on
  an interface member) returns an anonymous
  primitive-backed implementation in olc order TL, BL, BR, TR; `(u0, v0)` the
  TL texture coordinate, `(u1, v1)` the BR one; uniform tint. No named private
  class: it is instantiated only by the factory, so a concrete type buys
  nothing. The polygon path uses the same shape — a private factory in
  `DrawPolygonDecalService.kt` returning an anonymous array-backed
  implementation: one interleaved `FloatArray` (`x, y, u, v` per vertex) plus
  an `IntArray` of `nativeRGBA` tints, copied at construction (immutable by
  encapsulation); the list boundary keeps the previous `DecalInstance`
  fail-fast on non-parallel `pos`/`uv`/`tint` sizes
  (`IllegalArgumentException`).
- **`DefaultRenderer.drawDecal`** pulls one vertex at a time and writes the
  exact same bytes in the same order (`z = 1`, `w = 0`, then u, v, tint
  `nativeRGBA`).
- **Accepted:** the polygon service boundary keeps `List<Float2D>`/
  `List<Pixel>` (an array overload can follow — proposal).

## Behavior (must not change)

Same clip-space math, vertex order and bytes as the shipped C9 draw services.
The recording expectations move only to the new construction/pull API; any
value change is a defect.

## Steps (TDD: new test red → implement → green, per step)

0. **Baseline:** `./gradlew :kge-core:jvmTest --rerun-tasks` green before any
   edit (report and stop otherwise).
1. **`VerticesInfo` + `VerticesInfo.quad`** + `VerticesInfoTest`: `vertexCount == 4`;
   pulls TL/BL/BR/TR; UV mapping (`u0`/`v0` at TL, `u1`/`v1` at BR); uniform
   tint.
2. **`DecalInstance`** + `DecalInstanceTest`: exposes `decal`/`mode`/
   `structure`; `vertexCount` derives from the given `VerticesInfo`; pulls pass
   through.
3. **`DecalPatch` + `Decal.patch`** + `DecalPatchTest`: named `bl`/`tl`/`tr`/
   `br`, both factories.
4. **Draw services** + tests:
   - `DrawDecalServiceTest`/`DrawPartialDecalServiceTest`: assertions read the
     instance's pull contract (`assertVerticesCloseTo` for positions and UVs,
     `assertTints` for tints); override tests build the empty instance through
     the fixture.
   - `DrawPolygonDecalServiceTest`: N-vertex pulls with per-vertex tints;
     mutate the caller's lists after the call and pin that the instance did not
     change; non-parallel list sizes are rejected (`IllegalArgumentException`).
5. **`DefaultRenderer.drawDecal`** + `RendererDrawTest`/`RendererSurfaceTest`:
   the recorded vertex bytes are unchanged; test instances come from the
   fixture.
6. **Gate:** `./gradlew build --rerun-tasks` green (ktlint via `check`); then
   the decisions-log correction and the two-axis review.

## Test fixtures (`DecalDrawTestFixtures.kt`)

- `assertVerticesCloseTo(vertices: VerticesInfo, expected: List<Float2D>)`
  (x/y), `assertUvsCloseTo` (u/v), `assertTints` (`Pixel` equality).
- `emptyVertices()`: the zero-vertex double for the override tests.
- A list-backed `VerticesInfo` double for the renderer tests (arbitrary
  vertex counts).

## Files

Changed: `renderer/decal/{DecalInstance,DecalPatch,Decal}.kt`,
`renderer/decal/service/{DrawDecal,DrawPartialDecal,DrawPolygonDecal}Service.kt`,
`renderer/internal/DefaultRenderer.kt`, the four decal test files,
`RendererDrawTest.kt`, `RendererSurfaceTest.kt`. New:
`renderer/decal/VerticesInfo.kt`, `commonTest/.../decal/VerticesInfoTest.kt`
(the fixture file already exists).

## Out of scope

The polygon array boundary overload; renderer batching (`multiDrawArrays`,
carry-forward #22); every other decal/GL behavior.
