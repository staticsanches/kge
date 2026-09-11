# Window view + partial blit micro-plan

**Date:** 2026-09-10. Touch-point decisions in
`docs/decisions/phase-1/16-window-blit.md`. Merged concept: a `Pixmap` **window**
(local `0..size` space + `origin` offset, read-only/writable), a **raw-backing
capability** so the fast paths optimize through views (not by concrete type),
and the **blit** family widened to a `Pixmap` source with the type-driven rename
(`BlitService`/`blit`/`blitRegion`, `Flip` moved to `Pixmap`).

Executed step-by-step: compare → decide → test (red) → implement (green on
jvm+js+wasmJs) → mark. Per-step expectations are hypotheses; a contradicting
finding overrides them and is recorded in the step's `Decided:` field.

## Decisions (owner, 2026-09-10 — see the touch-point chunk)

> **Naming superseded (owner, design B — final).** The raw-backing and
> mutability shapes below went through iterations during the session; the final
> form is: `MutablePixmap`→**`Pixmap.Mutable`** and `RawBackedPixmap`→
> **`Pixmap.RawBacked`** (both **nested**), the `RawBacking` value type **folded
> into `Pixmap.RawBacked`** as `buffer`/`stride`/`baseIndex` + `index(x,y)`, and
> the redundant `abstract` on the interface members dropped. The authoritative
> record is the touch-point chunk `16-window-blit.md`; the earlier `RawBacking`/
> `rawBacking` wording kept in the Steps below is the historical iteration.

- **Origin as offset**, local space `0..size`; `uncheckedGet(x,y) =
  source.uncheckedGet(x + origin.x, y + origin.y)`; writable view propagates
  `uncheckedSet`.
- **Covariant factories** `Pixmap.window` / `Pixmap.Mutable.window`;
  `sampleMode` seeded from the source, independently mutable.
- **Strict validation**: non-empty and inside the source, else throw.
- **`Pixmap.RawBacked` capability** (`@KGESensitiveAPI`): `buffer` (getter),
  `stride`, `baseIndex`, `index(x,y)`; `Sprite` exposes its own, a window over a
  `RawBacked` source forwards and composes the offset (nested windows reach the
  root) as a `RawBacked` view; a non-contiguous source yields a plain `Pixmap`
  window. Fast paths branch on `is Pixmap.RawBacked`, never on `is Sprite`, with
  no `as?`.
- **Blit source widened to `Pixmap`** and renamed: `DrawSpriteService`→
  `BlitService`, `drawSprite`→`blit`, `drawPartialSprite`→`blitRegion`;
  `Flip` moves `Sprite`→`Pixmap`.
- **`blitRegion` shares the `blit` core** (private `blitCore`); `blit(window) ≡
  blitRegion` pinned by a test.
- **`SpritePatch`** (a textured-polygon primitive) is out of scope.
- **No changes to `ClipService`/`Viewport`/`Sprite` storage.**

## Contract (decided; shapes may tighten per step)

`RawBacking` (new, package `image`, `@KGESensitiveAPI`):

`Pixmap` (package `image`) — additions (nested specializations; no `RawBacking`
type):

```kotlin
enum class Flip { NONE, HORIZONTAL, VERTICAL, BOTH }   // moved from Sprite
fun window(origin: Int2D, size: Int2D): Pixmap   // anonymous Pixmap view

interface Mutable : Pixmap {
    override var sampleMode: Pixmap.SampleMode          // writable read policy
    override fun window(origin: Int2D, size: Int2D): Mutable   // anonymous
    fun uncheckedSet(...)                                // no `abstract`
}

@KGESensitiveAPI
interface RawBacked : Pixmap {
    val buffer: ByteBuffer                               // getter; fail-fast
    val stride: Int
    val baseIndex: Int
    fun index(x: Int, y: Int): Int = baseIndex + y * stride + x
}
```

`Sprite` (production, package `image`) — `: Pixmap.Mutable, Pixmap.RawBacked`,
with `buffer = storage.resource` (the ctor param is renamed `storage`),
`stride = width`, `baseIndex = 0`; `uncheckedGet`/`uncheckedSet` use
`index(x, y) * Int.SIZE_BYTES`.

The windows are **anonymous objects** returned by `window` — no `PixmapWindow`/
`MutablePixmapWindow` type. Each captures its source, seeds `sampleMode` from the
source, delegates `uncheckedGet` (`uncheckedSet` in the writable one) to the
source at `local + origin`, and — when the source is `Pixmap.RawBacked` —
implements `RawBacked` with the source's `buffer`/`stride` and
`baseIndex = source.baseIndex + origin.y * source.stride + origin.x` (computed
once). A shared `requireValidWindow(source, origin, size)` validates non-empty
and inside-source.

`BlitService` (package `rasterizer.service`, renamed file `BlitService.kt`),
fifth raster sub-service — abstract + companion-forwarded, `Int2D` position
overloads as interface defaults:

```kotlin
fun blit(target: Pixmap.Mutable, x, y, source: Pixmap, scale, flip: Pixmap.Flip, mode)
fun blitRegion(target: Pixmap.Mutable, x, y, source: Pixmap, origin: Int2D, size: Int2D, scale, flip, mode)
```

Default object, over a private
`blitCore(target, x, y, source, sx, sy, w, h, scale, flip, mode)`:

- `blit(...) = blitCore(..., 0, 0, source.width, source.height, ...)`;
- `blitRegion` validates the region inside the source (throw) and calls
  `blitCore(..., origin.x, origin.y, size.x, size.y, ...)`;
- `blitCore`: raw row copy when `source is Pixmap.RawBacked && target is
  Pixmap.RawBacked && mode == Normal && scale == 1 && !flipH && footprint fits`,
  using `index` for the row offsets (flipV swaps the source row); else the
  per-pixel loop reading `source.uncheckedGet(sx + i, sy + j)`.

`FillService.fillRect`: the raw-row path keys on `target is Pixmap.RawBacked &&
mode == Normal`, filling each row from `index(clipLeft, y)`.

## Files

- New (commonMain): none (the raw capability is nested in `Pixmap`).
- Deleted (commonMain): `image/RawBacking.kt`.
- Renamed (commonMain): `rasterizer/service/DrawSpriteService.kt` →
  `rasterizer/service/BlitService.kt`.
- Modified (commonMain): `image/Pixmap.kt` (`Flip` + nested `Mutable`/`RawBacked`
  + the anonymous `window`), `image/Sprite.kt` (remove `Flip`, implement
  `Pixmap.RawBacked`), `rasterizer/Rasterizer.kt`,
  `rasterizer/service/DrawService.kt` (KDoc), `rasterizer/service/FillService.kt`
  (raw path).
- New (commonTest): `image/PixmapRawBackedTest.kt`, `image/PixmapWindowTest.kt`,
  `image/BlitWindowTest.kt` (the first was `RawBackingTest.kt` before design B).
- Modified (commonTest): `image/RasterizerTest.kt`,
  `image/RasterizerPointOverloadTest.kt` (`drawSprite`→`blit`,
  `Sprite.Flip`→`Pixmap.Flip`, `MutablePixmap`→`Pixmap.Mutable`).

## Steps

### 0. `Pixmap.Flip` moves from `Sprite`

- [x] **Test (red):** the blit tests reference `Pixmap.Flip`; `Sprite.Flip`
  no longer resolves (compile red). Mechanically move the enum.
- [x] **Run:** fails (unresolved).
- [x] **Implement:** move `Flip` from `Sprite.kt` to `Pixmap.kt`; update the two
  test files' references.
- [x] **Run:** green on all targets.
- [x] **Decided:** the production `DrawSpriteService` still spells `Sprite.Flip`;
  moving the enum forces its references (`Sprite.Flip`→`Pixmap.Flip` + import) in
  step 0, before the step-3 rename. The enum KDoc moved verbatim.

### 1. `RawBacking` capability + `Sprite`

- [x] **Test (red):** `RawBackingTest`:
  - `RawBacking(buffer, stride, base).index(x, y)` is `base + y*stride + x`;
    `offsetBy(origin)` adds `origin.y*stride + origin.x` to `base` (and is
    identity for `(0,0)`);
  - a `Sprite.rawBacking` has `stride == width`, `base == 0`, and `index(x, y)`
    equals the element offset of `(x, y)` (offset in bytes / `Int.SIZE_BYTES`);
  - a non-contiguous `Pixmap` (the `PixmapDouble` double) has `rawBacking ==
    null` — no marker, no `as?`;
  - after `Sprite` `close()`, `sprite.rawBacking.buffer` throws (the resource
    fail-fast) — the backing is a cached `val` that resolves the buffer on
    demand; `rawBacking` identity is stable (`===`).
- [x] **Run:** fails (no `RawBacking`).
- [x] **Implement:** `RawBacking`; `Sprite` exposes `rawBacking`.
- [x] **Run:** green on all targets.
- [x] **Decided:** `Sprite` now has two supertypes providing `Pixmap.get`, so its
  override must spell `super<MutablePixmap>.get(...)`. `@OptIn(KGESensitiveAPI)`
  is required on overriders and on test readers of `rawBacking`.
  `PixmapDouble` (PixmapTest) became `internal` to share the non-raw double
  across the new test files.
  **Refinement (owner, before close):** the first cut had `RawBacking` capture a
  `ByteBuffer` and `rawBacking` as a getter, reallocating per access (per level
  for a nested window). `RawBacking` now holds the owning `ResourceWrapper` and
  exposes `buffer` as a getter that resolves it, so `rawBacking` is cached (one
  allocation per surface/window) while the release fail-fast stays (`buffer`
  throws); the raw-copy/fill loops hoist `raw.buffer` out of the row loop.
  Caching pinned by `===` identity tests. **Kotlin/Wasm finding (4-way matrix):
  a covariant override narrowing the interface's `RawBacking?` to a non-null
  `RawBacking` miscompiles — `call_ref` type mismatch at module instantiation
  (`BlitWindowTest$<init>`), the whole `wasmJsNodeTest` fails — regardless of
  `get()` vs `val`; the nullable type compiles in both forms. The determinant is
  the narrowed non-null return type alone. So the override keeps the exact
  `RawBacking?` type; a cached `val` field suffices.**

### 2. Window view + factories (+ `fillRect` raw path via `rawBacking`)

- [x] **Test (red):** `PixmapWindowTest` over a 5x5 patterned `Sprite`, window
  `origin=(1,1)`, `size=(3,3)`:
  - `width`/`height` = `3`; bounds `(0,0)`/`(3,3)`; `contains(0,0)` true,
    `contains(3,3)` false;
  - read-only: `get(0,0)` == source `get(1,1)`, `get(2,2)` == source `get(3,3)`;
    `get(3,0)` transparent though the source has pixels there;
  - writable: `set(1,1, RED)` writes source `(2,2)`; source outside unchanged;
    `set(0,3, ...)` false;
  - `clear` clears only `source(1..3, 1..3)`;
  - the sequence yields the 9 window pixels row-major, local order;
  - `sampleMode` seeded from the source, independently mutable (source
    unchanged);
  - validation throws: region past the edge, zero/negative size, negative origin;
  - the writable factory's static type is `MutablePixmap`;
  - **rawBacking**: a window over a `Sprite` has a backing with the root
    `stride`, `base` shifted by `origin`; a window-over-a-window composes to the
    root `base`; a window over a non-contiguous double has a `null` backing;
  - **fillRect**: `Rasterizer.fillRect(window, 0,0,2,2, RED)` paints source
    `(1,1)..(3,3)` and nothing outside (the raw path must not write past the
    window).
- [x] **Run:** fails (no `window`).
- [x] **Implement:** the anonymous `window` bodies (with cached `rawBacking`
  forwarding), the shared `requireValidWindow`, and the `MutablePixmap`
  `sampleMode` `var`; migrate `FillService.fillRect`'s raw-row path from
  `target is Sprite` to `target.rawBacking`.
- [x] **Run:** green on all targets.
- [x] **Decided (owner, before close):** the first cut used named
  `PixmapWindow`/`MutablePixmapWindow` classes and a `RawBackedPixmap` marker
  with `as?` at the call sites. The owner rejected both: the marker's nullable
  `rawBacking` is self-contradictory and the casts are unwanted. `rawBacking`
  moved onto `Pixmap` (default `null`) — no marker, no cast — and `window`
  returns anonymous `Pixmap`/`MutablePixmap` objects — no public window type,
  no `as PixmapWindow` in the test. So `MutablePixmap` had to redeclare
  `sampleMode` as a `var` (the anonymous window's `var` is otherwise invisible
  behind the `val` interface type); the read-only `Pixmap` keeps `val`. The
  `require` message cannot use `origin..size` (`Int2D` has no `rangeTo`).

### 3. `BlitService`/`blit`: `Pixmap` source, rename, `rawBacking` core

- [x] **Test (red):** rename the existing blit cases to `blit` and add:
  - a `blit` whose source is a non-`Sprite` `Pixmap` (the `PixmapDouble` double)
    paints the same cells as the equivalent `Sprite` source (widening);
  - a `blit` of a window source onto a `Sprite` target is raw (asserted by
    cell-equality with the per-pixel oracle) and skips the non-raw path when the
    mode is not Normal (a decorator/instrumented source can count `uncheckedGet`
    calls — raw path reads zero);
  - all existing cases (1:1, flips, scale, clip, Mask/Alpha, non-positive scale)
    pass under the new names (regression).
- [x] **Run:** fails (source is `Sprite`; names are `drawSprite`).
- [x] **Implement:** rename service/file/methods; widen `source` to `Pixmap`;
  extract `blitCore` keyed on `rawBacking`; update `Rasterizer`, `DrawService`
  KDoc, call sites.
- [x] **Run:** green on all targets.
- [x] **Decided:** the `blit`/`blitRegion` wide rewrite and the test rename are
  compile-coupled (the old names no longer resolve), so the value-red was
  re-proven by mutation: flipping the fast-path guard `Normal`→`Mask` fails
  `blit from a raw-backed source...` (`reads` becomes 4) and `blit honors
  Mask/Alpha...`; reverting restores green. `blitCore` is a private member of the
  default object; it reads `rawBacking` once per call. The companion forwards all
  four methods (both raw forms and both `Int2D` forms), matching
  `OutlineService`/`FillService`, so a decorator's typed override stays
  observable through the [Rasterizer] aggregate.
  **Refinement (owner, before close):** the per-row raw copy collapses to a
  single `copyInts(w * h)` when the block is contiguous in both buffers
  (`!flipV && sx == 0 && x == 0 && w == source.stride && w == target.stride`).
  `stride == width` alone is insufficient (a non-zero `x`/`sx` or `w < stride`
  keeps gaps); each such case stays per-row. The two paths are pinned by
  counting `copyInts` via a `BufferService` override (`1` for the whole-row
  case, `h` otherwise) in `BlitWindowTest`.

### 4. `blitRegion` + raster-through-window

- [x] **Test (red):** `BlitWindowTest`:
  - `blitRegion(target, x, y, source5x5, origin=(1,1), size=(2,2), scale=1,
    NONE, Normal)` paints `source(1,1)..(2,2)` at `(x,y)..(x+1,y+1)`
    (the olc `main` reference case);
  - an out-of-source `origin`/`size` throws (strict);
  - a non-`Sprite` source region is cell-identical to the per-pixel oracle;
  - a `blitRegion` Sprite→Sprite raw path is cell-identical to the per-pixel
    oracle and to the same region on a non-raw double (fast path must not
    diverge); flips apply inside the region; a scaled region paints
    `scale x scale` blocks; a region whose footprint runs past the target clips
    and paints nothing fully outside;
  - `blit(source.window(origin,size))` ≡ `blitRegion(...)` (same core);
  - raster through a window on an 8x8 `Sprite`, window `origin=(2,2)`,
    `size=(4,4)`: `fillRect(window,0,0,3,3)` paints source `(2,2)..(5,5)`;
    `drawLine(window,0,0,3,3)` paints the window diagonal; a `blit` past the
    window edge clips; the same primitives on a full `Sprite` match the existing
    suite (regression).
- [x] **Run:** fails (no `blitRegion`).
- [x] **Implement:** the `blitRegion` default + `Int2D` overloads (shared
  `blitCore`); no other production change expected.
- [x] **Run:** green on all targets.
- [x] **Decided:** region validation lives in the default `blitRegion` (strict
  non-empty/inside-source). Value-red re-proven by mutation: passing `0, 0`
  instead of `origin` fails the reference, scale and `blit(window)≡blitRegion`
  cases. A window target clips through the ordinary `DrawService.draw` bounds
  check, so a blit past the window edge never reaches the source outside it; the
  `fillRect` raw path through a window writes only `rowLength` ints per row.

### 5. Gate + close

- [ ] `./gradlew build --rerun-tasks` green (all suites + ktlint). API audit:
  `window` (both forms), `blit`/`blitRegion` (both forms), `Pixmap.rawBacking`/
  `RawBacking`, the moved `Pixmap.Flip` and the view accessors each have an
  observable effect pinned by a test; the raw path allocates nothing per pixel.
- [ ] Two-axis review (standards + spec), fixes, verify pass of the delta.
- [ ] Decisions-log entry (per-step `Decided:` + owner rulings) + roadmap
  update (post-R2 items 2/3: `blit`/`blitRegion` + window + `RawBacking` done,
  `SpritePatch` split out); touch-point chunk consumed.
- [ ] **Decided:** _

## Out of scope

`SpritePatch` (a `FillTexturedPolygon` textured-polygon primitive — own
touch-point) and `R3`'s `DecalPatch`; absolute-bounds windows (superseded);
a raw path through a window when the mode is flip/scale/Alpha/Custom (still
per-pixel, as for `Sprite`); a raster/`ClipService` rewrite (not needed in the
origin model); user-facing scissor/viewport on the raster API (still `C9`);
self-overlap (source and target sharing one buffer with overlapping regions) —
undefined in the reference (olc `DrawPartialSprite` corrupts identically) and
carried as accepted debt; a later scrolling operation is the real consumer;
changing `Sprite` storage/layout. `Sprite.byteBuffer` is retired in this session
(its raster justification moved to `RawBacking`; `rawBacking.buffer` is the raw
door) — a recorded widening over the closed S5 codecs.
