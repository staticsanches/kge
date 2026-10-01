# `R6` round E2 — the module's golden harness and the text goldens: touch-point

**Status: decided with the owner, 2026-10-01.** Confirmed at this touch-point:
(1) the harness is **extracted** — a new KMP module `kge-test-support` carries
the matcher and the fixtures, the codegen moves to `buildSrc`, and `kge-core`
migrates onto it (single source of truth); (2) E2 ships the **golden harness
only** — the GL fixture (`installGl`, `RecordingGLService`, the handle factories)
stays in `kge-core` and moves in E3, when a decal test actually needs it;
(3) the module is named **`kge-test-support`**, the name that survives E3's GL
fixture and the test hosts it will carry.

**Date:** 2026-10-01. Touch-point for round **E2** of the text work
(`kge-text-ttf`). Rounds: A bitmap (core) → B scaffold → C face + shaping +
layout → D raster + glyph cache/atlas → E1 CPU blit + addons → **E2 the golden
harness + the text goldens** → E3 coverage texture + decal. The micro-plan is
written just-in-time on top of this; it is the test contract.

Material: the round E touch-point (`docs/plans/2026-09-29-r6-round-e-touchpoint.md`
— its §"Round E2" records what this round inherits; decisions 1–5 there are E1's
and are settled), the round E1 micro-plan
(`docs/plans/2026-09-29-kge-text-ttf-round-e1-microplan.md` — its pinned advance
table and per-glyph raster table are the derivation source), the golden harness
chunk (`docs/decisions/phase-1/28-golden-image-harness.md`), the harness
micro-plan (`docs/plans/2026-09-15-golden-image-microplan.md` — its provenance
procedure is what this round repeats), and chunks `31` (the `buildSrc` embedder
precedent) and `36`.

## What E2 is

A **test-infrastructure concept**, as `kge-core`'s harness was (chunk `28`). Two
deliverables, one concept:

1. The harness becomes reachable by more than one module: `kge-test-support`
   (shared matcher + fixtures) and `buildSrc` (the codegen).
2. `kge-text-ttf` gains its committed text goldens with independently derived
   provenance, and the tests that assert them on all three targets.

## Scope — ships

- New module `kge-test-support` (jvm/js/wasmJs) with the shared `GoldenImage`
  type, the `shouldMatchGolden` matcher, the `canvas` fixture and its own
  matcher tests, plus the two `harness/*` references those tests need.
- The codegen as `buildSrc` task classes plus a `Project.goldenImages(...)`
  registration helper, with the pure source renderer separated and unit-tested.
- `kge-core` migrated: local harness and inline codegen deleted, `commonTest`
  depends on the new module, the 23 existing golden references unchanged.
- `kge-text-ttf`: `commonTest` on the shared harness, `src/commonTest/golden/text/`
  with the committed references, and the golden test suite on all three targets.
- The `.tmp/` oracle that derives those references, and its per-case provenance
  in the micro-plan and the decisions entry.

Does **not** ship: the GL fixture migration (E3), any production change in
`kge-core` or `kge-text-ttf`, any new dependency, any decal/GPU path, and any
change to the harness's comparison semantics (chunk `28` decisions 4–12 stand).

## Established (not re-decided here)

- The harness's semantics are chunk `28`'s: exact raw-RGBA comparison, PNG is
  the only committed source of truth, the matcher resolves by name, a failure
  carries the summary plus the `WxH:<base64>` token under a 4096-cell budget,
  nothing in the test path rewrites a reference, references are never blessed
  from the engine's output.
- E1's test suite is free of engine-derived expectations by construction
  (its micro-plan's §"Pinned expectations"), which is what lets E2 add
  references without unlearning anything. E1's numbers stay the numeric pins;
  the goldens add exact composition on top.
- The shipped default instance of Roboto 3.015 (`kge-font-roboto`) is the face
  under test, at 16 and 32 px.
- The text path is CPU-only in this round: `TtfTextService.drawString` into a
  `Pixmap.Mutable`, composited by `BlitService` through the private tint mode.
  A golden cannot see `Pixel.Mode` beyond `Custom`; the mode rules stay E1's
  numeric pins.

## Decision 1 — the harness lives in `kge-test-support`, the codegen in `buildSrc`

Owner's call at this touch-point: extract. A second module needs the harness
(`kge-text-ttf` cannot see `kge-core`'s test source set, and KMP has no test-
fixtures configuration), and the alternative — reproducing it module-locally —
is the maintenance hazard the extraction exists to remove: two matchers, two
codegens, two `MAX_TOKEN_CELLS` rules, drifting silently.

The split of responsibilities:

- **`kge-test-support`** (new module, `commonMain`) carries what test *code*
  needs: `GoldenImage`, `Pixmap.shouldMatchGolden(images, name)`, `canvas`.
  It depends on `kge-core` as `api` — its public signatures name `Pixmap` and
  `Sprite`. Its own `commonTest` carries the matcher's tests and the
  `harness/smoke` / `harness/oversized` references, so the harness is pinned by
  its own suite, not only by its first consumer.
- **`buildSrc`** carries what the *build* needs: the PNG-decoding generator, the
  token renderer, and the registration helper (decision 2). This is the chunk
  `31` precedent — `buildSrc` is outside the main task graph, and the root
  `check` already runs `buildSrcCheck`, so the gate still covers it (the round
  must confirm the two-command gate holds).
- **Package**: the shared code is
  `dev.staticsanches.kge.testsupport.golden` — the module is its own artifact and
  a split package across artifacts is avoided. The generated per-module accessor
  keeps `dev.staticsanches.kge.golden` (module-local data, one object per
  module), so every current call site keeps its meaning.
- **`GoldenImage` becomes a shared type** instead of codegen output; the
  generated file imports it. Consequence: the shared module must exist *before*
  the codegen is switched over, which fixes the implementation order (slice A
  before B).

Rejected — **reproduce the harness inside `kge-text-ttf`**: no `kge-core` change
and smaller, but it duplicates the matcher, the codegen and the token contract,
and it leaves E3's decal test without a fixture host, which is half the reason
the module exists.

Rejected — **keep the harness in `kge-core` and depend on its test classes**:
KMP does not publish a test source set as a consumable variant; making it work
would mean inventing a test-fixtures configuration of exactly the kind this
module replaces.

Rejected — **make every call site pass the map explicitly**
(`surface.shouldMatchGolden(GoldenImages.byName, "line/filled")`): it churns ~26
existing assertions for no behavior, and chunk `28` decision 6 recorded the
one-argument matcher as the harness's ergonomics.

**Migration contract for `kge-core`:** the golden *tests* stay in `kge-core`
(they test core raster behavior); only the harness moves. Its suite must pass
unchanged — a diff to any `kge-core` golden expectation during migration is a
finding, not a fix, and the round's verification plan re-runs the whole core
suite as the regression guard. `kge-core`'s production API does not change.

## Decision 2 — the codegen: a pure renderer in `buildSrc`, wired per module

Mirrors `embedResources` (chunk `31`):

- `buildSrc/src/main/kotlin/GoldenImages.kt` — the pure part:
  `GoldenImageSpec(name, width, height, rgbaBase64)` and
  `renderGoldenImages(packageName, images)`, which emits the file below. Pure so
  it is unit-testable in `buildSrc`'s own suite (red → green without a Gradle
  functional test).
- `buildSrc/src/main/kotlin/GenerateGoldenImagesTask.kt` — the PNG walk with
  ImageIO, the decodability/positive-dimension/`MAX_DIMENSION` guards, the
  duplicate-name check, and the name = path-relative-to-`golden/` rule — all
  unchanged from chunk `28`; plus `GoldenActualToPngTask` and `GoldenRgba` moved
  verbatim, and `fun Project.goldenImages(inputDir, outputDir, packageName)`
  registering both tasks and returning the generator provider.
- The emitted file:

```kotlin
package dev.staticsanches.kge.golden

import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.testsupport.golden.GoldenImage
import dev.staticsanches.kge.testsupport.golden.shouldMatchGolden

object GoldenImages {
    val byName: Map<String, GoldenImage> = listOf(
        GoldenImage("line/filled", 5, 3, "…"),
    ).associateBy { it.name }
}

/** Asserts this surface against this module's golden [name]. */
fun Pixmap.shouldMatchGolden(name: String) = this.shouldMatchGolden(GoldenImages.byName, name)
```

- The binder is **generated**, not handwritten per module: it is the one line
  that binds a module's data to the shared matcher, and generating it means a
  module adopting the harness cannot forget it. Rejected: a handwritten binding
  file per consumer (boilerplate that can drift) and a runtime registry that the
  matcher resolves through (initialization order on JS/wasmJs would decide
  whether a module's goldens are visible — an implicit dependency on module
  init, and untestable in the direction that matters).
- Each consuming module wires the output with
  `commonTest.kotlin.srcDir(generateGoldenImages.flatMap { it.outputDir })`, as
  `kge-core` does today; ktlint keeps excluding `/build/generated/`.

## Decision 3 — the oracle: an independent `.tmp/` driver

References are derived by a throwaway JVM driver under `.tmp/`, never blessed
from the engine's output (chunk `28` decision 2; the round E touch-point's
oracle rule). The driver:

- uses the raw LWJGL HarfBuzz and FreeType bindings directly — it must **not**
  import `Font`, `TtfTextService`, `GlyphAtlas`, `TextDraw` or any other
  `kge-text-ttf` class; the orchestrator verifies this statically on the oracle
  source before the references are copied in;
- reads the committed font source
  (`kge-font-roboto/fonts/roboto/Roboto[wdth,wght].ttf`) and the default
  instance, and mirrors round D's raster parameters (the same FreeType pixel
  mode and load flags the module uses) — parity of the *raster* is round D's
  pinned result, and re-deriving a second rasterizer is not what this round is
  for;
- re-implements the **placement and composition** rules independently from this
  document set: the `'\n'` line box with `baseline = lineTop + ascender`, the
  `'\t'` tab stop (`(floor(penX / step) + 1) * step`, `step = tabSizeInSpaces *
  spaceAdvance`), `dest = (round(penX + offset.x) + bearing.x, round(lineTop +
  ascender - offset.y) + bearing.y)`, the integer `scale` block replication,
  the coverage-weighted tint with `alpha = tint.a * coverage / 255`, src-over
  compositing, and target-bounds clipping;
- writes one PNG per case to `.tmp/`, which the round then copies into
  `kge-text-ttf/src/commonTest/golden/text/`.

What "independent" means here, recorded so it is not overclaimed: the oracle is
independent **code** for the walk and the composite — the part E2's goldens add
over E1's numeric pins — while the raster library and its parameters are
necessarily the module's own (round D). The goldens are therefore a second
opinion on placement and composition, not on FreeType's coverage bytes.

The driver is scratch: it is deleted in the round's cleanup step, and the
provenance survives in the micro-plan and the decisions entry (chunk `28`'s
procedure). If a later round needs to re-derive a reference, the case's entry
carries the string, the surface, the tint, the destination pre-fill and the
derived expectation, so the driver is reproducible from the record.

## Decision 4 — the case set

Eleven cases under `text/`, all on the shipped Roboto default instance at 16 px
except where noted. Surface sizes are derived from the round C/D advance table
(E1 micro-plan §"Pinned expectations": `A` 10.4375, `V` kerned 9.765625, space
3.96875, line height 19) and stay at or below 48x32, so a reference costs a few
kilobytes of generated base64 per module. Every case's expected ink box and
Σ alpha are derived by hand from that table and round D's per-glyph raster table
(`A` 11x12 `(0,-12)` Σ9983, `V` 10x12 `(0,-12)` Σ8878, `q` …, the mark pair),
exactly as E1 did — the oracle is then *checked against* those derivations, not
the other way round.

| case | draw | what the reference pins |
|---|---|---|
| `text/plain` | `"Ao"` at (2, 2), opaque white on a cleared target | the composed multi-glyph draw and the destination offset |
| `text/kerned` | `"AV"` at (2, 2) | kerning in the reference, not only in the size |
| `text/tab-stop` | `"A\tB"` at (2, 2) | the tab stop: `B` at `x + 15.875`, not at `A` + a fixed step |
| `text/multiline` | `"A\nB"` at (2, 2) | the line box: the second line exactly one line height (19) lower |
| `text/scale-2` | `"A"` at (2, 2), `scale = 2` | 2x2 block replication and the doubled box |
| `text/tint-opaque` | `"A"` at (2, 2), opaque non-white tint | the coverage-weighted tint color, cell by cell |
| `text/tint-translucent` | `"A"` at (2, 2), `alpha = 128` tint | `tint.a * coverage / 255` over a transparent target |
| `text/prefilled-opaque` | `"A"` at (2, 2) over a fully opaque pattern | src-over against an opaque destination, and no-ink cells keeping it exactly |
| `text/prefilled-translucent` | `"A"` at (2, 2) over a translucent pattern | src-over where both sides carry alpha |
| `text/clipped` | `"A"` at (-4, -6) on a 9x9 target | per-pixel target clipping, no throw |
| `text/mark` | `"q\u0323"` at (2, 6) on a taller target | the decomposed below-mark's offset and the y-negation (E1's falsifying case) |

The tint and the destination pre-fill are chosen so that a sign error, an
unrounded placement, a dropped coverage fold or an overwrite-instead-of-composite
each change at least one cell of at least one reference. `Pixel.Mode` is
`Normal` throughout (the addon's default path); E1 pins the `Custom` tap.

Rejected — **goldens of the whole E1 case set**: the numeric pins already cover
scale/no-op/clip/close semantics cell by cell, and a reference per E1 test would
multiply committed data without adding an independent opinion.

## Decision 5 — visibility and the extension contract

- `kge-test-support` is a test-support artifact with no production consumer.
  What it exposes — `GoldenImage`, `shouldMatchGolden`, `canvas` — is reached by
  other modules' `commonTest` source sets, which is what justifies its `public`
  surface; nothing in it is widened for convenience.
- In the consuming modules, the generated `GoldenImages` object and the
  generated binder stay module-local; no new `public` API appears in `kge-core`
  or `kge-text-ttf` production code.
- Test source sets keep the `private`-first ladder with no `internal` (chunk
  `28`'s cross-cutting convention); the moved matcher tests are re-checked
  against it rather than moved verbatim.
- The module builds the same three targets as its consumers, with the
  no-sandbox headless Chrome launcher (CI runs on Ubuntu 24.04, where the
  sandbox is blocked); it needs no WebGL, no SwiftShader and no karma shim.

## Verification plan (the micro-plan's contract)

1. **Build.** `tools/gradle build` green on all three targets, `buildSrcCheck`
   included; the `#35` zero-test guard makes each suite's reported counts the
   evidence that the browser runs executed. No new dependency.
2. **Codegen.** `buildSrc`'s own suite pins `renderGoldenImages`: deterministic
   ordering, the duplicate-name failure, the emitted package/imports, and the
   generated directory being wired into the consuming module's `commonTest`.
3. **Shared harness.** `kge-test-support`'s suite carries the matcher's seven
   cases (match, one-cell, two-cell, dimension, unknown name, alpha-only,
   over-budget token omission) and the accessor smoke test, green on jvm + js +
   wasmJs.
4. **`kge-core` non-regression.** The full core suite green with the migrated
   harness, every existing golden expectation untouched, and the file diff in the
   round touching no expectation.
5. **Text goldens.** The eleven cases green on jvm + js + wasmJs, in
   `commonTest`, on the committed PNGs.
6. **Oracle independence.** The oracle source is checked to import no module
   class; each reference's derivation (the hand-computed ink box and Σ alpha) is
   recorded and agrees with the PNG the oracle wrote, so the agreement between
   engine, oracle and derivation is a three-way check rather than a blessing.
7. **Provenance.** The micro-plan and the decisions entry list, per case, the
   string, the surface, the tint, the destination pre-fill, the derivation, and
   the oracle run that produced the file.
8. **Gate, review, decisions entry, commit** — the round's close, below.

## Carried to the micro-plan

- The exact surface size of each case and its hand-derived ink box and Σ alpha.
- The oracle's build shape (the throwaway Gradle project under `.tmp/`, its
  dependency set, how it reads the committed font) and the exact FreeType/HarfBuzz
  call sequence it mirrors from round D.
- The `buildSrc` helper's name and parameter list, and the emitted binder's final
  KDoc line.
- The names and packages of the moved test files, and the `kge-core` import
  churn the `canvas` move causes.
- Which integration step proves the `kge-core` → `kge-test-support` → `kge-core`
  configuration-level dependency resolves (a project cycle at the task level
  would surface here first).

## Out of scope (E2)

The GL fixture migration (`installGl`, `RecordingGLService`, the handle
factories) and any decal test host — E3's; the coverage texture, `drawStringDecal*`
and the GPU path — E3's; line breaking, rotation, text entry, SDF/MSDF, a size
cap, variable-font axes and a text workload in `kge-benchmark` — later or
never; any change to the harness's comparison semantics or to E1's draw
contract; and any production change in `kge-core` or `kge-text-ttf`.
