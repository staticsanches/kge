## 2026-09-15 — Golden image test harness: touch-point + close

A **test-infrastructure concept** (not an engine capability): assert CPU raster
output against reference images (*golden images*) committed as PNGs. Design
material: `docs/plans/2026-09-15-golden-image-microplan.md` (touch-point
decisions + per-step provenance). GL/renderer frames are out of scope (a later
concept).

### Touch-point decisions

1. **CPU raster only** (`Rasterizer` → `Pixmap`/`Sprite`): integer-exact and
   deterministic on all targets; GL full-frame goldens need tolerance and
   backend determinism (Mesa vs SwiftShader) — deferred.
2. **Oracle = hand/olc-derived**: each reference is derived from olc v2.30's
   pixel math (independent throwaway ports under `.tmp/`), never a blessed
   snapshot of the engine's output; engine-vs-golden disagreement is a finding,
   not an invitation to adjust the golden.
3. **All three targets**: the golden tests live in `commonTest` and run on jvm +
   js + wasmJs.
4. **Exact raw RGBA comparison**: PNG is authoring source; the assert compares
   bytes, so no codec — in particular not the web canvas' premultiplied-alpha
   round trip — sits in the comparison path.
5. **Gradle codegen** loading: no multiplatform test-resource mechanism exists,
   and KGP does not serve `src/*Test/resources` to browser tests.
6. **kotest matcher by name**: `sprite.shouldMatchGolden("circle/mask-all")`.
7. **Manual authoring**: nothing in the test path rewrites a reference.
8. **Failure output**: summary (name, dimensions, mismatch count) plus the
   actual surface as a copy-pasteable `WxH:<base64>` token (omitted above a
   4096-cell budget), rendered to a PNG by the inverse task.
9. **Curated 10-case matrix** (below).
10. **Only the PNG is committed**; the build decodes and validates it.
11. **Layout**: `commonTest/golden/<op>/<case>.png`, name = relative path.
12. **Surface size per case**, chosen to expose the behavior; the matcher
    validates the size against the reference.
13. Repo concept flow; no `CONTEXT.md`/ADR.
14. Vocabulary: *golden image*, `shouldMatchGolden`, `golden/`.

### Harness

- **`generateGoldenImages`** (`kge-core/build.gradle.kts`, `GenerateGoldenImagesTask`):
  `@InputDirectory src/commonTest/golden` → decodes every PNG with ImageIO,
  validates (decodable, positive dimensions, `4096` guard, unique names) and
  emits `build/generated/golden/commonTest/kotlin/.../GoldenImages.kt`
  (`object GoldenImages { val byName: Map<String, GoldenImage> }`); wired via
  `commonTest.kotlin.srcDir(taskProvider.map { it.outputDir })` so the compile
  depends on it. Configuration-cache compatible; the generated dir stays under
  `build/` and out of ktlint. The emitted RGBA byte order is `R,G,B,A` — the
  `Pixel.nativeRGBA` little-endian layout — so no channel swap at runtime.
- **`shouldMatchGolden`** (`commonTest` `golden/GoldenMatcher.kt`): resolves by
  name (unknown name lists the available names), fails on a dimension mismatch
  before any pixel walk, else compares `get(x,y).nativeRGBA` exactly; a failure
  carries the summary and, at or below `MAX_TOKEN_CELLS = 4096`, the
  `WxH:<base64>` actual token. Failures are `AssertionError`.
- **`goldenActualToPng`** (`GoldenActualToPngTask`): renders a token (from
  `-PgoldenActual` or `-PgoldenActualFile`, the latter a `@InputFile` so a
  content edit re-runs the task) to `build/golden-actual.png`. The
  RGBA↔ARGB conversion is shared with the forward task through a top-level
  `private object GoldenRgba` (a task class referencing a script-level function
  is rejected as a non-static inner class).
- **`canvas(width, height)`** (`commonTest` `golden/GoldenSurfaces.kt`) is the
  shared transparent-surface fixture.

### Cases and provenance (olc-derived, cross-checked against the pinned raster tests)

- `line/filled` 5x3 `(0,0)-(4,2)`; `line/dotted` same line (phase visible);
  `line/clipped` 4x4 `(-3,-1)-(3,2)` (phase from the clipped start).
- `circle/outline-all`, `circle/mask-two-octants` (non-adjacent `O1|O5`, the
  mask subtracts), `circle/fill` — 11x11, r=4.
- `rect/outline-filled`, `rect/fill` — 6x6 `(1,1)-(4,4)`;
  `triangle/outline-filled` 5x4 `(0,0)(4,0)(0,3)`; `triangle/fill` 4x3
  `(0,0)(0,2)(3,2)`.
- `blit/nearest` 2x2→4x4 integer scale; `blit/region` (source sub-rect);
  `blit/bilinear` = `Pixmap.sampleBL` at destination texel centers (see below).
- `sampling/periodic`, `sampling/clamp` — a mode-aware `Pixmap.get` walk.
- `blend/alpha`, `blend/mask` — 4x2 over an opaque pattern; `clip/draw-line`
  8x8 through an explicit `Pixmap.window` viewport.

### Accepted divergences (recorded, not defects)

- **`fillRect` is inclusive** of the opposite corner (`main` parity) while olc's
  `FillRect` is half-open; the `rect/fill` golden encodes KGE's inclusive box.
- **`fillTriangle` uses the top-left half-open rule** (log #12) — raw olc's
  avrfreaks fill paints the base row; deliberately not used.
- **`blit/bilinear` is not a blit**: olc's `DrawSprite`/`DrawPartialSprite` do
  not interpolate, so the only bilinear surface is `Pixmap.sampleBL` (olc
  `Sprite::SampleBL`); the golden asserts it directly.
- **`sampling/*` walks `Pixmap.get`**, not a blit: `BlitService` reads
  `uncheckedGet` and validates in-bounds, so no blit can observe `sampleMode`.
- **`clip/draw-line` uses the `Pixmap.window` viewport seam** (`ClipService`
  against window-local bounds; the olc walk keeps pre-clip `dx/dy` and the
  window write bound drops the local out-of-range cells).

### Test-source-set visibility convention (cross-cutting)

`AGENTS.md` scope discipline now states that **test source sets drop `internal`
from the ladder** (it is a no-op on unpublished code): prefer `private`, widen
to no modifier (default `public`) only when shared across test files or when
`private` is impossible (`expect`/`actual`). `review-standards.md` was updated
to match; the round removed the (meaningless) `internal` from the test source
sets and made the single-file `testWindowConfig` `private`.

### Incidental fix

`DecalAddonsTest` asserted an unwritten layer-target pixel was `TRANSPARENT`;
`SpriteService.create` documents content as unspecified, so the test now clears
the target to a sentinel first. (This was the failing CI job at the round's
start.)

### Review and gate

Two-axis review (Standards + Spec). Round 1: Spec PASS (2 minor), Standards
FAIL (1 Important — `goldenActualToPng`'s `tokenFile` was an `@Input` holding
only the path, so editing the file left the task UP-TO-DATE; 2 minor —
`surfaceFrom` not closing on failure, duplicated `canvas` fixtures). Fixed in
one round (`@InputFile RegularFileProperty`, `applyClosingIfFailed`, shared
`GoldenSurfaces.kt`, reconciled the micro-plan's `internal` wording), scope
re-review clean: Spec PASS (1 minor accepted: the decoded-length check is
unreachable — the accessor is build-generated), Standards PASS. Gate:
`./gradlew build --rerun-tasks` green (jvm + js browser + wasmJs browser +
ktlint + assemble/metadata).

### Carry-forward

- GL/renderer golden frames (tolerance, backend determinism) — separate concept.
- No auto-bless/snapshot update; references stay manual/olc-derived.
- A browser-side artifact is not possible; the `WxH:<base64>` token is the
  cross-platform diagnostic.
