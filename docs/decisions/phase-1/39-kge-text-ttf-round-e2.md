## 2026-10-01 — `kge-text-ttf` round E2: the shared golden harness and the text goldens

Round E2 of the text work: the golden harness leaves `kge-core` and becomes
shared infrastructure, and `kge-text-ttf` gains its committed text references
with independent provenance. Material: the round E2 touch-point
(`docs/plans/2026-10-01-r6-round-e2-touchpoint.md`), the E2 micro-plan
(`docs/plans/2026-10-01-kge-text-ttf-round-e2-microplan.md`), the harness chunk
`28` and the chunk `31` `buildSrc` precedent. Rounds: A bitmap (core) → B
scaffold → C face + shaping + layout → D raster + atlas → E1 CPU blit + addons →
**E2 shared golden harness + text goldens** → E3 coverage texture + decal.

### Shipped

- New module **`kge-test-support`** (jvm/js/wasmJs): the shared
  `dev.staticsanches.kge.testsupport.golden` package — `GoldenImage`,
  `Pixmap.shouldMatchGolden(goldens, name)`, `canvas(width, height)` — its own
  matcher suite (the moved seven matcher cases and the accessor smoke test) and
  the two `harness/*` references those tests need.
- **`buildSrc`**: `GoldenImages.kt` (the pure `GoldenImageSpec` +
  `renderGoldenImages`), `GenerateGoldenImagesTask.kt` (the PNG generator,
  `GoldenActualToPngTask`, `GoldenRgba` moved verbatim from `kge-core`, and the
  `Project.goldenImages(...)` registration helper mirroring `embedResources`).
- **`kge-core` migrated**: its local harness and inline codegen are deleted, its
  `commonTest` depends on `kge-test-support`, and the generated per-module
  accessor plus a generated one-argument binder keep every existing call site and
  every golden expectation unchanged (the only test edits are the `canvas`
  import in seven files).
- **`kge-text-ttf`**: `commonTest` on the shared harness, the codegen wired, and
  `TextGoldenTest` — eleven references under `src/commonTest/golden/text/`,
  asserted on jvm, js and wasmJs.

### The harness is extracted, not duplicated (owner)

The owner chose extraction at the touch-point. A second module needs the
harness, and KMP publishes no consumable test-fixtures configuration, so the
alternatives were to reproduce ~250 lines module-locally or to invent exactly
the configuration this module replaces.

- **Package split**: the shared code is its own artifact's package
  (`…testsupport.golden`); the generated data accessor stays
  `dev.staticsanches.kge.golden`, one object per module, so no split package and
  no call-site churn in `kge-core`.
- **The binder is generated**: `renderGoldenImages` emits, beside the accessor,
  `fun Pixmap.shouldMatchGolden(name: String) = this.shouldMatchGolden(GoldenImages.byName, name)`,
  which keeps chunk `28` decision 6's one-argument ergonomics and makes it
  impossible for a module adopting the harness to forget the binding. Rejected: a
  handwritten binding per consumer (boilerplate that drifts) and a runtime
  registry the matcher resolves through (a module's goldens would become visible
  or not depending on JS/wasmJs initialization order).
- **Migration contract honoured**: the golden tests stay in `kge-core` and not
  one expectation changed — the core suite's 23 committed references are
  byte-identical and five of its six golden test classes gained only the `canvas`
  import (the sixth uses its own pattern fixture).
  Two further consumers surfaced (`text/DrawStringTest`,
  `text/DrawStringResourcesTest`), the churn the touch-point had flagged.
- **The GL fixture stays in `kge-core`** (owner): E3 moves `installGl`,
  `RecordingGLService` and the handle factories into the same module when a decal
  test actually needs them. E2's suite is GL-free.

### The oracle (touch-point decision 3)

References were derived by a throwaway JVM driver under `.tmp/golden-oracle/`
(deleted in the round's cleanup; its `derivation.md` is the source of the
provenance below). It drives the raw LWJGL HarfBuzz and FreeType bindings,
mirrors `NativeFaceJvm.kt`'s call sequence (blob/face/font, `hb_font_set_scale`,
`FT_Set_Pixel_Sizes`/`FT_Load_Glyph(FT_LOAD_DEFAULT)`/`FT_Render_Glyph(NORMAL)`,
bearing `(bitmap_left, -bitmap_top)`), and **references no
`dev.staticsanches.kge.*` type** — verified statically before any reference was
accepted. Independence here is independence of **code** for the walk, the tab
stop, the scale magnification, the coverage fold and the composite; the raster
library and its parameters are round D's by construction, and the oracle
cross-checks its own raster against the round D table before writing anything.

Every case's `(ink box, Σ alpha)` was hand-derived from the round C advance pins
and the round D raster table, and the oracle **aborts** if its render disagrees.
Values not derivable from the pins (glyph `B` 9x12 `(1,-12)` Σ12713, the
`128 * coverage / 255` fold, the clipped sub-coverage) were measured on the
throwaway path and frozen. A stdlib-only Python decoder re-derived every Σ alpha
and ink box from the PNGs, and a re-run of the oracle is byte-identical. The
writer is pinned (`TYPE_INT_ARGB` + `setRGB` + `ImageIO.write`, non-premultiplied
ARGB) so the round trip through the harness's `GoldenRGB` decoder is lossless.

### Cases and provenance

All at 16 px on the shipped Roboto 3.015 default instance, tab size 4,
`Pixel.Mode.Normal`. `Σα` is the surface's summed alpha; the sha256 is the
committed reference.

| case | surface | draw | ink box | Σα | sha256 (12) |
|---|---|---|---|---|---|
| `text/plain` | 30x24 | `"Ao"` at (2,2) | (2,5)-(20,16) | 17617 | `f42968fe6135` |
| `text/kerned` | 30x24 | `"AV"` at (2,2) | (2,5)-(21,16) | 18861 | `7b4f74d9d9f4` |
| `text/tab-stop` | 32x24 | `"A\tB"` at (2,2) | (2,5)-(27,16) | 22696 | `e3ae9a6d64c1` |
| `text/multiline` | 30x36 | `"A\nB"` at (2,2) | (2,5)-(12,35) | 22696 | `49a151490594` |
| `text/scale-2` | 48x32 | `"A"` at (2,2), scale 2 | (2,8)-(23,31) | 39932 | `65522a57d6c5` |
| `text/tint-opaque` | 30x24 | `"A"`, `rgba(220,30,40,255)` | (2,5)-(12,16) | 9983 | `a3d67bd610a6` |
| `text/tint-translucent` | 30x24 | `"A"`, `rgba(220,30,40,128)` | (2,5)-(12,16) | 4988 | `7f0fe0d9fd69` |
| `text/prefilled-opaque` | 30x24 | `"A"` over the opaque pattern | (2,5)-(12,16) | 183600 | `dcb417b6e918` |
| `text/prefilled-translucent` | 30x24 | `"A"` over the alpha-128 pattern | (2,5)-(12,16) | 97104 | `9b21614c3a58` |
| `text/clipped` | 9x9 | `"A"` at (-4,-6) | (0,0)-(6,8) | 4799 | `00e021eae497` |
| `text/mark` | 16x32 | `"q\u0323"` at (2,6) | (2,12)-(10,26) | 10535 | `42cacfaf95fe` |

The pre-filled destination is the harness's deterministic pattern,
`rgba(20 + 15x, 30 + 20y, 200 - 10(x + y), a)`, `a = 255` and `a = 128`
(out-of-range channels saturate, exactly as the oracle's own `rgba` did). Two
recorded deviations from the touch-point's <=48x32 budget note: `multiline` needs
36 rows (line 2's ink reaches row 35) and was given them rather than clipping the
line; and `prefilled-opaque` shows 65 changed cells for 66 ink cells because the
truncating composite can leave a coverage-<=2 cell exactly at the pattern — the
reference pins that, it is not a gap.

### E1 defect corrected: the scale anchor

The oracle exposed it. `TextDraw.kt` scaled the **absolute** pen
(`((penX + offset.x) * scale)` with `penX` already carrying `x`), so `scale > 1`
magnified the draw origin too and text drawn at `(2,2)` landed at `(4,10)`. Both
references scale only the line-relative layout: olc draws at `x + sx + i*scale`
with `sx` the scaled line-relative pen (`olcPixelGameEngine.h`, `DrawString`),
and this repo's `C7` `DrawStringService.drawText` does the same. E1's scale test
only used the origin `(0,0)`, which is why it passed.

Fixed by scaling the line-box-relative offsets and adding the origin back. At
`scale = 1` the expression is bit-identical for an integer `x`/`y`
(`round(v - x) + x == round(v)`), so no E1 expectation moved. New pins: `"A"` at
`(2,2)` scale 2 -> ink 22x24 at `(2,8)`, Σα `39932`; and, closing an ambiguity the
oracle's own rule left open, `"A\nA"` at `(2,2)` scale 2 puts line 2 exactly
`38` px (`19 * 2`) below line 1 — a newline advance scales, as `C7`'s `sy += 8 *
scale` and olc's `sy += 8 * scale` both do.

### E1 defect corrected: the composite must be target-independent

The two `prefilled-*` references passed on jvm and wasmJs and failed on the JS
browser target by one unit in 1 and 12 cells. Measured from the references
themselves: the committed f32 evaluation is reproduced exactly by an f32 probe
(0 mismatching cells), Kotlin/JS's double evaluation reproduces the failures
(1 and 12), and an exact-integer evaluation differs from f32 in 1 and 13 cells;
over 82,620 sampled `(source alpha, destination alpha, channel)` combinations the
integer and f32 results differ by one in 3.5% of the colour channels, and the
alpha channel never differs. Kotlin/JS's `Float` is a `Double`, so the float form
of the straight-alpha source-over is a **platform-dependent function** — the
opposite of what an exact-RGBA golden can pin (chunk `28` decision 1).

`TextDraw.kt`'s private `srcOver` is therefore redefined in exact integer
arithmetic — the same rational function the float form approximates, truncated:
`n = a * 255 + oldA * (255 - a)`, `channel = (source * a * 255 + destination *
oldA * (255 - a)) / n`, `alpha = n / 255` — with the `a == 0` and `oldA == 0`
short-circuits unchanged. `Int` arithmetic is exact on all three targets and the
largest intermediate (`255 * 255 * 255 * 2`) fits an `Int`, so the composite is
target-independent by construction. olc has no TTF path, so this is our
contract's arithmetic, not a parity divergence; the bitmap path's olc-parity
float blend is untouched.

The two references were regenerated from the oracle updated to the same
documented rule: nine references stayed **byte-identical**, `prefilled-opaque`
changed in one cell (`(11,14)` green `254 -> 255`) and
`prefilled-translucent` in thirteen (all green `254 -> 255`), 14 cells in total,
alpha never changed, and both Σα derivations held (`183600`, `97104`). Any E1
expectation that shifted under the integer rule was re-derived from the formula
rather than pasted (the E1 suite had compared the spec formula in-test, the class
of artifact chunk `38` already refused to pin as a literal).

**Carry-forward, dispositioned rather than widened into this round:** `kge-core`'s
`DrawService.blendAlpha` uses the same float formulation (`(color.a / 255f) *
blendFactor`, `1f - a`, `toInt()`), so the identical Kotlin/JS double-precision
hazard exists there. It is a core concept with an arbitrary `blendFactor` and no
failing evidence today; it belongs to a core round.

### Small defect corrected: the codegen could not render an empty golden set

`renderGoldenImages` emitted an untyped `listOf()` for zero images, so any module
wiring the harness before its first reference failed to compile. It now emits a
typed empty list; `kge-core`, `kge-test-support` and `kge-text-ttf` generated
accessors are unchanged, and `buildSrc`'s suite pins the empty case.

### Owner direction recorded: the text API unification comes next

The owner commissioned the analysis
(`docs/plans/2026-10-01-text-api-unification-findings.md`) and set the direction
in the roadmap: the two text surfaces stay siblings through the already-decided
work, and the **unification is attacked afterwards** — the core keeps the olc
bitmap font and `kge-text-ttf` draws from a font file through an API the core
defines. The document's option (a′) (a non-owning `TextFont` handle-per-size,
`TextService` as a shape, `prop` as a core extension) is the starting shape, its
list of open decisions is the next touch-point's agenda, and E3 must have fixed
the TTF decal contract first. The analysis itself recommends not unifying now:
no font-agnostic consumer exists, `sizePx` would need an unobservable core
parameter or a public handle, the mono/prop pair has no TTF counterpart, and one
unified `KGEOverridable` proxy would make the two fonts mutually exclusive
process-wide.

### Gate and review

`tools/gradle build` green over the whole round: `kge-core` 707 jvm / 742 js / 742
wasmJs, `kge-text-ttf` 83 / 86 / 86 (the eleven golden cases present and passing on
all three targets), `kge-test-support` 8 / 9 / 9 (the moved matcher suite),
`kge-font-roboto` 6 / 6 / 6, `kge-benchmark` 16 / 17 — plus ktlint and the nested
`buildSrcCheck`. The counts were read from the JUnit XMLs and their freshness
checked, per the `#35` rule rather than the exit code. The gate was re-run over
the review-fix delta.

Round 1 (Standards and Spec, both PASS): 0 Critical / 0 Important / 5 Minor
across the axes, all fixed in the delta rather than carried. Code: a blank
leading line left by the removed imports in `kge-core/build.gradle.kts`, and the
generator's `inputDir` missing the `@PathSensitive(PathSensitivity.RELATIVE)` its
sibling `EmbedResourcesTask` declares (a spurious re-run if the checkout moves;
the emitted accessor is byte-identical either way). Record: this entry said 24
where the core suite has 23 unchanged references, the touch-point said 26, and
the largest composite intermediate was written `33_157_750` where it is
`33_162_750` — all corrected. The Spec axis independently decoded the eleven
committed references and reproduced every ink box, Σ alpha and changed-cell
count, and confirmed the oracle references no `kge.*` type.

### Carry-forward

- **E3**: the GL fixture (`installGl`, `RecordingGLService`, the handle
  factories) moves into `kge-test-support` when the decal test needs it; the
  coverage texture, `drawStringDecal*` and the float scale follow the round E3
  touch-point.
- **`kge-core` blendAlpha determinism** (above): the same hazard, its own round.
- **Text API unification** (above): after the decided steps, starting from (a′).
