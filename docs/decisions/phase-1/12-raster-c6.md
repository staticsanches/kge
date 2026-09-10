## 2026-09-08 — C6 (raster ops, R1): touch-point decisions

The R1 macro requirement (roadmap "Render"): primitives over a surface; fast
bulk paths; pixel modes (Normal/Mask/Alpha/Custom) + blend resolution math —
the modes' only consumers are raster (log #24 moved them here from C4). Open
items decided at this touch-point:

- **Scope — the full R1 + the items deferred to C6 by earlier concepts.** The
  draw funnel (mode-resolving write) + the primitives (`fillRect`, `drawRect`,
  `drawLine`, `drawCircle`/`fillCircle`, `drawTriangle`/`fillTriangle`, sprite
  draw with scale) over `MutablePixmap`, plus: `Pixel.Mode` + the blend math
  (log #24), `Flip` (deferred at C5, log #33), and the native bulk overrides
  (log #31, below). NOT in scope: `LinePattern` (roadmap out-of-scope), decals
  and text (R3/R6), viewport/clip (R2). Ties to earlier logs: no throwaway API
  — the shape below is what later concepts (renderer R4) build on.
- **Raster is a seam: a single `RasterizerService : KGEOverridable`.** Rejected
  the pure-function alternative (the old 13-service split of `main` and the
  single-seam-per-capability rule of #34 both point here): the T2 mechanism
  already lets a decorator override just `fillRect` and delegate the rest to
  `original`, so one registry entry carries per-method substitution. Owner
  constraint: **one file, everything beyond the public service kept private**
  (no per-family helper files), to keep the close review and the test suite
  tractable. Where the type lands (image vs raster package) is a micro-plan
  detail.
- **`Pixel.Mode` and `Sprite.Flip` nest on their data types** (as in `main`),
  not in the service: `Mode` (Normal/Mask/Alpha/Custom + the blend formula
  `r = a*p.r + (1-a)*d.r`, truncating — the C4 roadmap math) is a pixel-level
  write policy; `Flip` (NONE/HORIZONTAL/VERTICAL/BOTH) is a sprite-read
  policy. This is the C6 materialization of log #24 (the type is decided and
  built here, where the consumer exists), nested in `Pixel`/`Sprite` for
  discoverability and API shape.
- **`Sprite.byteBuffer` becomes `public` + `@KGESensitiveAPI` (owner, this
  session) — a widening of the closed C5 surface, done inside C6.** The raster
  fast paths and any future consumer that proves it (GPU upload) reach the raw
  storage. KDoc states the caller owns the bytes — writing outside the `Pixmap`
  contract is the caller's responsibility; the fail-fast after close is
  preserved because `byteBuffer` returns `buffer.resource`, whose getter
  throws once released. It touches a file of a closed concept (C5): recorded
  here so the C6 close review audits the delta.
- **Tie rule (surviving touch-point "C6 tie rules", roadmap ordering
  invariants) — clarified and decided: half-open top-left in `fillTriangle`.**
  The roadmap term was undefined; it traces to the discarded 2026-08-30 plan's
  Task 10 ("half-open top/left, closed bottom/right ... tie rules", the pixel
  ownership rule for shared triangle edges). Decided: `fillTriangle` paints a
  pixel when its center is inside the triangle, top/left edges open, bottom/
  right closed — the GPU top-left convention. This is a **deliberate deviation
  from `main`**, whose fully-inclusive scanline double-blends the shared edge
  of two adjacent triangles under Alpha/Custom (main's tests pinned inclusive
  rows). Consequences: (a) a filled triangle drawn alone is pixel-identical to
  `main`'s on non-edge rows only — edge rows differ; (b) the parity oracle for
  this primitive must be an *independent* half-open scanline (same rule), not
  a copy of `main`'s inclusive one; (c) two adjacent filled triangles tile
  without a seam. The rule is recorded here and pinned by differential tests;
  `main`'s inclusive behavior is not a mandate (roadmap lenses).
- **Native bulk overrides — implemented in C6 on both targets (benchmark
  before micro-plan).** Log #31 reserved per-platform bulk `fillInts`/`copyInts`
  for "the consumer that proves the need (C6 hot loop)". A time-boxed,
  warmed benchmark spike (throwaway `BulkSpikeJvmTest`/`BulkSpikeJsTest`,
  discarded, not committed) measured the gap on real hot-loop sizes (row 1920
  and a full 1920x1080):
  - **JVM (LWJGL direct buffer), ns/elem:** fill common loop ≈ 0.10; copy
    common loop ≈ 0.45; `memCopy`/`IntBuffer` bulk ≈ 0.06–0.10 (row) — copy
    gains ~4–7×; fill already at bulk speed (the JIT vectorizes `putInt`), and
    there is no native int-pattern fill (memset repeats a byte).
  - **JS/node (same ArrayBuffer layout), ns/elem:** per-element DataView loop
    (the engine's common path) ≈ 5–6; `Int32Array.fill`/`.set` ≈ 0.07–0.28 —
    **~20–85×**; the DataView per-element loop is the C6 hot-loop bottleneck
    on web.
  - Decision: implement the native bulk path on both targets — web routes
    `fillInts`/`copyInts` through `Int32Array` views (`fill`, `set`) over the
    same backing buffer; JVM routes copy through `memCopy`/`IntBuffer` bulk
    (fill keeps the common loop — no measured gain, no native int fill). Shape
    is a micro-plan detail (expect/actual vs a protected hook on the buffer).
  - Methodological note (recorded for future benchmarks): the first spike had
    no warmup and sized its window from a 20-iteration probe — with the JIT
    not yet settled the probe could divide by zero and loop ~2^31 times (the
    "hang"); the corrected harness warms ~300 ms and measures a fixed time
    window (~400 ms) counting iterations. Numbers above are warm and
    time-boxed, still indicative not precise.
- **Micro-plan next:** `docs/plans/2026-09-08-c6-raster-microplan.md`, then
  TDD per step; review/gate as usual — including the leak audit and the
  no-parameter-without-observable-effect rule.



## 2026-09-08 — C6 (raster ops, R1): closed

Micro-plan (rev 2, interactive) delivered step by step with the owner; the
per-step `Decided:` fields in `docs/plans/2026-09-08-c6-raster-microplan.md`
are the record (touch-point decisions above stand unless superseded here).
Two-axis review passed after one fix round (report in `.opencode/reviews/`,
machine-local). Decisions recorded at close:

- **Sub-service redesign supersedes the single-seam record (owner review).**
  The touch-point "one seam, one file, everything private" shape was rejected
  in review for size; `RasterizerService` is replaced by four per-scope
  `KGEOverridable` sub-services under `dev.staticsanches.kge.rasterizer.service`
  — `DrawService` (the per-pixel mode-resolving write: blend math, OOB
  no-throw, old pixel always the stored value), `OutlineService`
  (drawLine/drawRect/drawCircle/drawTriangle), `FillService`
  (fillRect/fillCircle/fillTriangle), `DrawSpriteService` (drawSprite) —
  aggregated by `data object Rasterizer` (package `rasterizer`) that delegates
  `by` each companion (`main`'s topology). Composite primitives resolve
  sub-draws through the active sub-service, so decorator overrides compose;
  the extension-contract proof moved to the sub-services. The names
  `RasterizerService`/`SpriteService` and the term "funnel" are retired.
- **`fillTriangle` is a double deviation** (from `main`'s inclusive scanline
  AND olc's inclusive rows): GPU top-left center rule — a pixel paints when
  its center is inside, top/left edges open, bottom/right closed; the
  base-vertex row is never painted; two adjacent triangles paint a shared
  edge exactly once. The oracle is an independent float-center half-open
  scanline in the test file (300 seeded differential per mode).
- **`drawLine` adopts the reference walk verbatim** (olc v2.30 gurkanctn, tie
  phase `px<0`/`py<=0`), deviating from `main` on exact-midpoint cells. The
  partial-OOB parity debt (unclipped walk subset vs the reference clip
  re-walk) is carried to R2 (roadmap R2 entry + micro-plan step 4).
- **`BufferService` (renamed `MemoryAllocatorService`; C3 surface widened,
  recorded) owns allocation AND bulk fill/copy.** `fillInts`/`copyInts` are
  `void`: the portable fallback (memmove-safe loop) lives in the interface
  default bodies; platform defaults override only when native and call
  `super` conditionally — JVM copy via an `IntBuffer` bulk transfer of a
  `duplicate()` (fill inherits the loop: java.nio has no int fill, the loop
  already runs at bulk speed), web via the backing `TypedArray` view
  (doubling fill, `set` copy). The companion validates ranges first, so
  implementations only see legal regions. Platform files
  `BufferServiceJvm`/`BufferServiceWeb`.
- **`Pixel.Mode.Alpha` parameter accepted (owner ruling).** The secondary
  constructor's `parameterToAvoidPlatformDeclarationClash` has no observable
  effect and no test; it is `main`-parity (value-class secondary-constructor
  workaround) and accepted as such, with the API-discipline tension noted by
  the review. `Sprite.byteBuffer` public `@KGESensitiveAPI` widening and
  `Sprite.Flip` (C5 deferral) recorded; KDoc self-containment held.
- **Int-coordinate API retained.** No point type exists and none is planned;
  the roadmap records vectors/points as the next concept, naming these
  sub-services as the first consumers to adjust.
- **Gate:** `./gradlew build --rerun-tasks` green on all five suites (jvm, js
  node + browser, wasmJs node + browser) and ktlint.

## 2026-09-09 — C6 post-close: raster-test blank canvas + resource guards + `SpriteService` rename

CI (ubuntu/windows) failed the C6 commit: the failing `RasterizerTest` cases
read pixels of a freshly created `Sprite` expecting `TRANSPARENT`, but the
engine contract is unspecified initial content (this log 2026-09-04 and C5) —
the JVM/JS allocators zero fresh memory on macOS/web while reused native blocks
on linux/windows carry garbage, so the failures were allocator-dependent and
non-deterministic (different case sets per OS).

- **Tests must not assume blank content — fix in the test helper.** The
  `target()` helper (and the already-correct `grid()`) now clears the surface
  to `Colors.TRANSPARENT` right after creation, making the blank canvas
  explicit and platform-independent. Engine code untouched (content stays
  unspecified by contract).
- **`applyClosingIfFailed` ported (the C5 deferral reversed).** The C5 close
  recorded `applyClosingIfFailed` as deliberately not ported, waiting for a
  consumer ("C6 raster ... would be its first"). Its consumer arrived: the
  sprite create-and-initialize pattern in the test fixtures (`target()`,
  `distinctSprite`, the `SpriteService` decorator proof). It mirrors the
  `main` guard — receiver block, returns the receiver, closes on failure —
  and is the safe replacement for `create(...).also { init }`, which leaks the
  fresh surface when the init block throws. Registered next to
  `letClosingIfFailed` in `resource/KGEResource.kt`; the no-`also` rule for
  resource initialization follows from it. A future `alsoClosingIfFailed`
  variant is not excluded but has no consumer yet.
- **`SpriteCreationService` renamed `SpriteService` (owner).** Generic service
  name for the surface capability (create/duplicate; later sprite ops land
  here), matching `main`'s `image/service/SpriteService.kt`. No conflict with
  the retired C6 name: that `SpriteService` was the raster-aggregate
  candidate, and the raster seams carry the scope prefix
  (`DrawService`/`OutlineService`/`FillService`/`DrawSpriteService`). File,
  KDoc references, tests and the live docs (AGENTS.md current state, roadmap)
  updated; this entry is the record of the rename, so the historical C5/S5
  mentions above keep the original name of their era (as done for
  `MemoryAllocatorService`/`BufferService`).

