# Decal vertex storage — proposal

**Status:** closed by `docs/plans/2026-09-13-decal-vertices-microplan.md`
(2026-09-13).

**Date:** 2026-09-13. Pre-micro-plan material for a targeted correction of the
`C9` decal geometry (concept closed 2026-09-12, decisions log #22). Driver: the
current `DecalInstance` stores the vertex geometry as `List<Float2D>`/
`List<Pixel>`; this (a) boxes every `Pixel` (a value class used as a generic
type argument — the same non-optimized path removed from `Pixmap` on
2026-09-13), (b) exposes a mutable `ArrayList` behind a `List` while KDoc
claims immutability, and (c) regresses against `main`, whose `DecalInstance`
used indexed pull access (`VerticesInfo`) with no per-vertex collection.

## Proposed shape

**`VerticesInfo` (public pull contract, `renderer/decal`).** The renderer reads
one vertex at a time and owns the byte layout (`z = 1`, `w = 0` today).

```kotlin
interface VerticesInfo {
    val vertexCount: Int
    fun x(index: Int): Float
    fun y(index: Int): Float
    fun u(index: Int): Float
    fun v(index: Int): Float
    fun tint(index: Int): Pixel
}
```

**`DecalInstance`.** Holds the geometry, not lists; no size validation needed.

```kotlin
class DecalInstance(
    val decal: Decal,
    val mode: Decal.Mode,
    val structure: Decal.Structure,
    val vertices: VerticesInfo,
) {
    val vertexCount: Int get() = vertices.vertexCount
}
```

**Concrete implementations: `private` types behind `internal` factories.** The
concrete types are never module-visible; only the factory crosses files.

```kotlin
// VerticesInfo.kt
private class QuadVerticesInfo(...) : VerticesInfo { ... }      // computes TL, BL, BR, TR
private class PolygonVerticesInfo(...) : VerticesInfo { ... }   // array-backed, tints as Int

// factories shared across the service files
internal fun quadVertices(posX: Float, posY: Float, dimX: Float, dimY: Float,
                          u0: Float, v0: Float, u1: Float, v1: Float, tint: Pixel): VerticesInfo =
    QuadVerticesInfo(...)
internal fun polygonVertices(xs: FloatArray, ys: FloatArray, us: FloatArray,
                             vs: FloatArray, tints: IntArray): VerticesInfo =
    PolygonVerticesInfo(...)
```

- `QuadVerticesInfo` is shared by `DrawDecalServiceDefault` and
  `DrawPartialDecalServiceDefault`, so its factory is `internal`; the type stays
  `private`. `PolygonVerticesInfo` is used by `DrawPolygonDecalServiceDefault`
  alone and could be `private` in that file instead.
- Arrays inside `PolygonVerticesInfo` are private; the value is immutable by
  encapsulation. Tints are stored as `Int` (`nativeRGBA`) and returned as
  `Pixel` (unboxed — value class in return position).

**`DecalPatch`.** Four named coordinates instead of a `List`.

```kotlin
class DecalPatch @KGESensitiveAPI constructor(
    val decal: Decal,
    val bl: Float2D, val tl: Float2D, val tr: Float2D, val br: Float2D,
)
```

**Renderer.** Same bytes as today, so the recording expectations do not move.

```kotlin
val vertices = instance.vertices
val n = vertices.vertexCount
val data = quad.staging.ensureCapacity(VertexLayout.BYTES * n)
for (i in 0 until n) {
    data.resource.putVertex(
        i * VertexLayout.BYTES,
        vertices.x(i), vertices.y(i), 1f, 0f, vertices.u(i), vertices.v(i), vertices.tint(i),
    )
}
quad.staging.upload(VertexLayout.BYTES * n)
```

## Effect

| | objects / draw | boxes `Pixel` | immutable |
|---|---|---|---|
| current quad | `DecalInstance` + 3 `ArrayList` + 8 `Float2D` + 4 `Pixel` | yes | claimed, not enforced |
| proposed quad | `DecalInstance` + `QuadVerticesInfo` | no | by construction |
| current polygon | lists + per-vertex objects | yes | claimed, not enforced |
| proposed polygon | `DecalInstance` + `PolygonVerticesInfo` + arrays | no | by encapsulation |

## Impact / open items

- Closed `C9` API changes: `DecalInstance` constructor and `DecalPatch.coords`
  removal; tests/fixtures building with `List` migrate. A targeted fix round,
  not a concept re-open.
- `VerticesInfo` is public (custom renderers/services must build and read it);
  implementations are private. Verified on `main` that a pull interface is the
  established shape.
- Per-vertex interface dispatch is accepted; `main`'s `putAll(buffer)` was
  rejected because it couples geometry to the byte layout. An internal fast
  path is possible later without widening the API.
- The polygon service still takes `List` at its public boundary; a
  `FloatArray`/`IntArray` overload can follow if the boundary box matters.
