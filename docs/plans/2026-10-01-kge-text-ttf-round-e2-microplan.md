# `kge-text-ttf` — micro-plan, round E2 (golden harness + text goldens)

**Date:** 2026-10-01. Round **E2** of the text work: the harness becomes shared
(`kge-test-support` + `buildSrc`) and `kge-text-ttf` gains its committed text
goldens. Rounds: A bitmap (core) → B scaffold → C face + shaping + layout → D
raster + atlas → E1 CPU blit + addons → **E2 golden harness + text goldens** →
E3 coverage texture + decal. Context: the round E2 touch-point
(`docs/plans/2026-10-01-r6-round-e2-touchpoint.md` — decisions 1–5 are settled
there), the E1 micro-plan (`docs/plans/2026-09-29-kge-text-ttf-round-e1-microplan.md`,
whose §"Pinned expectations" is the derivation source), the harness chunk
(`docs/decisions/phase-1/28-golden-image-harness.md`), the chunk `31` `buildSrc`
precedent (`buildSrc/src/main/kotlin/EmbedResourcesTask.kt` + `EmbeddedResources.kt`).

## Scope

New module `kge-test-support`; `kge-core` migrated onto it; `kge-text-ttf` golden
suite; a throwaway `.tmp/` oracle. **No production change** in `kge-core` or
`kge-text-ttf`, **no new dependency**, no change to the harness's comparison
semantics.

## Slices

- **A — the shared harness**: `buildSrc` codegen (pure renderer + tasks + helper)
  and the new `kge-test-support` module with the matcher, the fixtures, the moved
  matcher suite and its two `harness/*` references.
- **B — `kge-core` migration**: the local harness and the inline codegen are
  deleted, the module depends on `kge-test-support`, every existing golden
  expectation stays byte-identical.
- **C — the oracle and the text goldens**: the independent `.tmp/` driver, the
  eleven references, and the `kge-text-ttf` golden suite.

Slice A must land before B (the shared type is generated into), and B before C
(C wires the module through the same helper B proves on core).

## Slice A — the shared harness

### A1 — the pure renderer (`buildSrc`, red → green)

New `buildSrc/src/main/kotlin/GoldenImages.kt`:

```kotlin
data class GoldenImageSpec(val name: String, val width: Int, val height: Int, val rgbaBase64: String)

fun renderGoldenImages(packageName: String, images: List<GoldenImageSpec>): String
```

Contract (pinned by `buildSrc/src/test/kotlin/GoldenImagesRenderingTest.kt`,
`kotlin.test`, `assertContains`/`assertFailsWith` like `EmbeddedResourcesTest`):

- emits `package <packageName>`, then exactly these imports:
  `dev.staticsanches.kge.image.Pixmap`,
  `dev.staticsanches.kge.testsupport.golden.GoldenImage`,
  `dev.staticsanches.kge.testsupport.golden.shouldMatchGolden`;
- emits `object GoldenImages { val byName: Map<String, GoldenImage> = listOf(...).associateBy { it.name } }`
  with one `GoldenImage("<name>", <width>, <height>, "<base64>")` per spec, **sorted by name**
  regardless of input order;
- emits the binder, with its one-line KDoc, verbatim:
  `/** Asserts this surface against this module's golden [name]. */`
  `fun Pixmap.shouldMatchGolden(name: String) = this.shouldMatchGolden(GoldenImages.byName, name)`
- fails on a blank package, a non-positive dimension, a blank name, a duplicate
  name, and a malformed base64 payload;
- is byte-deterministic: the same specs in a different order render the same file.

Red: `renderGoldenImages` does not exist. Green: `tools/gradle -p buildSrc test`.

### A2 — the tasks, the helper and the module wiring (red → green)

Move to `buildSrc/src/main/kotlin/GenerateGoldenImagesTask.kt` (JVM, ImageIO):

- `GoldenImagesTask`'s decode half: walk `inputDir` for `*.png`, name =
  path relative to `inputDir` without the extension, decode with `ImageIO.read`,
  guard decodable/positive dimensions/`MAX_DIMENSION = 4096`/unique names — the
  chunk `28` rules, moved verbatim — then call `renderGoldenImages` and write
  `<outputDir>/<packageName as path>/GoldenImages.kt`.
- `GoldenActualToPngTask` and `private object GoldenRgba`, moved verbatim from
  `kge-core/build.gradle.kts` (including the `@InputFile tokenFile` fix of the
  chunk `28` review round).
- `fun Project.goldenImages(taskName: String, packageName: String, inputDir: Directory, outputDir: Provider<Directory>): TaskProvider<GenerateGoldenImagesTask>`,
  mirroring `embedResources`: registers the generator under [taskName] with
  `description`/`group = "build"`, registers `goldenActualToPng` (description and
  group `verification`, output `build/golden-actual.png`), and returns the
  generator provider.

New module `kge-test-support` (add to `settings.gradle.kts`), build script
mirroring `kge-text-ttf`'s minus the web/native dependencies: KMP + ksp + ktlint
+ kotest plugins, `jvm { jvmTarget = JVM_11 }`, `js(IR)`/`wasmJs` with
`useChromeHeadlessNoSandbox()`, `commonMain.dependencies { api(project(":kge-core")) }`,
`commonTest` kotest framework/assertions + `jvmTest` the JUnit5 runner, plus:

```kotlin
val generateGoldenImages =
    goldenImages(
        taskName = "generateGoldenImages",
        packageName = "dev.staticsanches.kge.golden",
        inputDir = layout.projectDirectory.dir("src/commonTest/golden"),
        outputDir = layout.buildDirectory.dir("generated/golden/commonTest/kotlin"),
    )

kotlin { sourceSets { commonTest { kotlin.srcDir(generateGoldenImages.flatMap { it.outputDir }) } } }
```

Move in: `golden/harness/smoke.png` and `golden/harness/oversized.png` from
`kge-core`'s `commonTest/golden/`, and `GoldenImagesTest.kt` (the accessor smoke
test) rewired to the new package's imports.

Red: `tools/gradle :kge-test-support:jvmTest` fails (no module, no task, no
accessor). Green: the accessor smoke test passes; `jsBrowserTest` and
`wasmJsBrowserTest` follow in A3.

### A3 — the matcher, the fixtures and the moved suite (red → green)

`kge-test-support/src/commonMain/kotlin/dev/staticsanches/kge/testsupport/golden/`:

- `GoldenImage.kt` — the moved `GoldenImage(name, width, height, rgbaBase64)`,
  same four `val`s, no behavior change (the type stops being generated).
- `GoldenMatcher.kt` — the moved matcher, with the map made explicit:
  `fun Pixmap.shouldMatchGolden(goldens: Map<String, GoldenImage>, name: String)`;
  message text, the `MAX_TOKEN_CELLS = 4096` budget, the dimension-first check,
  the `nativeRGBA` comparison and the token emission are unchanged.
- `GoldenSurfaces.kt` — the moved `canvas(width, height)`, unchanged.

`kge-test-support/src/commonTest/kotlin/.../golden/`:

- `GoldenMatcherTest.kt` — moved, its seven cases unchanged in substance, now
  importing `dev.staticsanches.kge.golden.GoldenImages` and the generated binder
  so the module's own suite pins the one-argument path end to end.
- the moved `GoldenImagesTest.kt` (A2).
- Test source sets keep the `private`-first ladder with no `internal`.

Red: the moved tests do not compile (no matcher, no fixtures). Green:
`tools/gradle :kge-test-support:jvmTest`, then `:jsBrowserTest` and
`:wasmJsBrowserTest` — all three must report the moved case counts, not merely
exit 0 (the `#35` guard).

## Slice B — `kge-core` migration

`kge-core/build.gradle.kts`: drop the `java.awt`/`javax.imageio`/`java.util.Base64`
imports, the `GoldenRgba` object, `GenerateGoldenImagesTask`, the
`goldenActualToPng` registration and the `generateGoldenImages` val; replace with
`goldenImages(...)` exactly as in A2; add
`commonTest.dependencies { implementation(project(":kge-test-support")) }`.

Delete from `kge-core`: `commonTest/.../golden/GoldenMatcher.kt`,
`GoldenSurfaces.kt`, `GoldenMatcherTest.kt`, `GoldenImagesTest.kt`, and the two
`golden/harness/*.png`. Keep the six golden test classes; add
`import dev.staticsanches.kge.testsupport.golden.canvas` to the five that use it
(`GoldenLineTest`, `GoldenCircleTest`, `GoldenRectTriangleTest`,
`GoldenBlitTest`, `GoldenSamplingTest`). Their package
(`dev.staticsanches.kge.golden`) and call sites stay untouched, so the generated
binder resolves with no import.

Red: with the local matcher deleted, `tools/gradle :kge-core:jvmTest` fails to
compile. Green: jvm + js + wasmJs green with every expectation byte-identical.
**This slice is the round's regression guard: a diff to any golden expectation
here is a finding, not a fix.**

## Slice C — the oracle and the text goldens

### Delta found by the oracle (2026-10-01): the scale anchor

The oracle's independent implementation exposed an E1 defect. `TextDraw.kt`
scales the **absolute** pen
(`((penX + offset.x) * scale).roundToInt() + bearing.x * scale`, where `penX`
already carries `x`), so `scale > 1` magnifies the draw origin too and the text
lands at `x * scale`, `y * scale`. Both references do the opposite: olc draws at
`x + sx + i*scale` with `sx` the scaled line-relative pen
(`olcPixelGameEngine.h:4137-4153`), and this repo's own `C7`
`DrawStringService.drawText` draws at `x + sx + i*scale` as well. Magnifying the
anchor violates the round E touch-point's own rule ("scale multiplies both the
advance and the painted block") and would move any text drawn at a non-zero
position — E1's scale test only ever used the origin `(0, 0)`, which is why it
passed.

Fix (production delta, in this round): scale the **line-box-relative** offsets
and add the origin back —

```kotlin
x + ((penX - x + glyph.offset.x) * scale).roundToInt() + placed.bearing.x * scale,
y + ((penY - y - glyph.offset.y) * scale).roundToInt() + placed.bearing.y * scale,
```

At `scale = 1` this is bit-identical to today for an integer `x`/`y`
(`round(v - x) + x == round(v)`), so every E1 expectation stands; the pinned
non-zero-origin case is `"A"` at `(2, 2)` with `scale = 2`: ink box 22x24 at
`(2, 8)`, Σ alpha `39932`, against the defect's `(4, 10)`. The oracle's
`text/scale-2` already holds the documented anchor, so the reference does not
change. Recorded in the decisions entry as an E1 defect corrected by E2.

A second, uncovered divergence the delta pins: the oracle's own log scales the
*first* line's baseline offsets but adds `lineTop` unscaled
(`lineTop + round((ascender - offset.y) * scale)`), while olc advances the row
by `8 * scale` per newline and this repo's `C7` advances `sy += 8 * scale`. The
line advance scales (the touch-point's "multiplies both the advance and the
painted block"), so the engine's fix — scaling the whole line-box-relative
offset, `y + round((n * lineHeight + ascender - offset.y) * scale)` — is the
olc/C7-shaped one. No reference covers multi-line with `scale > 1` (`multiline`
is `scale = 1`, `scale-2` is single-line), so nothing is re-derived; the delta
adds the pin, `"A\nA"` at `(2, 2)` with `scale = 2` putting the second line's
ink top exactly `38` px (the scaled line height) below the first's.

### Delta 2 found by the goldens (2026-10-01): the composite must be target-independent

The two `prefilled-*` references pass on jvm and wasmJs and fail on `jsBrowserTest`
only — 1 cell in `prefilled-opaque`, 12 in `prefilled-translucent`, one channel,
one unit. Measured (`/tmp` probe, reproduced from the references themselves): the
reference encodes the **f32** evaluation exactly (0 mismatching cells), Kotlin/JS
evaluates the same expression in **double** (1 and 12 cells), and an exact
**integer** evaluation differs from the f32 one in 1 and 13 cells. Over 82,620
sampled `(source alpha, destination alpha, channel)` combinations the integer and
f32 results differ by one in 3.5% of the colour channels, and the alpha channel
never differs. Kotlin/JS's `Float` is a `Double`, so the float form of the
straight-alpha source-over is a *platform-dependent* function — the opposite of
what an exact-RGBA golden (chunk `28` decision 1) can pin.

Fix (production delta, in this round): `TextDraw.kt`'s private `srcOver` is
redefined in exact integer arithmetic, the same rational function the float form
approximates, truncated:

```kotlin
val n = a * 255 + oldPixel.a * (255 - a)
// (source * a * 255 + destination * oldPixel.a * (255 - a)) / n, channel-wise
// alpha = n / 255
```

`Int` arithmetic is exact on all three targets and the largest intermediate
(`255 * 255 * 255 * 2 = 33_162_750`) fits an `Int`, so the composite becomes
target-independent by construction. `olc` has no TTF path, so this is our
contract's arithmetic, not a parity divergence; the module's `scale=1` placement
and the `da == 0` / `sa == 0` short-circuits are untouched.

Consequences: the two `prefilled-*` references are regenerated from the oracle
updated to the same documented rule (1 and 13 cells change; Σ alpha is unchanged
at `183600` and `97104`, so the oracle's own derivations still hold), the other
nine references must stay byte-identical, and any E1 expectation that shifted is
re-derived from the integer rule. **Carry-forward, not fixed here:** `kge-core`'s
`DrawService.blendAlpha` uses the same float formulation
(`(color.a / 255f) * blendFactor`, `1f - a`), so the same Kotlin/JS hazard exists
there; it is a core concept with an arbitrary `blendFactor` and no failing
evidence, so it is dispositioned in the decisions entry rather than widened into
this round.

### Delta 3 (small, from C2's red): the codegen cannot render an empty golden set

`renderGoldenImages` emits an untyped `listOf()` for zero images, so a module
that wires the harness before its first reference does not compile
(`Cannot infer type for type parameter 'T'`). Emit an empty typed list instead
and pin it in the `buildSrc` test.

### C1 — the independent oracle (`.tmp/golden-oracle/`, throwaway)

A standalone Kotlin/JVM Gradle project (`settings.gradle.kts`,
`build.gradle.kts`, `src/main/kotlin/Oracle.kt`) run with
`tools/gradle -p .tmp/golden-oracle run`: LWJGL `harfbuzz` + `freetype` + `core`
(version from `gradle/libs.versions.toml`, host classifier from
`buildSrc/src/main/kotlin/LwjglNatives.kt`), reading
`kge-font-roboto/fonts/roboto/Roboto[wdth,wght].ttf` directly.

**It must import no `kge-text-ttf` class** (`Font`, `TtfTextService`,
`GlyphAtlas`, `TextDraw`) — the orchestrator checks this statically on the
source before any reference is accepted. It mirrors round D's raster parameters
(the same FreeType load flags/pixel mode the module uses) and re-implements the
placement and composition rules from the touch-point: the `'\n'` line box
(`lineHeight = ceil(ascender - descender + lineGap)`, `baseline = lineTop +
ascender`), the tab stop (`(floor(penX / step) + 1) * step`), `dest =
(round(penX + offset.x) + bearing.x, round(lineTop + ascender - offset.y) +
bearing.y)`, integer-`scale` block replication, the coverage-weighted tint
(`alpha = tint.a * coverage / 255`), src-over compositing and target clipping.

For each of the eleven cases the oracle asserts its own `(ink box, Σ alpha)`
against the derivation table (below) and prints the pair; a mismatch aborts the
run. The printed table is the provenance evidence the report carries.

### C2 — the references and the `kge-text-ttf` suite (red → green)

Copy the oracle's PNGs to `kge-text-ttf/src/commonTest/golden/text/`. Wire
`commonTest.dependencies { implementation(project(":kge-test-support")) }` and the
`goldenImages(...)` block as in A2, and add
`commonTest/kotlin/dev/staticsanches/kge/text/ttf/golden/TextGoldenTest.kt`
(package `dev.staticsanches.kge.text.ttf.golden`), one `test` per case:

```kotlin
test("text/plain matches the golden") {
    Font.load(Roboto.variableFont).use { font ->
        canvas(30, 24).use { target ->
            TtfTextService.drawString(
                font, target, 2, 2, "Ao", 16, Colors.WHITE, 1, 4, Pixel.Mode.Normal,
            )
            target.shouldMatchGolden("text/plain")
        }
    }
}
```

Red: the first case fails with `unknown golden "text/plain"` before its PNG
exists (the matcher's own error path is the red evidence). Green: all eleven on
jvm + js + wasmJs, with the reported counts read per the `#35` rule.

### C3 — the case set and the derivation

Eleven cases under `text/`, all at 16 px on the shipped Roboto default instance,
surfaces ≤ 48x32, `Pixel.Mode.Normal`, tint opaque white unless stated:

| case | draw | derived expectation to record |
|---|---|---|
| `text/plain` | `"Ao"` at (2, 2) | box from the pinned advances; ink box + Σ alpha |
| `text/kerned` | `"AV"` at (2, 2) | `V` at the kerned advance 9.765625, not `A`-alone's |
| `text/tab-stop` | `"A\tB"` at (2, 2) | `B`'s origin at x + 15.875 (E1's stop pin) |
| `text/multiline` | `"A\nB"` at (2, 2) | line 2 exactly 19 px lower |
| `text/scale-2` | `"A"` at (2, 2), `scale = 2` | 2x2 blocks, box 22x24, pen 20.875 |
| `text/tint-opaque` | `"A"` at (2, 2), tint `rgba(220, 30, 40, 255)` | per-cell `tint` × coverage, Σ alpha 9983 |
| `text/tint-translucent` | `"A"` at (2, 2), tint `rgba(220, 30, 40, 128)` | per-cell `tint.a * coverage / 255` |
| `text/prefilled-opaque` | `"A"` at (2, 2) over the opaque pattern | src-over; a no-ink cell equals the pattern exactly |
| `text/prefilled-translucent` | `"A"` at (2, 2) over the alpha-128 pattern | src-over with both sides carrying alpha |
| `text/clipped` | `"A"` at (-4, -6) on 9x9 | only the in-bounds cells, no throw |
| `text/mark` | `"q\u0323"` at (2, 6) | the E1 mark pins: base (0, 6), mark (6, 19), pen 9.09375 |

The pre-filled destination is the harness's familiar deterministic pattern,
`rgba(20 + 15x, 30 + 20y, 200 - 10(x + y), a)` with `a = 255` (opaque) and
`a = 128` (translucent), written cell by cell before the draw — the same shape
`kge-core`'s `blend/*` goldens use, so the composite is pinned against a
destination with structure instead of a flat color.

Surfaces as the oracle rendered them (C1, recorded): `plain`/`kerned`/`tint-*`/
`prefilled-*` 30x24, `tab-stop` 32x24, `multiline` 30x36, `scale-2` 48x32,
`clipped` 9x9, `mark` 16x32. Two recorded deviations from the ≤48x32 budget note:
`multiline` needs 36 rows — the second line's ink runs to row 35 — so the budget
gives way to holding the whole line (E1's own newline test uses height 40); and
`prefilled-opaque` shows 65 changed cells for 66 ink cells, because the
truncating src-over can leave a coverage-≤2 cell exactly at the pattern — the
reference pins that truncation deliberately, it is not a rendering gap.

The oracle's per-case derivations (derived vs rendered ink box, Σ alpha, changed
cell count, sha256) live in `.tmp/golden-oracle/derivation.md`; they are
transcribed into the decisions entry, since the scratch tree is deleted at the
round's cleanup.

The derivation rule is E1's: every number comes from the round C advance pins
(`A` 10.4375, `V` 9.765625 kerned, space 3.96875, line height 19) and round D's
per-glyph raster table (`A` 11x12 `(0,-12)` Σ9983, `V` 10x12 `(0,-12)` Σ8878),
never from the engine's or the oracle's output. A value that cannot be derived
from the pins is measured on all three targets first (round C/D's procedure) and
then recorded as a constant, not pasted from either implementation. The filled
table goes in the slice C report and in the decisions entry.

## Gate

`tools/gradle build` once, after all three slices are staged: all three targets
of `kge-core`, `kge-test-support`, `kge-text-ttf` and `kge-font-roboto`, ktlint,
`buildSrcCheck` (which covers the new buildSrc code), and the metadata/kLIB
compilations. Test counts are read, not assumed (`#35`); `--rerun-tasks` is not
needed.

## Out of scope (round E2)

The GL fixture migration, the decal host and any `decal`/GPU work (E3); the
coverage texture; line breaking, rotation, text entry, SDF/MSDF, a size cap,
variable-font axes, a `kge-benchmark` text workload; any change to the harness's
comparison semantics, to E1's draw contract, or to production code in `kge-core`
and `kge-text-ttf`.
