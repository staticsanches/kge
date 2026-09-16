# Golden image harness micro-plan (TDD)

**Date:** 2026-09-15. Touch-point decisions (grill, this session) are recorded
below and carried to the close entry. Concept: a test-only harness that asserts
CPU raster output against committed reference images (*golden images*), running
on jvm + js + wasmJs. GL/renderer output is out of scope (a later concept).

## Working mode

- **Step-by-step TDD.** One step = test (red) → run → implement (green on jvm +
  js browser + wasmJs browser) → mark. Doubts resolved in-session with the owner.
- **Implementation/test writing may be delegated** to a fresh sub-agent per step
  (context hygiene); the owner reviews the diff at the step boundary.
- **Two-axis review** (Standards + Spec) at the close, via two fresh general
  sub-agents, report + marker as usual.
- **Gate:** `./gradlew build --rerun-tasks` (jvm + js browser + wasmJs browser +
  ktlint + assemble/metadata). Only a force-executed green counts.
- **Oracle = olc v2.30 + `main`.** Each reference is derived from olc's pixel
  math (`.tmp/olc/olcPixelGameEngine.h`); `main` is evidence for the Kotlin-level
  expected values. Existing exact-pixel raster tests stay the primary contract —
  goldens are a complementary, cross-platform cross-check, not a replacement.
  A golden that contradicts olc + `main` is a bug in the golden.
- **References are never re-blessed from engine output.** Authoring is manual
  (below); no test ever rewrites a reference.

## Touch-point decisions

1. Layer: CPU raster only (`Rasterizer` → `Pixmap`/`Sprite`).
2. Oracle: hand/olc-derived references with recorded provenance.
3. Targets: jvm + js + wasmJs (`commonTest`).
4. Comparison: exact raw RGBA (no codec in the assert path); PNG is authoring
   source only.
5. Loading: Gradle codegen from the committed PNGs.
6. Utility: kotest matcher by name — `sprite.shouldMatchGolden("circle/mask-all")`.
7. Creation: manual; a Gradle task converts/validates; nothing auto-blesses.
8. Failure: a summary (name, dimensions, mismatch count) plus the actual
   surface as a copy-pasteable `WxH:<base64>` token (omitted above a size
   budget), feeding an inverse task that renders it to a PNG.
9. Matrix: 10 categories (steps 2-7).
10. Source of truth: **only the PNG is committed**; the task decodes and validates.
11. Layout: `golden/<op>/<case>.png`; the name is the relative path, `/`-separated.
12. Surface size: per case, chosen by the test to expose the behavior (a sloped
    line needs a larger canvas than a 1-px point); the matcher validates it.
13. Process: repo concept flow; no `CONTEXT.md`/ADR.
14. Vocabulary: *golden image*, `shouldMatchGolden`, directory `golden/`.

## Resume tracker (a new session starts at the first unchecked step)

- [x] **0. Resource dir + codegen/validation Gradle task**
- [x] **1. `GoldenImages` accessor + `shouldMatchGolden` matcher + `goldenActualToPng`**
- [x] **2. Lines + `LinePattern` goldens**
- [x] **3. Circles + octant-mask goldens**
- [x] **4. Rectangles + triangles goldens**
- [x] **5. Blit nearest / bilinear / region goldens**
- [x] **6. Sprite sampling (`PERIODIC`/`CLAMP`) goldens**
- [x] **7. Blend `Alpha`/`Mask` + clip/viewport goldens**
- [x] **8. Gate + review + decisions-log close**

## Contract

### Golden files (`commonTest`)

- Live at `kge-core/src/commonTest/golden/<op>/<case>.png`. The golden *name* is
  the path relative to `golden/` without the `.png`, `/`-separated
  (`line/dotted-45`). Only PNGs are committed; no `.bin` is committed.
- Authored manually (image editor or a one-off `magick` command) from olc's
  pixel math; the PNG that is committed is the reference.

### Codegen + validation (Gradle, `kge-core`)

- A task `generateGoldenImages` reads the `golden/` directory, decodes every PNG
  on the build JVM (`javax.imageio.ImageIO`), and emits a Kotlin source into
  `build/generated/golden/commonTest/kotlin/dev/staticsanches/kge/golden/`:
  `object GoldenImages { val byName: Map<String, GoldenImage> }` with
  `class GoldenImage(val name: String, val width: Int, val height: Int, val rgbaBase64: String)`
  (test source sets carry no `internal` — see the AGENTS.md scope rule).
- The emitted RGBA byte order is `R, G, B, A` — identical to `Pixel.nativeRGBA`
  little-endian, so runtime comparison needs no channel swap.
- Validation **fails the build**: an undecodable PNG, non-positive dimensions,
  dimensions above a guard, or two files mapping to the same name.
- Wired as `commonTest.kotlin.srcDir(taskProvider.map { it.outputDir })` so the
  compile depends on it automatically; configuration-cache compatible
  (`@get:InputDirectory`/`@get:OutputDirectory`). Output is under `build/`
  (gitignored) and must stay out of ktlint.

### Inverse task (Gradle, `kge-core`)

- A task `goldenActualToPng` turns the matcher's `WxH:<base64>` failure token
  (same `R,G,B,A` order) into a PNG under `build/` for visual inspection: it
  takes the token or a file holding it, and writes e.g.
  `build/golden-actual.png`. Configuration-cache compatible.

### Matcher (`commonTest`, `dev.staticsanches.kge.golden`)

- `fun Pixmap.shouldMatchGolden(name: String)` — a kotest matcher (no modifier:
  shared across the golden test files).
- Unknown name fails, listing the available names.
- A dimension mismatch fails **before** any pixel walk (no partial compare; the
  test chose the wrong surface or the golden is wrong).
- Otherwise it walks every cell via `get(x, y).nativeRGBA` (exact) and counts
  the mismatches. The failure message carries the name, both dimensions, the
  mismatch count, and — when the surface is at or below the size budget — the
  actual RGBA as a `WxH:<base64>` token in the golden's `R,G,B,A` order, ready
  to paste into `goldenActualToPng`. Above the budget the token is omitted and
  the summary says so.
- Exact equality, no tolerance: CPU raster is integer-exact, alpha is straight,
  and olc draws without anti-aliasing.

## Steps

### 0. Resource dir + codegen/validation task

- **Change:** create `commonTest/golden/` with one tiny smoke PNG (e.g. a 2x2
  `harness/smoke.png`); add the `generateGoldenImages` task and the `srcDir`
  wiring in `kge-core/build.gradle.kts`.
- **Verify:** the task runs on `build`; the generated `GoldenImages.kt` contains
  the smoke entry with the right dimensions; a throwaway test reads it; gate
  green on all targets. Infra step — the green gate is the evidence.
- **Decided:** done — `smoke.png` (2x2 RGBA, translucent pixel), task
  `generateGoldenImages` (`GenerateGoldenImagesTask`) decoding via ImageIO and
  emitting the accessor, wired through `commonTest.kotlin.srcDir`; validates
  decode, positive dimensions and duplicates. The generated dir is covered by
  the pre-existing `build/` ktlint exclusion. Gate green (jvm 641 / js 676 /
  wasmJs 676).

### 1. `GoldenImages` accessor + `shouldMatchGolden` matcher + inverse task

- **Tests (red first), commonTest:** a `Pixmap` equal to the smoke golden passes;
  a surface with one changed cell fails with a message carrying the name, both
  dimensions, a mismatch count of 1, and a `WxH:<base64>` token that decodes to
  the actual surface; a two-cell change reports a count of 2; a dimension
  mismatch fails and the message says both sizes; an unknown name fails and
  lists the available names; an alpha-only change on the golden's translucent
  pixel is a mismatch; above the size budget the token is omitted and the
  summary says so. Tests that must fail use `shouldThrow<AssertionError>` and
  assert the message content.
- **Inverse task:** `goldenActualToPng` turns a `WxH:<base64>` token into a PNG
  under `build/`; verify it round-trips a token produced by the matcher back to
  the original pixels (compare with `BytesDecoder`/`Pixmap.get` or the golden
  codec), and that an undecodable token fails the task.
- **Files:** `commonTest` `golden/GoldenMatcher.kt` + `GoldenMatcherTest.kt`;
  the inverse task in `kge-core/build.gradle.kts`.
- **Decided:** done — matcher `fun Pixmap.shouldMatchGolden(name)` with
  the summary + `WxH:<base64>` token contract and a 4096-cell budget
  (`MAX_TOKEN_CELLS`), failures as `AssertionError` (unknown name lists the
  available names; a dimension mismatch fails before the walk). Inverse task
  `goldenActualToPng` (`-PgoldenActual="WxH:<base64>"` / `-PgoldenActualFile`),
  output `build/golden-actual.png`, round-trip verified against the smoke
  pixels. The RGBA↔ARGB conversion is shared with `generateGoldenImages` through
  a top-level `private object` (Gradle rejects a task class that references a
  script-level member function as a non-static inner class). `harness/oversized`
  (65x64 solid) exercises the above-budget branch. Gate green (jvm 648 / js 683
  / wasmJs 683).

### 2. Lines + `LinePattern`

- **Cases:** `line/filled` (a sloped line on a canvas large enough to show the
  walk), `line/dotted` (phase visible), `line/clipped` (pattern phase from the
  first cell of the clipped walk).
- **Tests:** draw each case onto a surface of the chosen size and
  `shouldMatchGolden`. Cross-check against the existing `RasterizerTest`
  assertions for the same case.
- **Decided:** done — goldens derived from an independent port of olc
  `DrawLine` + `ClipLineToDrawTarget` with olc's `rol()` phase (throwaway oracle
  under `.tmp/`, not committed): `line/filled` 5x3 `(0,0)-(4,2)` →
  `(0,0)(1,1)(2,1)(3,2)(4,2)`; `line/dotted` same line →
  `(0,0)(2,1)(4,2)`; `line/clipped` 4x4 `(-3,-1)-(3,2)` → `(0,0)(2,1)` (phase
  from the clipped start). Each painted-cell set matches the engine's pinned
  raster assertions; no divergence. Gate green (jvm 651 / js 686 / wasmJs 686).

### 3. Circles + octant masks

- **Cases:** `circle/outline-all`, `circle/mask-two-octants`, `circle/fill`.
- **Decided:** done — goldens derived from olc `DrawCircle`/`FillCircle` +
  octant masks (oracle under `.tmp/`): `circle/outline-all` 11x11 r=4 ALL → 24
  ring cells; `circle/mask-two-octants` 11x11 r=4 `O1|O5` → 8 cells (subset);
  `circle/fill` 11x11 r=4 ALL → 61 cells. Cross-checked against the pinned
  `RasterizerTest`/`CircleOctantMaskTest` references; no divergence. Gate green
  (jvm 654 / js 689 / wasmJs 689).

### 4. Rectangles + triangles

- **Cases:** `rect/outline-filled`, `rect/fill`, `triangle/outline-filled`,
  `triangle/fill`.
- **Decided:** done — olc-derived oracle (`.tmp/`): `rect/outline-filled` 6x6
  `(1,1)-(4,4)` 12 perimeter cells; `rect/fill` 6x6 same box 16 cells;
  `triangle/outline-filled` 5x4 `(0,0)(4,0)(0,3)` 11 cells; `triangle/fill` 4x3
  `(0,0)(0,2)(3,2)` 3 cells. Two recorded KGE semantics, not defects: `fillRect`
  is inclusive (main parity) while olc `FillRect` is half-open, and
  `fillTriangle` uses the log #12 top-left half-open rule (raw olc avrfreaks
  `FillTriangle` paints the base row; deliberately not used). Each golden
  matches the pinned `RasterizerTest` cells. Gate green (jvm 658 / js 693 /
  wasmJs 693).

### 5. Blit nearest / bilinear / region

- **Cases:** `blit/nearest`, `blit/bilinear`, `blit/region` (a
  `Pixmap.window`/`blitRegion` source).
- **Decided:** done — `blit/nearest` 2x2→4x4 integer scale, nearest blocks,
  `Normal` verbatim (olc `DrawSprite`); `blit/region` uses `blitRegion`
  (source 5x5 gradient → 4 cells; olc `DrawPartialSprite`); both cross-checked
  against `RasterizerTest`/`BlitWindowTest`. **`blit/bilinear` is not a blit**:
  olc's `DrawSprite`/`DrawPartialSprite` do not interpolate, so the only
  bilinear surface is `Pixmap.sampleBL` (olc `Sprite::SampleBL`, decoded
  trunced RGB + alpha forced 255, corners folded) — the golden asserts
  `sampleBL` at destination texel centers, sourced from the `Pixmap.sampleBL`
  KDoc / `PixmapTest` oracle, not engine output. Gate green (jvm 661 / js 696 /
  wasmJs 696).

### 6. Sprite sampling (`PERIODIC`/`CLAMP`)

- **Cases:** `sampling/periodic`, `sampling/clamp` (source `Sprite` over-sampled
  through a blit so the wrap mode is observable).
- **Decided:** done, with one justified deviation from the wording above: a blit
  cannot expose `sampleMode` (its core reads `uncheckedGet` and validates
  in-bounds, so no out-of-range source read is reachable). The golden walks a
  3x3 source under the mode into a 5x5 target via mode-aware `Pixmap.get` (the
  `SampleMode` seam = olc `Sprite::GetPixel`): `sampling/periodic` = `abs(x % w)`
  wrap, `sampling/clamp` = `coerceIn` edge, both cross-checked against
  `PixmapTest` and `main`. No engine divergence. Gate green (jvm 663 / js 698 /
  wasmJs 698).

### 7. Blend `Alpha`/`Mask` + clip/viewport

- **Cases:** `blend/alpha`, `blend/mask`, `clip/draw-line` (viewport clipping
  keeps out-of-clip pixels untouched).
- **Decided:** done — olc-derived oracle (`.tmp/`): `blend/alpha` 4x2 over an
  opaque pattern, `fillRect` translucent `Alpha` (truncating `a*src + (1-a)*dst`,
  resolved opaque); `blend/mask` 4x2, translucent skipped and opaque written
  (`Mask` = `a == 255`); both cross-checked against the pinned `ModeTest`.
  `clip/draw-line` 8x8 draws through `Pixmap.window` (the explicit viewport
  seam): `ClipService.clipLineTo` against the window-local bounds, then the
  olc walk with pre-clip `dx/dy`, and the window write bound drops local
  `x>=4`/`y>=4`; inside cells match `RasterizerClipTest`/`BlitWindowTest`,
  outside kept. No engine divergence. Gate green (jvm 666 / js 701 /
  wasmJs 701).

### 8. Gate + review + decisions-log close

- **Gate:** `./gradlew build --rerun-tasks` green.
- **Audits:** resource discipline (the matcher allocates the decoded reference —
  free it on every path); no-parameter-without-observable-effect; visibility
  confines the harness to `commonTest` (no `internal`, `private` where a file
  uses a helper alone).
- **Two-axis review** (two fresh general sub-agents), then the close entry in
  `docs/decisions/phase-1/28-golden-image-harness.md` + the `phase-1.md` index +
  the roadmap current-state update.
- **Decided:** done — gate green. Round-1 review: Spec PASS (2 minor), Standards
  FAIL (1 Important: `goldenActualToPng`'s `tokenFile` typed as an `@Input`
  path-only property → stale UP-TO-DATE; fixed to `@InputFile
  RegularFileProperty` and verified by a content edit; plus `surfaceFrom`
  closing and the duplicated `canvas` helpers). Scope re-review clean: Spec PASS
  (1 minor accepted as unreachable), Standards PASS. Close entry #28 written;
  `phase-1.md` index and the roadmap current-state updated.

## Files

New: `kge-core/src/commonTest/golden/**/*.png`; `kge-core` Gradle task(s) in
`build.gradle.kts` (or `buildSrc`): `generateGoldenImages` + `goldenActualToPng`;
generated `GoldenImages.kt` (under `build/`); `commonTest` `golden/GoldenMatcher.kt`,
`golden/GoldenSurfaces.kt`, `golden/GoldenMatcherTest.kt`,
`golden/GoldenImagesTest.kt`, and the per-category golden test files. Modified:
`kge-core/build.gradle.kts` (srcDir + task wiring).
Close: `docs/decisions/phase-1/28-golden-image-harness.md`,
`docs/decisions/phase-1.md`, the roadmap.

Out of scope: GL/renderer golden frames; color tolerance; auto-bless/snapshot
update; `CONTEXT.md`/ADR; a browser-side file artifact (the `WxH:<base64>` token
is the cross-platform diagnostic); replacing the existing exact-pixel raster
tests.
