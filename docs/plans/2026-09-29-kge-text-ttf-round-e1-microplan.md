# `kge-text-ttf` — micro-plan, round E1 (CPU text drawing)

**Date:** 2026-09-29. Round **E1** of the text work: the pen walk, the coverage
blit into a `Pixmap.Mutable`, `getTextSize` and the olc-shaped addon — all on the
CPU. Rounds: A bitmap (core) → B scaffold → C face + shaping + layout → D raster
+ atlas → **E1 CPU blit + addons** → E2 golden harness + text goldens → E3
coverage texture + decal. Context: the round E touch-point
(`docs/plans/2026-09-29-r6-round-e-touchpoint.md` — decisions 1–5 are settled
there, including the tab stop, the line box and the coverage compositing), the
round D micro-plan, and decisions chunks `29`, `34`, `36`.

## Scope

Module only: **no `kge-core` change, no build change, no new dependency**. Public
additions: `TtfTextService.getTextSize`/`drawString` (typed and raw forms) and
`TtfDrawStringAddon`. Nothing else becomes public — the walk, the tint mode and
the blit stay module-internal, and `GlyphAtlas`/`AtlasGlyph` stay `internal`.

## Surfaces

`TtfTextService` (existing file) gains three members, and the companion forwards
each to the delegate:

```kotlin
fun getTextSize(font: Font, text: String, sizePx: Int, tabSizeInSpaces: Int): Int2D

fun drawString(
    font: Font, target: Pixmap.Mutable, x: Int, y: Int, text: String,
    sizePx: Int, color: Pixel, scale: Int, tabSizeInSpaces: Int, mode: Pixel.Mode,
)

fun drawString(font: Font, target: Pixmap.Mutable, position: Int2D, /* as above */)
```

`TtfDrawStringAddon` (new, public, `dev.staticsanches.kge.text.ttf`):

```kotlin
interface TtfDrawStringAddon : HasDrawTarget, HasDrawModes {
    /** The number of space advances a tab stop spans; olc's `nTabSizeInSpaces`. */
    val tabSizeInSpaces: Int get() = 4

    fun getTextSize(font: Font, text: String, sizePx: Int): Int2D =
        TtfTextService.getTextSize(font, text, sizePx, tabSizeInSpaces)

    fun drawString(font: Font, position: Int2D, text: String, sizePx: Int,
                   color: Pixel = Colors.WHITE, scale: Int = 1) =
        drawString(font, position.x, position.y, text, sizePx, color, scale)

    fun drawString(font: Font, x: Int, y: Int, text: String, sizePx: Int,
                   color: Pixel = Colors.WHITE, scale: Int = 1) {
        val target = drawTarget ?: return
        TtfTextService.drawString(font, target, x, y, text, sizePx, color, scale, tabSizeInSpaces, pixelMode)
    }
}
```

- `HasWindow`/`HasLayers` are **not** here: they belong to E3's decal variants.
  Adding them later is source-compatible for an `Engine` subclass, and leaving
  them out is what keeps this round's addon test free of `LayerStack` and GL.
  `HasResourceScope` is not here either — these draws take the `Font` directly, so
  no body would read `resourceScope` (review round 1, Minor).
- No `…Prop` variants and no `scale` in `getTextSize` (touch-point decision 1).

## Files

| file | change |
|---|---|
| `commonMain/…/TextDraw.kt` | new: the walk and the coverage blit |
| `commonMain/…/TtfTextService.kt` | the three members + the default's bodies |
| `commonMain/…/TtfDrawStringAddon.kt` | new: the olc surface |
| `commonTest/…/TextDrawTest.kt` | new: walk, size, coverage, scale, offsets, clipping, cache |
| `commonTest/…/TtfDrawStringAddonTest.kt` | new: the addon over a GL-free host |
| `commonTest/…/RobotoFixture.kt` | reused unchanged |

`TextDraw.kt`'s entry points (`walkText`, `measureText`, `drawText`) are
`internal` because their consumers are the service default in another file, this
module's tests, and E3's decal path; the tint mode, the composite and the line
splitting are file-`private`.

## The walk

One walk drives both measuring and drawing, so `getTextSize` and `drawString`
can never disagree about a tab stop or a line:

```kotlin
/**
 * Walks [text] from the line box top-left ([x], [y]) and reports every shaped
 * glyph at its float baseline pen; returns the box the text occupies.
 */
internal inline fun Font.walkText(
    text: String,
    sizePx: Int,
    tabSizeInSpaces: Int,
    x: Int,
    y: Int,
    place: (glyph: ShapedGlyph, penX: Float, penY: Float) -> Unit,
): Int2D
```

- **Lines and tabs are the engine's.** Split at `'\n'` into lines and at `'\t'`
  into segments; each segment is one `font.shape(segment, sizePx)`. `'\n'`/`'\t'`
  are control characters and are never shaped.
- **`y` is the top of the line box** (olc's cell top): for line *n*,
  `lineTop = y + n * lineHeight` and `baseline = lineTop + ascender`, where
  `lineHeight = ceil(ascender - descender + lineGap)` (19 at 16 px, 38 at 32 px
  on the shipped Roboto). The pen handed to `place` is the pair `(penX, baseline)`,
  absolute in the target — two `Float`s, not a `Float2D`, so the measuring sink
  allocates nothing per glyph (review round 1, Minor; the `C7` finding class).
- **Float pen**: `penX` accumulates exact advances; `place` receives the float and
  the CPU blitter snaps (`round(penX + glyph.offset.x) + bearing.x`). The offset
  never advances the pen, and its `y` is negated by the blitter (HarfBuzz is
  y-up, the raster y-down).
- **A tab is a stop, not a step** (touch-point decision 2):
  `penX = (floor(penX / step) + 1) * step`, `step = tabSizeInSpaces *
  spaceAdvance`, the grid measured from the line origin, with `spaceAdvance` the
  advance of U+0020 at `sizePx` shaped once per walk and only when a tab occurs.
- **Measuring must not rasterize**: `place` is the only place the atlas is
  touched, so `getTextSize` passes an empty sink and leaves `Font.atlas(sizePx)`
  `null`. Pinned by a test.
- **The returned box** is `Int2D(ceil(widest penX - x), max(1, lines) *
  lineHeight)`; `""` is `(0, lineHeight)` and trailing spaces count, olc's
  pen-extent rule.
- Metrics come from `font.shape("", sizePx).metrics` once per walk (both actuals
  handle an empty code-point array), so a text of only tabs and newlines still
  has a line height.

## The coverage blit

- Per `AtlasGlyph.Placed`, one
  `BlitService.blitRegion(target, dest.x, dest.y, chart, placed.source, placed.size, scale, Pixmap.Flip.NONE, tintMode)`
  with `dest = Int2D(round(penX + offset.x) + bearing.x, round(penY - offset.y) + bearing.y)`
  and `chart = font.atlas(sizePx)!!.charts[placed.chartIndex]` (hoisted once per
  draw).
- `tintMode` is a file-private `Pixel.Mode.Custom`: the source pixel the blit
  hands it is the chart's white-with-alpha-coverage pixel, so coverage is
  `newPixel.a`. A **no-ink cell returns `oldPixel` without reaching the caller's
  mode** — olc taps its draw seam only where the sheet has ink — and an ink cell
  returns the tint with `alpha = tint.a * coverage / 255` composited src-over onto
  `oldPixel`. When the caller's `mode` is itself a `Custom`, the coverage-weighted
  pixel is passed to it instead, so a custom mode still sees the text
  (touch-point decision 3).
- `AtlasGlyph.Blank` (space, control) is skipped — the advance already moved the
  pen. `scale <= 0` returns before any validation (the `C7` rule);
  `check(tabSizeInSpaces > 0)` and the positive `sizePx` (from `Font`) follow.

## Pinned expectations (the derivation rule)

The round pins an **advance table** for the characters the tests use, at 16 px,
measured on jvm, js and wasmJs and pasted only once the three agree (round C/D's
procedure), beside `Roboto.FAMILY`/`Roboto.VERSION`. From the already-pinned
values: `"A"` 10.4375, `"AV"`'s first glyph 9.765625 (kerned), `" "` 3.96875,
metrics 14.84375 / −3.90625 / 0 → line height 19.

Every placement and size expectation is then **derived by hand** from those
constants and round D's raster table (chunk `36`: `A` 11x12 `(0,-12)` Σ9983,
`V` 10x12 `(0,-12)` Σ8878, `o` 9x9 `(0,-9)` Σ7634, `1` 5x12 `(1,-12)` Σ5418) —
never pasted from the engine's own draw output, because E2's goldens must not
have to unlearn an engine-derived expectation. Examples the tests assert:

| case | derived expectation |
|---|---|
| `getTextSize("")` | `(0, 19)` |
| `getTextSize("A")` | `(11, 19)` — `ceil(10.4375)` |
| `getTextSize("A\nB")` | height `38` |
| `"A\tB"` tab stop | pen `10.4375`, stop `15.875` (= `(floor(10.4375 / 15.875) + 1) * 15.875`), and **not** `10.4375 + 15.875 = 26.3125`, the fixed step |
| `"AAAA\tB"` tab stop | the next multiple of `15.875` above the pinned advance of `"AAAA"` (at most `3 * 15.875 = 47.625`) — the same grid reached from a further pen, which a fixed step cannot produce |
| `drawString(0, 0, "A")` ink | box 11x12 at `(0, round(14.84375) - 12) = (0, 3)` |
| Σ alpha over that box | `9983` (`color = WHITE`, composited over a transparent target) |
| the same box over a pre-filled target | each cell is the src-over composite of the tint at that cell's coverage over the destination; a no-ink cell inside the box equals the destination exactly |
| `drawString(0, 0, "A\nA")` | the second line's ink box is `19` px lower |
| `"x\u0301"` mark placement | mark (glyph 169) at `(round(7.9375 + 0.453125) - 6, round(14.84375 + 0.078125) - 12) = (2, 3)`, 5x2, and the pen after the pair equals `"x"`'s 7.9375 |
| `"q\u0323"` mark placement | base (glyph 85) at `(0, 6)`, mark (glyph 173) at `(round(9.09375 + 2.734375) - 6, round(14.84375 + 3.171875) + 1) = (6, 19)`, and the pen afterwards equals `"q"`'s 9.09375 — the sign is falsified here: adding the offset gives `13`, not `19` |

The mark offsets and boxes are **new pinned constants** (measured on the three
targets like the advances), not derivations: the derivation rule above applies to
the placement arithmetic, not to inventing the font's values. Both mark cases
draw past the line box — `"q\u0323"`'s dot lands at row 19 of a 19-row box — which
is olc's pen-extent sizing: the size is where the pen went, not where the ink
ended. The test's target is tall enough to hold the ink and asserts the ink
position, so the two facts stay distinguishable.

## Steps (TDD: red → green per feature)

1. **The walk + `getTextSize`** (red → green): `TextDrawTest` — the derived sizes
   above, the two tab-stop cases, a multi-line width taken from the widest (not
   the last) line, and the "measuring does not rasterize" invariant
   (`font.atlas(16)` is still `null` after `getTextSize`). Red: the members do not
   exist.
2. **The coverage blit** (red → green): draw `"A"` at 16 px into a cleared sprite
   — the ink box, Σ alpha `9983`, a full-coverage pixel equal to the opaque tint,
   a zero-coverage pixel untouched; a translucent tint scales the alpha; a
   recording `Custom` receives the coverage-weighted pixel; a space draws nothing
   but moves the pen; `""` draws nothing.
3. **Pen, kerning and offsets** (red → green): `"AV"` places the second glyph at
   the kerned advance, not `"A"`-alone's; `"x\u0301"` places the mark with its
   pinned non-zero offset while the pen after it equals the pen after `"x"` alone
   (the mark's advance is 0), and the mark's ink sits above the base's; `"q\u0323"`
   places a **below** mark and is the case that actually falsifies the y sign.
   **Fixture finding (2026-09-29, measured on JVM):** the mark case cannot be
   `"e\u0301"` — HarfBuzz composes a base and a canonical mark into the
   precomposed glyph whenever the font has it (`e`+U+0301 → glyph 703, offset
   `(0,0)`; same for `e`+U+0323, `a`+U+0301, `e`+U+0302, `e`+U+0304,
   `A`+U+030A, `n`+U+0303), so no mark is ever positioned. `x`+U+0301 and
   `q`+U+0323 have no precomposed form and stay decomposed.
   **Test-strength finding (2026-09-29):** `"x\u0301"` alone does not pin the
   y-negation — its y offset is `-0.078125`, so
   `round(14.84375 - (-0.078125))` and `round(14.84375 + (-0.078125))` are both
   `15`, and an implementation that added the offset instead of subtracting it
   passed the whole suite. `"q\u0323"`'s offset is `-3.171875`, which separates
   them (19 against 13), so both cases stay: `"x\u0301"` pins the above-mark
   direction, the x offset and the zero advance, `"q\u0323"` pins the sign and
   the below-mark direction.
4. **Scale and the no-ops** (red → green): `scale = 2` paints each covered pixel
   as a 2×2 block, doubles the box and the advance; `scale = 0` paints nothing
   and does not validate the tab size; `sizePx <= 0` and `tabSizeInSpaces <= 0`
   throw.
5. **Clipping** (red → green): a glyph drawn partly outside the target paints only
   the inside and never throws.
6. **Cache and close** (red → green): the same text drawn twice adds no chart and
   rasterizes nothing; drawing after `Font.close()` fails fast.
7. **The addon** (red → green): `TtfDrawStringAddonTest` — a private GL-free host
   implementing the two roles (`drawTarget` a `SpriteService` sprite, `pixelMode`);
   the typed and raw forms reach the service with the host's `tabSizeInSpaces`,
   `pixelMode` and non-default `color`/`scale`, a null `drawTarget` is a no-op.
8. **Delta after review round 1** (both axes FAIL, one Important each and the same
   cause): draw over a **pre-filled** destination (opaque and translucent) and
   assert the composite plus a no-ink cell keeping the destination — with the
   empty target the `srcOver` formula is unfalsifiable (`return weighted` passes
   everything); a `"\n"` draw pinned one line height down; the `Int2D` overload
   pinned; the `Custom` tap narrowed to ink cells (decision 3's new rule) with the
   two mode tests updated; the recorded `Custom` cells compared as a multiset; the
   test helper's sprite closed on the failure path; and the file's blank-line
   separators restored.
9. **Gate, review, decisions entry, commit** (below).

## Out of scope (round E1)

The decal/GPU path, the coverage texture, `drawStringDecal*`, `getTextSizeProp`/
`drawStringProp`, the golden harness and its references (E2), line breaking/word
wrap, rotation, text entry and the console, SDF/MSDF, chart sizing or a size cap,
variable-font axes, and a text workload in `kge-benchmark`.

## Gate

`tools/gradle build` green: every target's tests (both browser suites included),
ktlint, the `webMain` metadata compilation and `buildSrcCheck`; test tasks always
execute, but the reported counts are read, per the `#35` guard. Goldens are not
wired in this round: the module has no harness yet (E2).
