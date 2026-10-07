# Text API unification — round U4 micro-plan (legacy TTF surface retirement)

Base: `d91d45e` (round U3). Touch-point:
`docs/plans/2026-10-05-text-api-unification-u4-touchpoint.md`. Gate:
`tools/gradle build`, once, before the two review axes.

## What this round delivers

The module publishes exactly `KGETtfFontService` + `TtfFontAddon`; every pin the
legacy surface carried either survives on the unified path, moves to the
module's `internal` seam, or is deleted with a recorded reason. The benchmark's
TTF cells run on the unified path. No drawing behavior changes and no golden
reference moves.

## Success criteria

1. `kge-text-ttf`'s `commonMain`/`commonTest` name none of `Font`,
   `TtfTextService`, `TtfDrawStringAddon` or `*Prop`; the module's public
   declarations are `KGETtfFontService`, `TtfFontAddon` and `KGEFont`'s own.
2. The pin inventory below exists and passes on all three targets.
3. The loader seam has an extension-contract proof.
4. The benchmark's `text-ttf-region`/`text-ttf-full` cells and
   `TtfCarrierUploadTest` run through `TtfFontAddon` + `TextAddon`.
5. No `text/*.png` reference changes.
6. Gate green with the counts in "Expected counts".

## Pin inventory (the contract)

Every item is one `test(...)`. Items 1–17 are additions; 18 is an addition at
the internal seam; the rest of the surviving coverage is the existing unified
suites unchanged.

**`TtfFontMeasureTest`**

1. `the 32 px box and the widest line on either side` — `"A"`@32px is
   `(21, 38)`; `"AAAA\nA"` and `"A\nAAAA"` are `(42, 38)`.
2. `a trailing space advances the pen and kerning narrows the pair` — `"A "` is
   `(15, 19)`, `"AV"` is `(20, 19)`, `"AA"` is `(21, 19)`.
3. `blank and empty draws paint nothing while a trailing space changes no
   pixel` — `" "` and `""` leave alpha 0; `"A "` is pixel-identical to `"A"`
   with alpha 9983.
4. `a non-positive tab size fails fast on measure and on draw`.
5. `measuring never rasterizes` — with the recording GL installed,
   `measureText` issues no GL call and allocates no chart buffer.

**`TtfFontDrawTest`**

6. `the tab grid is measured from the line origin, not the draw origin` — `"A\tB"`
   drawn at `(100, 0)` is the `(0, 0)` draw shifted by `(100, 0)`.
7. `the scale anchors on the line box, not the draw origin` — `"A"` at `(2, 2)`
   with scale 2 inks `(2, 8)..(23, 31)`, alpha 39932.
8. `scaled text advances by the scaled line height and the scaled advance` —
   `"A\nA"` at scale 2 puts line 2 at `(2, 46)..(23, 69)` (alpha 39932); `"AA"`
   at scale 2 spans to x 42.
9. `a decomposed above mark keeps its shaped offset` — `"x"` against `"x\u0301"`:
   the mark cells are `(2, 3)..(6, 4)`, alpha 736, strictly above the base.
10. `a below mark's negated offset lands under the base` — `"q"` against
    `"q\u0323"`: cells `(6, 19)..(8, 20)`, alpha 518, strictly below.

**`TtfFontDecalTest`**

11. `two leases at different sizes draw from their own atlas and advance` —
    one face, a 16 px and a 32 px lease; the 32 px `"A"` anchors at `(2, 9.6875)`
    and its `"AA"` second glyph at `22.875`.
12. `updating a collected instance's decal re-specifies the chart as RGBA` —
    `Decal.update()` re-issues `texImage2D` with `GL.RGBA` while the coverage
    swizzle stays armed (the legacy hazard pin, kept).

**`TtfFontAddonTest`**

13. `the host's tab size drives the measurement` — tab 1 host measures `"A\tB"`
    as `(22, 19)`, the default host as `(26, 19)`.
14. `the host's pixel mode drives a real draw` — a `Custom` mode paints exactly
    the ink cells, the rest stays cleared.
15. `omitting the color and scale queues the same instance as the explicit white
    and unit scale`.
16. `the addon forwards a non-default color and scale at the lease's size` — a
    32 px lease with a tint and a non-unit scale.

**new `KGETtfFontServiceTest`**

17. `an overriding decorator is the loader the returned family came from` — a
    delegating decorator over `KGETtfFontService.original` whose construction is
    observable in the family the caller receives; restores the original in
    `finally` (principle 1's extension-contract proof).

**`ShapingTest` (internal seam)**

18. `a combining mark carries its shaped offset and zero advance` — `"x\u0301"`
    glyph 2 offset `(0.453125, -0.078125)`; `"q\u0323"` glyph 2 offset
    `(2.734375, -3.171875)`; both with zero advance.

## Ownership of the surviving legacy cases

These are the only fates; nothing else is dropped.

| legacy case | fate |
|---|---|
| box, newline-width, tab-stop, "A"@32, trailing space, kerning | inventory 1–2 |
| `getTextSize("")` was `(0, 19)` | superseded by U3 Decision 8 (`(0, 0)`) |
| blank/empty draw, non-positive tab, measure-without-rasterize | inventory 3–5 |
| tab grid from the line origin | inventory 6 |
| scale anchor, scaled newline, scaled advance | inventory 7–8 |
| decomposed/below mark pixels and offsets | inventory 9, 10, 18 |
| composite/tint/Custom/clip/close, `Int2D` overload, scale no-op | existing `TtfFontDrawTest`, `TtfFontAddonTest` |
| placed atlas entry (`11x12`, bearing `(0, -12)`) | `GlyphRasterTest`, `GlyphAtlasTest` |
| repeat draw reuses the chart | `GlyphAtlasTest`'s cache case + one atlas per lease |
| golden scenes (11) | `TtfFontGoldenTest`, unchanged |
| decal worked example, blank, non-uniform scale, mode/structure/viewport/tint, order, tab | existing `TtfFontDecalTest` |
| per-size atlas and the `Decal.update()` hazard | inventory 11–12 |
| addon tab default, null target, forwarding, render-step drain | existing `TtfFontAddonTest` |
| host tab size, host pixel mode, default-vs-explicit, non-default forwarding | inventory 13–16 |
| loader scope ownership, two families, base64 ≡ bytes, rejected loads | existing `TtfFontAddonTest`, `TtfFontFamilyTest` |
| family/lease close, inert values, sibling lease, payload buffers | existing `TtfFontFamilyTest`, `TtfFontMeasureTest`, `PayloadAllocationTest`, `WebNativeFaceTest` |
| leak identity | deleted; U6 owns it (touch-point Decision 4) |

## Steps

**Step 1 — the pin inventory's public items (test-only).** Add 1–17 to the named
specs. Each must be green against the unified path while the legacy surface
still exists: that is the evidence the pin does not depend on what is about to
be deleted. No red is available in this step and none may be fabricated.

**Step 2 — the internal anchor (test-only).** Promote `GlyphRasterTest`'s
`withRobotoFace` (`TtfPayload` + `createNativeFace` + `closeNativeFace`) into
`RobotoFixture.kt` as the shared fixture, add the atlas-side companion the decal
oracle needs, and move `ShapingTest`, `MetricsTest`, `GlyphAtlasGpuTest`'s six
`Font`-anchored cases, `CoverageTextureSmoke`, `WebNativeFaceTest` and
`DecalDrawTestFixtures` onto it. Add inventory 18. Green throughout.

**Step 3 — the benchmark (production + test).** `CarrierProbeEngine` loads
through `TtfFontAddon` and draws through `TextAddon`; `FpsBenchmarkEngine`
assigns the configured lease to `textFont` for the two TTF cells and drops
`TtfTextSceneTarget`/`renderTtfTextScene`/`SCENE_TTF_TEXT_SIZE`; `Scene.kt` loses
the TTF scene. **This step has a real red:** `TtfCarrierUploadTest`'s per-frame
`texSubImage2D` counts must be unchanged by the migration — if the unified path
uploads differently, that is a finding for this round, not a re-pin.

**Step 4 — the deletion.** Delete `Font.kt`, `TtfTextService.kt`,
`TtfDrawStringAddon.kt`, the four adapters in `TextDraw.kt`, and the specs
`TextDrawTest`, `TextGoldenTest`, `TtfTextServiceTest`,
`TtfTextServiceDecalTest`, `TtfDrawStringAddonTest`, `FontLoadTest`,
`FontResourceTest`, `FontLeakReportTest`; delete `ShapedRun` and its
`TextLayoutTest` case; narrow `ShapedGlyph`/`TextMetrics` to `internal`; move
`wrapNativeFace`/`toCodePoints` beside `NativeFace`. **The red is the compiler:**
the unresolved-reference list is the step's worklist, and the step is green when
it is empty and the suite passes. Any reference the plan did not inventory is a
plan defect — report it as a blocker rather than widening the deletion.

## Expected counts

`kge-text-ttf`: **148 jvm / 150 js / 150 wasmJs** — 70 legacy `commonTest` cases
plus one `TextLayoutTest` case deleted, 18 pins added. `kge-benchmark`: 21 jvm /
22 wasmJs, unchanged. `kge-core`, `kge-font-roboto` and `kge-test-support`
unchanged. The counts are checked against the named inventory, not the number
alone; a deviation is a finding (chunk `35`: a suite reporting fewer tests than
it has still exits 0).

## Dispatch

One `tdd-developer` per step, in order, none overlapping; the orchestrator runs
`tools/gradle build` once after step 4 and before the reviews. The developer
reports, per step, the exact commands and the observed counts, and returns a
blocker instead of editing this plan or a settled decision.
