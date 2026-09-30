# `R6` round E — CPU text drawing: touch-point

**Status: decided with the owner, 2026-09-29.** Confirmed at this touch-point:
(1) `E` becomes three rounds — E1 (this round), E2 the module's golden harness
and the text goldens, E3 the coverage texture and the decal; (2) E1's draw
contract is decision 1's; (3) coverage compositing with only `Custom` preserved
is decision 3's; (4) a tab is a **tab stop on the pen**, not olc's fixed advance
(decision 2); (5) E1's addon carries no `HasWindow`/`HasLayers`, which is what
keeps its test host GL-free (decision 1). The 2026-09-24 roadmap note's "E1/E2"
split is expanded here: its E2 is this document's E3, with the golden harness
taking the E2 slot.

**Date:** 2026-09-29. Touch-point for round **E** of the text work
(`kge-text-ttf`), the module's fifth round. Rounds: A bitmap (core) → B scaffold
→ C face + shaping + layout → D FreeType raster + glyph cache/atlas → **E1 CPU
blit + addons** → **E2 golden harness + text goldens** → **E3 coverage texture +
decal**. The micro-plan is written just-in-time on top of this; it is the test
contract.

Material: `docs/plans/2026-09-16-r6-text-touchpoint.md` (stack, scope, and the
rule that olc parity here is *shape only*), the round D touch-point
(`docs/plans/2026-09-22-r6-round-d-touchpoint.md` — decisions 1, 4 and 6 are what
E1 builds on; do not re-decide), the glyph-atlas sizing research
(`docs/plans/2026-09-24-glyph-atlas-sizing-research.md`, whose §6 measurements
belong to E2) and decisions chunks `29`, `34`, `36`.

## Split (owner, 2026-09-29; expands the 2026-09-24 direction)

- **E1 — this round.** The CPU path end to end: the pen walk, the coverage blit
  into a `Pixmap.Mutable`, `getTextSize`, the service draws, and the olc-shaped
  addon's CPU variants.
- **E2 — the module's golden harness and the text goldens.** A test-infrastructure
  round: the codegen and the matcher the harness needs, and the committed text
  references with their independent provenance.
- **E3 — the GPU path.** The one-channel coverage texture at the upload seam
  (`GL_R8` + swizzle, region update, no `pixelStorei`), the per-glyph
  `DecalInstance`s and the addon's decal variants.
- Rationale: E1 is module-only and ships whole (an antialiased text draw a
  consumer can use); the golden harness is a concept of its own — `kge-core`'s was
  a separate round too (chunk `28`) — and its oracle has to be authored
  independently and its provenance recorded case by case; E3 forks on a core-side
  seam decision plus the measurements the research demands (region vs full
  upload, `GL_MAX_TEXTURE_SIZE`).
- Rationale for E2's place: the harness is shared infrastructure, and the same
  test-support module that carries the matcher also removes the fixture blockage
  E3 has anyway (a decal test needs a `LayerStack`, hence GL, and today the
  module can see neither `installGl` nor `RecordingGLService`).
- Rejected: shipping E as one round — a module-only feature, a test
  infrastructure concept and a core renderer seam change in one diff.
- Rejected: E1 without the addon (service-only) — the olc surface is the
  consumer's entry point, and it needs no GL to be pinned (decision 1).
- Rejected: goldens inside E1, whether by duplicating `kge-core`'s harness in the
  module or by extracting it mid-feature — either puts two concepts in one round
  and risks the three-round review cap, and a duplicated harness is the exact
  maintenance hazard the extraction is for.

**Consequence for E1's verification:** E1 pins placement, coverage and semantics
numerically (see the plan below); it does **not** commit goldens. Any expectation
E1 writes must come from the font's pinned raster table and the documented rules —
never from the engine's own output — so that E2 can add references without having
to unlearn an engine-derived expectation.

## Scope (E1)

Ships: `TtfTextService.getTextSize`/`drawString` (CPU), the pen walk, the
coverage blit, and `TtfDrawStringAddon`. Does **not** ship: any decal/GPU path,
the coverage texture, `drawStringDecal*`, line breaking, rotation or text entry.

## Established (not re-decided here)

- Stack HarfBuzz (shaping) + FreeType (rasterization), Latin/LTR, opt-in
  `kge-text-ttf` module (touch-point 2026-09-16).
- Round D: `GlyphCoverage` (normalized at the seam, whole-pixel bearing, y down)
  and the `Font`-owned per-size `GlyphAtlas`, whose entries are
  `AtlasGlyph.Blank` or `AtlasGlyph.Placed(chartIndex, source, size, bearing)`.
  Charts are 512² white-RGB + alpha-coverage `Sprite`s, the entry carries no
  advance, and `Font.glyph(sizePx, glyphId)` is the single cache entry point.
- Round C: `ShapedRun`/`ShapedGlyph(glyphId, cluster, offset, advance)` and
  `TextMetrics(ascender, descender, lineGap)`, all in font pixels. `offset` and
  `advance` are HarfBuzz 26.6 values, so **x is right-positive and y
  up-positive**; `ascender` is positive and `descender` negative.
- Round D decision 6: draws take the `Font` directly and the scope is ownership
  only; `TtfTextService.createResources(scope, font)` stays as it is.

## Reference evidence (olc / `main`)

Neither olc v2.30 nor `main` has a TTF text path (round D verified; `main`'s only
text is the ported bitmap font), so parity here is the **shape** of the drawing
API, which `C7`'s `DrawStringService`/`DrawStringAddon` already carries into
`kge-core` and `main`'s `DrawStringAddon` carried before it:

- names, defaults (`color = olc::WHITE`, `scale = 1`, `nTabSizeInSpaces = 4`),
  `\n` resetting x and advancing y by the line height, `\t` advancing x,
  `GetTextSize` returning the pen extent in cells with a one-line minimum, and
  `Custom` being the only preserved pixel mode.
- `main`'s addon (`git show main:kge-core/.../engine/addon/DrawStringAddon.kt`)
  is the same surface over `Rasterizer`; `C7` (chunk `29`) is the Kotlin
  precedent for the service/addon split and the recorded mode/scale divergences
  E1 repeats.

## Decision 1 — the draw contract (E1 public surface)

`TtfTextService` gains the draws; every one takes the `Font` (round D decision 6)
and a required `sizePx`:

```kotlin
fun getTextSize(font: Font, text: String, sizePx: Int, tabSizeInSpaces: Int): Int2D

fun drawString(
    font: Font, target: Pixmap.Mutable, x: Int, y: Int, text: String,
    sizePx: Int, color: Pixel, scale: Int, tabSizeInSpaces: Int, mode: Pixel.Mode,
)

fun drawString(font: Font, target: Pixmap.Mutable, position: Int2D, /* as above */)
```

- **`sizePx` is required and has no default.** There is one face and no fixed
  cell, so any default would be an unjustified constant of exactly the kind the
  atlas research rejected for `512`.
- **No `…Prop` variants.** The mono/prop pair is olc's two-sheet artifact; one
  face has one metric set, and `getTextSize` is therefore a single function.
- **`getTextSize` takes no `scale`** (olc/C7 parity: the returned box is
  unscaled; the caller multiplies).
- `color = WHITE`, `scale = 1` and `tabSizeInSpaces = 4` are the **addon's**
  defaults, never the service's (olc's `nTabSizeInSpaces` default is 4).

The addon:

```kotlin
interface TtfDrawStringAddon : HasDrawTarget, HasDrawModes {
    val tabSizeInSpaces: Int get() = 4
    fun getTextSize(font: Font, text: String, sizePx: Int): Int2D
    fun drawString(font: Font, position: Int2D, text: String, sizePx: Int,
                   color: Pixel = Colors.WHITE, scale: Int = 1)
    fun drawString(font: Font, x: Int, y: Int, text: String, sizePx: Int,
                   color: Pixel = Colors.WHITE, scale: Int = 1)
}
```

- **E1's addon carries only `HasDrawTarget` and `HasDrawModes`.** `HasWindow` and
  `HasLayers` exist for the decal viewport and the layer instance queue, both
  E2's; adding them later is source-compatible for the intended consumer, an
  `Engine` subclass that already satisfies them. `HasResourceScope` is **not**
  there either: C7's addon carries it because its draws resolve the font from the
  scope, while these draws take the `Font` directly (round D decision 6), so the
  role would have no consumer — it returns only if E3's decal path needs it,
  which nothing yet suggests.
- **This is also the test decision.** Without `HasLayers` the addon's host needs
  no `LayerStack`, hence **no GL context and no core test fixture** — `installGl`,
  `RecordingGLService` and the handle factories live in `kge-core`'s test source
  set, which the module cannot see, and nothing in the module has a fake or a
  hidden context. The CPU path is therefore pinned on all three targets in the
  module's `commonTest`. Shipping the decal variants in E1 would have forced the
  module to grow its own GL fixture or a real context; recorded for E2.
- An application may mix `DrawStringAddon` (core) with `TtfDrawStringAddon`: the
  names repeat but the parameter lists are disjoint (`Font` + `sizePx` vs not),
  so the overloads coexist. The cross-API compatibility analysis stays the
  recorded follow-up, not a commitment.
- `TtfDrawStringAddon` is public because it is the consumer's extension seam, in
  the module's `Ttf` naming; nothing else E1 adds becomes public. The walk, the
  measure and draw entry points and the blit are `internal` — their consumers are
  the service default in another file, the module's tests, and E3's decal path —
  while the tint mode and the composite stay file-`private`; `GlyphAtlas` and
  `AtlasGlyph` stay `internal` as round D left them.

## Decision 2 — the pen walk: lines, tabs, placement

- **Lines and tabs are the engine's, never HarfBuzz's.** The text is split at
  `'\n'` into lines and each line at `'\t'` into segments; each segment is one
  `font.shape(segment, sizePx)` call. `'\n'` and `'\t'` are control characters
  with no shaping, and the cluster indices of a segment are local to it, which is
  irrelevant while text entry is out of scope.
- **Pen:** float, per line. For each shaped glyph the destination is
  `x + round(penX + offset.x) + bearing.x` and
  `y + round(penY - offset.y) + bearing.y`, then `penX += advance.x`. The
  offset never advances the pen, and its **y is negated** because HarfBuzz's y
  axis points up while the raster's points down (round C exposed the sign and did
  not normalize it; the mark test in the plan pins it).
- **`'\n'`** resets `penX` to 0 and advances
  `penY += lineHeight`, with
  `lineHeight = ceil(ascender - descender + lineGap)` — whole pixels, from the
  run's `TextMetrics` (19 at 16 px and 38 at 32 px on the shipped Roboto). This
  is olc's `sy += 8 * scale` shape with the font's own line height in place of
  the fixed cell.
- **`'\t'` moves the pen to the next tab stop**, it does not add a fixed
  advance: `penX = (floor(penX / step) + 1) * step`, with
  `step = tabSizeInSpaces * spaceAdvance` (the advance of U+0020 at `sizePx`,
  shaped once per call when a tab occurs) and the grid measured from the line
  start, so the pen lands on the same stops whatever precedes it. A tab already
  on a stop advances a whole step, as in an editor. `getTextSize` and
  `drawString` share the walk, so both see the same stops. **Divergence from olc,
  recorded:** olc's tab always adds `nTabSizeInSpaces` 8-pixel cells — a fixed
  step with no stops — and its cell only exists because its bitmap font is a
  fixed grid; the cell of a proportional face is its space.
- **Rounding happens at placement, not in the pen.** The pen keeps HarfBuzz's
  kerning and ligature geometry; only the drawn origin is snapped. E2's decal
  path needs the same walk with float positions, so the walk yields float
  placements and each consumer snaps what it must. **Divergence from olc,
  recorded:** olc's bitmap pen is an integer cell grid and has no fractional
  advance at all.
- **Scale** (CPU) is an `Int` that multiplies both the advance and the painted
  block — olc's `uint32_t scale`. `scale <= 0` is a no-op and short-circuits
  before the argument checks, exactly as `C7`'s `drawText` does (its recorded,
  plan-mandated rule).
- **Validation:** `sizePx > 0` (thrown by `Font`'s own `require`),
  `tabSizeInSpaces > 0` (olc/C7 `check`). Empty text draws nothing;
  `getTextSize("")` is `(0, lineHeight)` — olc's `(0, 8)` shape with the font's
  line.
- **`y` is the top of the line box, not the baseline** — olc's cell-top convention
  — so the baseline of line *n* is `y + n * lineHeight + ascender` and the
  destination is `round(lineTop + ascender - offset.y) + bearing.y`. Without it
  `drawString(0, 0, …)` would draw above the target: `bearing.y` is negative
  (`-12` for `A` at 16 px) because it is measured from the baseline up.
  `getTextSize`'s height is the same line box, so the two agree by construction.
- **Clipping** is the target's bounds only, per pixel, as any blit.

## Decision 3 — coverage compositing, the tint and `mode`

- The blit writes, per covered pixel, the tint with the glyph's coverage folded
  into its alpha, composited src-over. The atlas's alpha **is** the coverage, and
  preserving it is the reason the module exists.
- **Divergence from olc/C7, recorded:** olc resolves an opaque `col` to `Mask`
  and a translucent one to `Alpha`; that is a binary mask for a bitmap sheet, but
  for TTF it would throw the antialiasing away and blend a uniform alpha. E1
  keeps **only `Custom`** — olc's own "preserve `Custom`" rule, and the same
  *shape* `C7` recorded — and composites coverage for every other mode.
- `Pixel.Mode.Custom` receives the coverage-weighted pixel, so a custom mode
  still observes the text's own pixels; the composition is pinned by a test. This
  is also why the `mode` parameter stays: the addon's `HasDrawModes` supplies it,
  and `Custom` is a real, observable effect.
- **A cell with no ink never reaches the caller's `Custom`.** olc taps its draw
  seam only where the sheet has ink (`DrawString` checks the cell's red channel
  before `Draw`), and here the coverage byte is that test: coverage 0 returns the
  stored pixel untouched, ink cells are the only ones the mode sees. Without this
  rule a mode that ignores alpha would paint the glyph's whole bounding box —
  corner cells included — which is not what olc does and not what a caller can
  compensate for.
- The tint's own alpha multiplies the coverage, so a translucent `color` fades
  the glyph instead of flattening it.

## Decision 4 — the blit reuses `BlitService`

- One ink glyph is one
  `BlitService.blitRegion(target, dest.x, dest.y, chart, glyphBox.source, glyphBox.size, scale, Pixmap.Flip.NONE, tintMode)`,
  where `tintMode` is a private `Pixel.Mode.Custom` that reads the chart pixel's
  alpha as coverage (`Colors.TRANSPARENT` where there is no ink), returns the
  stored pixel unchanged when there is no ink, folds the tint's alpha in
  otherwise, and hands the result to the caller's `Custom` when there is one.
- Rationale: it reuses the engine's tested scale, clipping and out-of-bounds
  rules instead of re-deriving them, and keeps text on the same overridable seam
  as every other raster draw. The `fontDevelopment`/`C7` shape — a per-pixel
  `Rasterizer.draw` walk — is **rejected** as a duplicate of those rules.
- The source chart is `Font.atlas(sizePx)`'s `charts[chartIndex]`, internal like
  `AtlasGlyph`; no new name becomes public.

## Decision 5 — resources: E1 allocates nothing

- The atlas and its charts belong to the `Font` (round D), the target belongs to
  the layer, and the walk allocates no engine-owned memory — the blit reads the
  chart directly. E1 adds no `letClosingIfFailed` call site and no new leak path,
  so the round's resource coverage is round D's, re-run.
- The scope only ever arrives as `createResources`' parameter — how the engine
  hands the `Font` an owner — and the draws must fail fast after the font is
  closed (`Font`'s existing guard). Drawing a closed atlas is impossible because
  `Font.glyph` → `GlyphAtlas.glyph` checks first. No resource role is needed to
  reach it, which is why the addon does not carry `HasResourceScope`.

## Verification plan (the micro-plan's contract)

1. **Build.** `kge-text-ttf:build` green on all three targets; no new dependency,
   no `kge-core` change.
2. **Placement**, pinned on the shipped Roboto 3.015 default instance at 16 and
   32 px: `getTextSize` for `""`, `"A"`, a kerned pair (`"AV"`), `"A\tB"` and
   `"AAAA\tB"` (the same tab stop reached from a pen before it and from a pen
   after it, so the stop rule is pinned and not a fixed step), `"A\nB"` (height
   is 2 lines) and a multi-line string whose widest line is not the last;
   `drawString`'s ink box equals the atlas box placed at the pinned bearing — for
   `"A"` at 16 px drawn at `(0, 0)` that is the round D box 11x12 at
   `(0, round(14.84375) - 12) = (0, 3)` — the second glyph's pen equals the
   first's snapped advance, and a `"\n"` draw puts the second line one line
   height lower (the draw path must be pinned off line 0, not only measured).
3. **Coverage.** Over the drawn box, the summed alpha equals the summed coverage
   round D pinned for that glyph — the composition adds and removes nothing; spot
   pixels: full coverage equals the opaque tint over an **empty** target, zero
   coverage leaves the target as it was, partial coverage equals the blend of the
   tint at that coverage. The same draw over a **pre-filled** destination (opaque
   and translucent) asserts the src-over result and that a zero-coverage cell
   keeps the destination pixel exactly — an empty target alone cannot distinguish
   the composite from an overwrite. A translucent `color` scales the coverage; a
   `Custom` mode observes the coverage-weighted pixel and is never tapped on a
   no-ink cell.
4. **Offsets.** A decomposed mark places the mark with a non-zero offset: the pen
   after it is unchanged and the mark's pixels land where the offset says — the
   y-negation and the "offset does not advance" rule. **Fixture finding
   (2026-09-29, measured on JVM):** the obvious `"e\u0301"` cannot be used —
   HarfBuzz composes a base and a canonical mark into the precomposed glyph
   whenever the font carries it, so no mark is positioned; the same holds for the
   other Latin pairs measured. **Test-strength finding (2026-09-29):** an
   above-mark alone is not enough either — `"x\u0301"`'s y offset is `-0.078125`,
   which rounds the same with the sign flipped, so it cannot falsify the rule it
   is there for; the case set is `"x\u0301"` (above, x offset, zero advance) plus
   `"q\u0323"` (below; its `-3.171875` separates 19 from 13). Both are properties
   of the fixture and the test, recorded because a test that cannot fail is worse
   than a missing one.
5. **Scale and no-ops.** `scale = 2` paints each covered pixel as a 2×2 block and
   doubles the advance; `scale = 0` paints nothing and, `C7`'s parity, does not
   validate the tab size; `sizePx <= 0` and `tabSizeInSpaces <= 0` fail fast;
   empty text draws nothing.
6. **Clipping.** A glyph drawn partly outside the target paints only the inside
   and never throws.
7. **Cache.** Drawing the same text twice at the same size adds no chart and
   rasterizes nothing (`Font.atlas(sizePx)?.charts` stays constant).
8. **Addon.** Typed and raw forms both forward to the service with the addon's
   `tabSizeInSpaces`, `pixelMode` and `drawTarget`, on a GL-free host; a null
   `drawTarget` is a no-op. Pinned on the module's three targets in `commonTest`.
9. **Close.** Drawing after `Font.close()` fails fast; round D's
   `createResources`/scope-ownership test is re-run.
10. **Gate.** `tools/gradle build`; the `#35` zero-test guard is what makes the
    web counts trustworthy, so the module's browser suites must report the new
    tests rather than merely exit 0.
11. **Residual, for the report:** no golden of a composed string in this round —
    the harness is E2's, so the composition rests on 2–4 and the per-glyph raster
    stays round D's; and no size cap for a glyph box larger than a chart (round
    D's fail-fast), unchanged.

## Carried to the micro-plan

- The exact rounding (`roundToInt`) and the exact `lineHeight` ceiling, pinned
  once against the shipped Roboto at 16 and 32 px.
- Whether the walk is a private iterator/sequence or a private function per
  caller, and how the later rounds reuse it (float placements out for the decal,
  no engine-derived expectation for E2's goldens).
- The tint mode's shape, and whether the space advance is shaped per call or
  memoized per `(font, sizePx)` within a draw.
- The GL-free addon host for the test and the test file names.

## Round E2 — the module's golden harness and the text goldens

Not decided here; this records what it inherits. It is a test-infrastructure
concept, as `kge-core`'s was (chunk `28`), and its decisions are its own
touch-point:

- **Harness.** The codegen (`GenerateGoldenImagesTask`/`GoldenActualToPngTask`
  today live inline in `kge-core/build.gradle.kts`) and the runtime matcher
  (`shouldMatchGolden`, the surface fixture, the `WxH:<base64>` token) exist only
  in `kge-core` and are not reachable by another module. The round decides
  whether they move to `buildSrc` plus a shared test-support module — which would
  also serve E3's GL fixture need — or are reproduced module-locally, and
  migrates `kge-core`'s call sites if they become shared.
- **Oracle.** References are derived independently (a throwaway `.tmp/` port
  driving FreeType with this document's placement rules), never blessed from the
  engine's output; provenance is recorded per case, as chunk `28` did.
- **Cases.** At least: a plain string, a kerned pair, a tab stop, a multi-line
  draw, `scale = 2`, an opaque and a translucent tint, **a draw over a pre-filled
  destination** (the composite E1 can only pin numerically), a glyph clipped at
  the target edge, and the decomposed mark — the composition facts E1 pins
  numerically and a golden can pin exactly.
- **Constraint.** E1's tests must already be free of engine-derived expectations,
  so E2 only adds references.

## Round E3 — the GPU path

- The owner's 2026-09-24 direction stands: the reduction is taken at the
  **upload seam** — a one-channel coverage texture (`GL_R8` + swizzle) with a
  region update and no `pixelStorei` — leaving `GlyphAtlas`, its `Sprite` charts
  and round D's white-alpha encoding unchanged. `CHART_SIZE` is not what changes.
- E3's touch-point decides: which side owns the coverage texture (a core
  `Texture`/`Decal` capability versus a module-private carrier over the raw GL
  seam, with the missing `GL` constants `R8`/`RED`/`TEXTURE_SWIZZLE_*`), how the
  existing `Decal.update()`/UV-mirror coupling is kept honest, and the upload
  policy measured as research §6 requires (region versus full re-upload at
  256/512, `GL_MAX_TEXTURE_SIZE`, chart-count effect) with the `kge-benchmark`
  apparatus.
- E3 also decides the decal geometry (one `DecalInstance` per ink glyph through
  `DrawPartialDecalService`, source the glyph box, tint, `decalMode`/
  `decalStructure` carried, blank glyphs skipped), the addon's `drawStringDecal*`
  with `HasWindow`/`HasLayers`, the float scale, and sampling/padding (NEAREST
  with bleeding at fractional scale, or padding for LINEAR).
- The fixture question is E2's to answer: a decal test needs a `LayerStack`,
  hence GL, and the module can see neither `installGl` nor `RecordingGLService`.

## Out of scope (E1)

The decal/GPU path, the coverage texture, `drawStringDecal*`,
`getTextSizeProp`/`drawStringProp` (there is one face), line breaking/word wrap,
rotation, text entry and the console, SDF/MSDF, chart sizing or a size cap,
variable-font axes, the golden harness and its references (E2), and a text
workload in `kge-benchmark`.
