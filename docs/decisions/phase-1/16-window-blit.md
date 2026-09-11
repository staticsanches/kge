## 2026-09-10 — Window view + partial blit: touch-point decisions

Merged post-R2 session (roadmap item 3, extended by the owner to absorb item 2
`DrawPartialSprite` and to add a raw-backing optimization capability). Reason
for the merge: the window is the natural expression of a sub-rect read, so the
partial blit is defined through it, and both need the same optimization story.

### Verified facts (olc v2.30, main, current code)

- **olc `DrawPartialSprite` (v2.30:3398)** is a plain per-pixel loop reading
  `sprite->GetPixel(ox + i, oy + j)` over `w x h`, with flip applied inside the
  region; no fast path. `sprite->GetPixel` is lenient (NORMAL returns
  transparent OOB).
- **olc `SpritePatch` (v2.30:1124, 3433)** is **not** a sub-rect blit: it is
  `DrawSprite(pos, patch, scale)` → `FillTexturedPolygon(verts, uv, ...)`, a
  textured-polygon fill with UV coords. A separate, larger primitive; `main`'s
  `drawPartialSprite` (a sub-rect blit) is the real analogue of olc
  `DrawPartialSprite`.
- **`main`'s `drawPartialSprite`** takes `diagonalStart`/`diagonalEnd`,
  normalizes min/max, and indexes `sprite[x, y]` in the fallback — a `PixelMap`
  indexed read that throws on an out-of-range region (strict), unlike olc's
  lenient `GetPixel`.
- **Current `DrawSpriteService`** takes a concrete `Sprite` source; its raw fast
  path (`copyInts` whole rows) is guarded by `target is Sprite` and reaches
  `sprite.byteBuffer`/`target.byteBuffer`; the per-pixel path reads
  `sprite.uncheckedGet`. `Flip` is nested in `Sprite`. The `fillRect` raw-row
  path in `FillService` is likewise guarded by `target is Sprite`.
- The raster stack operates in the target's local `(0,0)..(width,height)` space;
  `ClipService` clips through the generic `Viewport` bounds. A window with a
  local `0..size` space therefore needs no change to either.
- `ByteBuffer` exposes only byte/int get/put and the `BufferService`
  `fillInts`/`copyInts` over byte offsets — enough to copy or fill a contiguous
  int span given a buffer, a row stride and a base index.

### Decisions (owner)

- **Window = origin as an offset.** A window has its own local space
  `(0,0)..(size)` and an `origin` into the source; access maps `local + origin`
  and delegates. `width`/`height` are the size. Supersedes the earlier "bounds as
  the contract / arbitrary lower bound" sketch: the flexibility lives in the
  `origin`, so `Pixmap`'s `(0,0)` lower bound stays and the raster stack and
  `ClipService` are untouched.
- **Specializations are nested; windows are anonymous (owner).** `MutablePixmap`
  and `RawBackedPixmap` become **`Pixmap.Mutable`** and **`Pixmap.RawBacked`** —
  symmetric nested specializations of `Pixmap` (the owner rejected the earlier
  asymmetry), and the raw-backing concept is folded in (below): there is no
  `RawBacking` type. `window` is declared on `Pixmap` and on `Pixmap.Mutable`
  (covariant) and returns an **anonymous** `Pixmap`/`Pixmap.Mutable` object
  (implementing `Pixmap.RawBacked` when the source is raw-backed) — no named
  window class, no cast anywhere. The writable view's `uncheckedSet` propagates
  to the source. The redundant `abstract` modifier on the interface members
  (`uncheckedGet`/`uncheckedSet`) is dropped.
- **The view owns its read policy.** `sampleMode` seeded from the source,
  independently mutable, no write-back. Because the window is anonymous, the
  mutability cannot surface through the read-only `Pixmap`; `Pixmap.Mutable`
  redeclares `sampleMode` as a `var`, so a writable window's mode is settable
  through the `Pixmap.Mutable` type (the read-only `Pixmap` keeps `val`). `Flip`
  likewise is a read policy.
- **Strict validation.** The region must be non-empty and inside the source,
  else `IllegalArgumentException`. Matches `main`'s indexed read; olc's
  `GetPixel` leniency is incidental, not a semantic to preserve.
- **Source type widened to `Pixmap`, names follow the type (owner).** The blit
  source becomes `Pixmap` (a window is a `Pixmap`). To reflect the type the
  service and methods are renamed: `DrawSpriteService` → **`BlitService`**,
  `drawSprite` → **`blit`**, `drawPartialSprite` → **`blitRegion`**. `Flip`
  migrates from `Sprite` to **`Pixmap`** (a source read policy, beside
  `SampleMode`). `blit`/`blitRegion` stay a fifth raster sub-service in the
  `Rasterizer` aggregate.
- **Raw-backing capability — `Pixmap.RawBacked` (owner).** Keying the raw paths
  on `is Sprite` makes a window (or a window of a window) fall to per-pixel, a
  performance cliff with no reason. The capability separates *where the pixels
  are* from *the view offset*: a `@KGESensitiveAPI` nested
  **`Pixmap.RawBacked : Pixmap`** exposes the raw addressing directly —
  `buffer: ByteBuffer` (a getter resolving the owning `ResourceWrapper`, so the
  release fail-fast after `close()` is preserved), `stride: Int` (elements per
  row), `baseIndex: Int` (the element index of the view's local `(0,0)`), and
  `index(x, y) = baseIndex + y * stride + x`:
  - `Sprite` exposes `buffer = storage.resource`, `stride = width`,
    `baseIndex = 0`;
  - a window over a `RawBacked` source implements `RawBacked` with the source's
    `buffer`/`stride` and `baseIndex = source.baseIndex + origin.y *
    source.stride + origin.x`, so a window of a window composes and still
    reaches the root; a non-contiguous source yields a plain `Pixmap` window
    (not `RawBacked`).
  **No `RawBacking` type, no optional accessor, no cast (owner):** intermediate
  cuts had a `RawBacking` value type and a `RawBackedPixmap` marker either with
  a nullable `rawBacking` (self-contradictory) or accessed via `as?` at every
  call site. The three accessors now live on `Pixmap.RawBacked` itself and the
  raster branches with `source is Pixmap.RawBacked && target is Pixmap.RawBacked`
  (smart cast, no `as?`). The backing reads are O(1) per operation (a window
  computes `baseIndex` once at construction), not per pixel. The blit core and
  the `fillRect` raw-row path key on the `is` checks, so a window source or
  target keeps the raw copy/fill whenever the mode/scale/flip allow it.
- **`blitRegion` shares the `blit` core; it is not a separate loop.** A private
  `blitCore(target, x, y, source, sx, sy, w, h, scale, flip, mode)` owns the
  algorithm; `blit` calls it with the full region (`0, 0, source.width,
  source.height`), `blitRegion` validates the region is inside the source and
  calls it with `(origin, size)`. One source of truth, no per-call window
  allocation. `blit(source.window(origin, size))` is cell-identical to
  `blitRegion` (pinned by a test).
- **The raw path collapses to one bulk copy when the block is contiguous
  (owner).** The per-row raw copy is a `copyInts` per row; when the rectangle
  fills whole rows in both buffers — `!flipV && sx == 0 && x == 0 && w ==
  source.stride && w == target.stride` — the block is one contiguous int run on
  each side, so a single `copyInts(w * h)` covers it. `stride == width` alone is
  not enough: a non-zero `x`/`sx` or a `w` below the stride leaves gaps between
  rows, and those cases keep the per-row copy. For a full `blit` of equal-width
  surfaces `x` is forced to `0`, so the common whole-sprite move takes the
  single-copy path. The two paths are pinned by counting `copyInts` through a
  `BufferService` override (`1` vs `h`).
- **Self-overlap (source and target sharing one buffer with overlapping
  regions) is undefined — reference-aligned, carried debt.** The per-row raw
  copy is safe per call (`copyInts` is memmove-safe) but not across rows: an
  in-place blit whose regions overlap (e.g. a vertical flip onto itself, or a
  shift within one surface) can read a row already overwritten and corrupt it.
  olc `DrawPartialSprite` (and the pre-session C6 raw path) corrupt the same
  way, so this is not a regression and not a semantic the reference defines;
  the contiguous single-copy path is the only overlap-safe one. Left as an
  accepted limitation (a later scrolling/`Scroll` operation is the real
  consumer; no scope creep here).
- **`Sprite.byteBuffer` retired; `Pixmap.RawBacked.buffer` is the raw-storage
  door (owner).** The C6 widening (log #35: `byteBuffer` public +
  `@KGESensitiveAPI`) existed for "the raster fast paths and the future GPU
  upload". The raster fast paths now read `Pixmap.RawBacked`; `buffer` returns
  the same buffer with the same release fail-fast. Keeping both would be two
  doors to one buffer, so `byteBuffer` is removed and its consumers
  (`PngServiceJvm`/`PngServiceWeb` encode, `SpriteService.duplicate`, the
  byte-level tests) use `sprite.buffer`. Touches the closed S5 codecs — recorded
  here (as the C6 widening recorded touching C5).
- **`SpritePatch` is separated** from this session: it is `FillTexturedPolygon`
  (a textured-polygon primitive), not a sub-rect blit. It gets its own
  touch-point later (the roadmap's item-2 scope is corrected accordingly).
  `DecalPatch` remains `R3`.

### Follow-up

Micro-plan next: `docs/plans/2026-09-10-window-blit-microplan.md`, TDD per step,
then the usual gate/review. Real consumers of a window remain the renderer/layer
(`C9`); this session ships the contract, the `Pixmap.RawBacked` optimization
capability, the type-driven rename, and pins it all with tests.

## 2026-09-10 — window + partial blit: closed

Micro-plan delivered; the shape went through several owner-driven iterations
during the session (below). Final shape:

- **`Pixmap` owns the family:** top-level `Pixmap` + nested `Pixmap.Mutable` and
  `@KGESensitiveAPI Pixmap.RawBacked` (nested so a use site needs a single
  `Pixmap` import; the owner rejected keeping `Mutable` top-level while nesting
  `RawBacked`). No `RawBacking` type — its three accessors are folded into
  `RawBacked` (`buffer` getter, `stride` in int elements, `baseIndex` of the
  local `(0,0)`, plus `index(x,y)`). The redundant `abstract` on the interface
  members is gone.
- **`window` returns anonymous views.** `Pixmap.window`/`Pixmap.Mutable.window`
  build an `object : Pixmap` (or `: Mutable`, and `: RawBacked` when the source
  is raw-backed). They must be **explicit**, not `by source`: `by` also
  delegates the default-bodied members (`get`/`sample`/`sampleBL`/`iterator`/
  `window`/`index`), so the delegated view would ignore the overridden
  `width`/`height`/`baseIndex` and lose its origin on a nested window — proven
  with a discriminating probe and pinned by read-only-branch tests.
- **`Pixmap.RawBacked` drives the raw paths** (`blit`/`blitRegion`,
  `fillRect`), keyed on `is`, no `as?`/`!`; a window composes `baseIndex` once.
  `blit` collapses to a single `copyInts(w*h)` when the block is contiguous in
  both buffers (else per row).
- **`Sprite.byteBuffer` retired** in favour of `RawBacked.buffer` (touches S5).
- **`blitRegion` shares `blitCore`**; `blit(source.window(...)) ≡ blitRegion`.
- **`SpritePatch` split out** (a textured-polygon primitive, not a sub-rect
  blit); `DecalPatch` stays `R3`.
- **Gate:** `./gradlew build --rerun-tasks` green (all targets + ktlint +
  metadata/assemble).
