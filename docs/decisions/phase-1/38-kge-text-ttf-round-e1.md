## 2026-09-29 — `kge-text-ttf` round E1: the CPU text draw

Round E1 of the text work: the pen walk, the coverage blit into a
`Pixmap.Mutable`, `getTextSize` and the olc-shaped addon. Material: the round E
touch-point (`docs/plans/2026-09-29-r6-round-e-touchpoint.md`) and the E1
micro-plan (`docs/plans/2026-09-29-kge-text-ttf-round-e1-microplan.md`). Rounds:
A bitmap (core) → B scaffold → C face + shaping + layout → D raster + atlas →
**E1 CPU blit + addons** → E2 golden harness + text goldens → E3 coverage texture
+ decal.

### Round split: the harness takes E2, the GPU path moves to E3

The owner's 2026-09-24 direction split `E` into E1 (CPU blit + addons) and E2
(coverage texture + decal); this round's touch-point expanded it to three, on the
owner's decision, because the golden harness is a test-infrastructure concept of
its own — `kge-core`'s was a separate round too (chunk `28`) — and its oracle has
to be authored independently, case by case. The harness needs the codegen and the
matcher, which today live only in `kge-core` (the tasks inline in its build
script, the matcher in its test source set), and the same shared test-support
module that carries them also removes the fixture blockage E3 has anyway: a decal
test needs a `LayerStack`, hence GL, and the module can see neither `installGl`
nor `RecordingGLService`. Consequence recorded for E1: it commits no goldens, and
its expectations are hand-derived from round C's advance pins and round D's
raster table rather than pasted from the engine's own output.

### Shipped

- Public: `TtfTextService.getTextSize` and `drawString` (the raw `x`/`y` form and
  the `Int2D` form), and `TtfDrawStringAddon` over `HasDrawTarget` and
  `HasDrawModes`, with olc's defaults (`tabSizeInSpaces` 4, `color = WHITE`,
  `scale = 1`).
- Internal (`TextDraw.kt`): `walkText` (the shared pen walk), `measureText`,
  `drawText`, and a file-private `Pixel.Mode.Custom` coverage tint; `GlyphAtlas`
  and `AtlasGlyph` stay as round D left them.
- Tests: `TextDrawTest` (18) and `TtfDrawStringAddonTest` (4) on jvm, js and
  wasmJs; `TtfTextServiceTest`'s decorator follows the widened interface
  (`by TtfTextService.original`). No `kge-core`, build or dependency change.

### Decisions

1. **One walk drives measuring and drawing**, so the size and the draw can never
   disagree about a tab stop or a line: lines are split at `'\n'` and segments at
   `'\t'` and only the segments are shaped; the pen is float and keeps HarfBuzz's
   kerning; the offset never advances the pen.
2. **`y` is the line-box top**, not the baseline: `baseline = lineTop +
   ascender`, `lineHeight = ceil(ascender - descender + lineGap)` (19 at 16 px
   and 38 at 32 px on the shipped Roboto). Without it `drawString(0, 0, …)` would
   draw above the target, since `bearing.y` is negative by construction.
3. **A tab is a tab stop, not a fixed advance** (owner decision): `penX =
   (floor(penX / step) + 1) * step`, `step = tabSizeInSpaces ×` the isolated
   U+0020 advance, with the grid anchored at the **line origin** — the internal
   pen is line-relative and the reported pen is `x + penX`, so a stop is
   `x + k * step` (pinned by the `x = 100` case: 115.875, not the target-grid
   111.125). **Divergence from olc, recorded:** olc always adds
   `nTabSizeInSpaces` 8-pixel cells — a step with no stops, which only exists
   because its font is a fixed grid; the cell of a proportional face is its
   space.
4. **The blit composites by coverage**: the coverage byte folds into the tint's
   alpha and the pixel is composited source-over. Only `Pixel.Mode.Custom` is
   preserved (it receives the coverage-weighted pixel) and it is **never tapped on
   a no-ink cell** — olc checks the sheet's ink before calling its draw seam, and
   a mode that ignores alpha would otherwise paint the glyph's whole bounding box;
   `Normal`, `Mask` and `Alpha` all resolve to coverage compositing. **Divergence
   from olc/C7, recorded:** olc resolves an opaque colour to `Mask` and a
   translucent one to `Alpha`, which is a binary mask for a bitmap sheet but would
   throw the antialiasing away here.
5. **The blit reuses `BlitService.blitRegion`** with a file-private
   `Pixel.Mode.Custom` tint, so scale, clipping and out-of-bounds behavior stay
   the engine's tested ones and text draws on the same overridable seam as every
   other raster draw. The `fontDevelopment`/`C7` per-pixel `Rasterizer.draw` walk
   is rejected as a re-derivation of those rules.
6. **`sizePx` is required and has no default** — one face, no fixed cell, so any
   default would be an unjustified constant of the kind the atlas research
   rejected for `512`. There are no `…Prop` variants (one face has one metric
   set) and `getTextSize` takes no `scale` (olc/C7 parity); `scale <= 0` is a
   no-op that short-circuits before the argument checks (C7's rule).
7. **The addon carries no `HasWindow`, `HasLayers` or `HasResourceScope`** —
   the first two exist for E3's decal viewport and instance queue, and the third
   has no consumer at all while the draws take the `Font` directly (round D
   decision 6). That is also what keeps the addon's test host free of
   `LayerStack` and GL, so the CPU path is pinned on all three targets.

### Findings — the fixture, and a test that could not fail

- **Composition (fixture).** HarfBuzz composes a base and a canonical mark into
  the precomposed glyph whenever the font carries it: `e`+U+0301 → glyph 703 at
  offset `(0, 0)`, and the same for `e`+U+0323, `a`+U+0301, `e`+U+0302,
  `e`+U+0304, `A`+U+030A and `n`+U+0303. The micro-plan's offset case,
  `"e\u0301"`, therefore had no mark to position at all.
- **Test strength.** The replacement `"x\u0301"` cannot falsify the y negation on
  its own: its offset is `-0.078125`, so `round(14.84375 - (-0.078125))` and
  `round(14.84375 + (-0.078125))` are both 15 — an implementation that *added*
  the offset instead of subtracting it passed the whole suite. `"q\u0323"`'s
  offset, `-3.171875`, separates 19 from 13, so the round pins both:
  `"x\u0301"` for the above-mark direction, the x offset and the zero advance,
  `"q\u0323"` for the sign and the below direction. A test that cannot fail is
  worse than a missing one, so the case set is the record.
- The `"q\u0323"` dot lands at row 19 of a 19-row pen box: the expected
  consequence of pen-extent sizing (olc's rule), not a defect.

### Verification

- Gate `tools/gradle build` green over the final tree (`check` + `assemble`,
  every target's tests, ktlint, metadata/kLIB).
- Module counts, read from the post-delta run's XMLs: **jvm 70 / js 73 / wasmJs
  73, 0 failures** (+26 per target over round D); `TextDrawTest` 22 and
  `TtfDrawStringAddonTest` 4 on each.
- Pinned on all three targets and identical: `"A"` box 11x19, `"AAAA"` 42 and the
  tab stop 47.625; `A` Σ9983, `x` 8x9 `(0,-9)` Σ6788, the acute 169 5x2
  `(-6,-12)` Σ736 at offset `(0.453125, -0.078125)` → dest `(2, 3)`, `q` 8x12
  `(0,-9)` Σ10017, the dot below 173 3x2 `(-6,1)` Σ518 at `(2.734375,
  -3.171875)` → dest `(6, 19)`, scale-2 Σ39932.
- **Process note.** Micro-plan steps 3–6 produced no independent red: those
  behaviors arrived with step 2's blit, so their tests never saw a failing
  implementation. Sensitivity was shown by perturbation instead — flipping the
  offset sign fails only the below-mark test, dropping `* scale` fails the scale
  test, and weakening the `scale <= 0` guard fails the no-op test. Recorded
  rather than presented as strict TDD.
- **Cross-cutting gate fact (supersedes nothing; extends chunk `35`).** A Kotlin
  compiler ICE in a `compileTestDevelopmentExecutableKotlin*` task fails the
  build *before* the test task runs, so the previous run's JUnit XML survives and
  the suite can be read as green while it never executed. Observed twice here:
  the first `tools/gradle build` of this round ICEd the js and wasmJs test
  executables (the identical task then compiled clean), and the wasmJs
  test-executable ICE (`NoSuchElementException` in the incremental
  `DefinedDeclarationsResolver` after a small test edit) was reproduced by the
  developer. The `#35` rule — read the reported counts — is therefore not enough
  on its own: at the gate, compare the XML mtime against the run or force the
  target with `--rerun-tasks`. The gate for this round was re-run and its XMLs
  are newer than the last source edit.
- **Residual, for E2/E3:** no golden of a composed string here (the harness is
  E2's); the y negation's falsification rests on `"q\u0323"` alone; and there is
  still no size cap for a glyph box larger than a chart (round D's fail-fast).

### Review round 1 and the delta

Both axes FAILed over tree `68b3049d…`, each with the same Important finding and
four to six Minors, all test-only or documentary — no production defect in the
shipped behavior.

- **Standards (0 Critical / 1 Important / 6 Minor) and Spec (0 Critical / 1
  Important / 4 Minor), the same Important: the src-over composite was
  unfalsifiable.** Every target in both suites was cleared to transparent, so
  only `srcOver`'s `oldPixel.a == 0` return was pinned; replacing the composite
  with `return this`, or writing a zero-alpha pixel for a no-ink cell, passed all
  22 new tests. The delta draws over a **pre-filled** destination (opaque and
  translucent) and asserts the composite per cell plus a no-ink cell keeping the
  destination exactly — the rule the touch-point always claimed and no test
  exercised.
- **Spec, `Custom` on no-ink cells (Minor, fixed rather than recorded):** with
  `blitRegion` the mode was tapped for every cell of the glyph box, where olc taps
  only where the sheet has ink. Coverage 0 now returns the stored pixel without
  reaching the caller's mode, which is both olc parity and one line cheaper; the
  two mode tests follow.
- **Spec, `Int2D` overload untested (Minor, the `C7` repeat):** pinned by drawing
  through both forms and comparing the surfaces.
- **Standards, `measureText` allocated a `Float2D` per glyph its empty sink
  discarded (Minor, the `C7` finding class):** the sink now takes `(penX, penY)`
  as `Float`s, so measuring allocates nothing per glyph.
- **Both axes, the `draw` path was only exercised on line 0:** a `"\n"` draw now
  pins the second line one line height lower.
- **Standards, remaining Minors:** the addon's `color`/`scale` never received
  non-defaults (now forwarded and asserted), the recorded `Custom` cells were
  compared in `BlitService`'s iteration order (now a multiset), `emptySprite()`
  cleared an allocated sprite unguarded (now `applyClosingIfFailed`), and three
  tests lacked the file's blank-line separator.
- **Both axes, documentary:** the touch-point and micro-plan said the walk and the
  blit stay file-`private` while the shipped entry points are `internal` (the
  widening is justified — the service default, the tests and E3's decal path —
  so the wording was corrected, not the code), and `HasResourceScope` was on the
  addon with no consumer (removed).
- **The E2 case list** gains the pre-filled-destination draw, the one composition
  fact the CPU tests can only pin numerically.
- **Mutation evidence, not behavior-red.** The composite cases were correct
  before the delta, so their tests could not be red against them; each was
  falsified by a deliberate mutation instead — `srcOver = { this }` fails three,
  a zero-alpha write for a no-ink cell fails two, `lineTop += 0f` fails the
  newline case, an `Int2D` overload ignoring its position fails, and hardcoding
  the addon's `color`/`scale` fails. Recorded, as in the first submission, rather
  than claimed as red.
- **A hand-derived literal was rejected as brittle:** a partial-cell composite
  written as `Pixel.rgba(c, c, 255)` failed by one on the blue channel
  (`#5757FEFF` against `#5757FFFF`) because the production `outAlpha` is a Float
  that is not exactly 1; the test compares the spec formula instead, plus
  hand-derived invariants (`r == g`, `b > r`, `a == 255`). The literal would have
  pinned a truncation artifact, which is the class of expectation E2's goldens
  must not inherit.

Verification of the delta, and the tree the round-2 reports must carry: the gate
is re-run over the delta and its XMLs are checked against the last source edit;
the marker names one of the two round-2 reports, and both must carry the staged
tree's hash.
