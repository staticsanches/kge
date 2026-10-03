# `kge-text-ttf` — micro-plan, round E3 (coverage texture + decal)

**Date:** 2026-10-01. Round **E3** of the text work, the last of the `E` split:
the one-channel coverage texture at the upload seam, the per-glyph
`DecalInstance`s and the addon's decal variants. Rounds: A bitmap (core) → B
scaffold → C face + shaping + layout → D raster + atlas → E1 CPU blit + addons →
E2 golden harness + text goldens → **E3 coverage texture + decal**. Context: the
round E3 touch-point (`docs/plans/2026-10-01-r6-round-e3-touchpoint.md` — all
nine decisions are settled there), the round E touch-point, the E1 micro-plan,
and decisions chunks `36`, `38`, `39`.

## Scope

Module + two core enablers + the shared fixture move + one benchmark cell. No
new engine dependency. The dependency changes are test-infrastructure only:
`kge-test-support` gains compile-only LWJGL in jvmMain (its consumers' test
runtimes provide the natives), and `kge-text-ttf` gains a `webTest` source set
with `kotlin.browser` (the core's `webTest` wiring is the template).

Public additions: six `GL` constants, `Layer.decalInstances` reopened under
`@KGESensitiveAPI`, `GLService.getInteger`, `TtfTextService.drawStringDecal`, and
the addon's `drawStringDecal` over `HasWindow`/`HasLayers`. Everything else —
the carrier, the packing, the swizzle application, the bookkeeping — stays
module-`internal`, and the carrier's `Texture`/`Decal` objects are file-private.

## Surfaces

Core, `GL.kt` (constants only, grouped as the file groups them):

```kotlin
const val RED: GLenum = 0x1903                // pixel format, beside RGBA
const val R8: GLenum = 0x8229                 // sized internal format
const val TEXTURE_SWIZZLE_R: GLenum = 0x8E42  // texture swizzle (new group)
const val TEXTURE_SWIZZLE_G: GLenum = 0x8E43
const val TEXTURE_SWIZZLE_B: GLenum = 0x8E44
const val TEXTURE_SWIZZLE_A: GLenum = 0x8E45
const val MAX_TEXTURE_SIZE: GLenum = 0x0D33   // capability query
```

Core, `GLService.kt` — one method, the measurement's query seam (the
touch-point's decision 6 amends decision 2's "constants only" by this much;
recorded in the entry):

```kotlin
/** The integer value of [pname]; only for pnames whose value is an integer. */
fun getInteger(pname: GLenum): GLint
```

Core, `Layer.kt` — the list opens, no behavior change:

```kotlin
@KGESensitiveAPI
val decalInstances: MutableList<DecalInstance>  // was internal
```

Module, `TtfTextService.kt` gains (C7's decal signature with the `Font` in
place of the scope, round D decision 6):

```kotlin
fun drawStringDecal(
    font: Font, position: Float2D, text: String, sizePx: Int, color: Pixel,
    scale: Float2D, tabSizeInSpaces: Int, screenSize: Int2D,
    decalMode: Decal.Mode, decalStructure: Decal.Structure,
    decalInstanceCollector: (DecalInstance) -> Unit,
)
```

Module, `TtfDrawStringAddon.kt` — the interface gains `HasWindow`, `HasLayers`
and `@OptIn(KGESensitiveAPI::class)` at the declaration site (decision 3), and
one member:

```kotlin
fun drawStringDecal(
    font: Font, position: Float2D, text: String, sizePx: Int,
    color: Pixel = Colors.WHITE, scale: Float2D = Float2D(1f, 1f),
) {
    TtfTextService.drawStringDecal(
        font, position, text, sizePx, color, scale, tabSizeInSpaces,
        window.screenSize, decalMode, decalStructure,
        layers.target.decalInstances::add,
    )
}
```

Module, `Font.kt` gains one internal member beside `atlas`:

```kotlin
internal fun gpuAtlas(sizePx: Int): GlyphAtlasGpu  // lazy, like the atlas
```

## Files

| file | change |
|---|---|
| `kge-core/…/gl/GL.kt` | the seven constants |
| `kge-core/…/gl/service/GLService.kt` | `getInteger` + the companion forwarder |
| `kge-core/…/gl/service/LwjglGLService.kt`, `WebGLService.kt` | the `getInteger` actuals |
| `kge-core/…/engine/layer/Layer.kt` | `decalInstances` opens under the marker |
| `kge-test-support/…/gl/*`, `…/engine/*` | the fixtures move in from `kge-core`'s test source sets |
| `kge-test-support/build.gradle.kts` | jvmMain compile-only LWJGL |
| `kge-text-ttf/…/GlyphAtlasGpu.kt` | new: the carrier + the one expect fact |
| `kge-text-ttf/…/Font.kt` | `gpuAtlas` + the close order |
| `kge-text-ttf/…/TextDraw.kt` | `drawStringDecalText` (the decal walk consumer) |
| `kge-text-ttf/…/TtfTextService.kt` | the member + the default's body |
| `kge-text-ttf/…/TtfDrawStringAddon.kt` | the roles + the member |
| `kge-text-ttf/src/jvmTest/…/CoverageTextureSmokeTest.kt` | new: the real-GL smoke |
| `kge-text-ttf/src/webTest/…` | new: the web smoke (the SwiftShader launcher landed in step 3) |
| `kge-text-ttf/build.gradle.kts` | the `webTest` source set |
| `kge-benchmark/…` | the upload-policy lever + the cell |

## The carrier (`GlyphAtlasGpu`)

One per `(Font, sizePx)`, created at the **first decal draw** for that size —
the GPU shadow of the CPU atlas, never touched by the CPU path:

- **The one platform fact** is `internal expect val coverageTextureIsSingleChannel: Boolean`
  (JVM `true`, web `false`); everything else derives from it in file-private
  code: the internal format (`R8`/`RGBA`), the upload format (`RED`/`RGBA`), the
  swizzle application (four `texParameteri` calls, `ONE/ONE/ONE/RED`, JVM only),
  and the packing walk (the chart's alpha byte per pixel vs. the chart's RGBA
  pixel). One expect declaration, one seam.
- **Per chart**, created lazily as the atlas's chart list grows: raw
  `GL.createTexture()` → `texParameteri` MAG/MIN `NEAREST`, WRAP S/T
  `CLAMP_TO_EDGE` → the swizzle calls (JVM) → `texImage2D(…, internalFormat,
  512, 512, 0, uploadFormat, UNSIGNED_BYTE, null)` → the handle wrapped in a
  `ResourceWrapper` + the core `Texture` (the sensitive constructor — the
  documented external-backend seam; the carrier file opts in) → `Decal(texture,
  chartSprite)` (the sensitive constructor). The failure path closes the
  texture (`letClosingIfFailed`).
- **Per placed glyph**, once: the box's bytes packed from the chart `Sprite`'s
  public per-pixel surface into a scratch buffer (`BufferService.allocate`,
  grown by close-and-reallocate when a larger box appears, closed with the
  carrier), then `texture.apply()` + `texSubImage2D(TEXTURE_2D, 0, box.x,
  box.y, box.w, box.h, uploadFormat, UNSIGNED_BYTE, scratch)`. The bookkeeping
  is a `MutableSet<AtlasGlyph.Placed>` — placements are unique by construction.
- **One internal entry point**: `fun decalFor(placed: AtlasGlyph.Placed): Decal`
  — ensures the chart's decal exists, uploads the box if new, returns the decal.
  Everything else is file-private.
- **`close()`** (idempotent, fail-fast after): every chart `Decal` (whose
  `close` deletes the texture) and the scratch buffer. `Font.close()` runs
  carriers → atlases → face. The context is current at every allocation (draw
  time) and at engine-teardown close (the `LayerStack` precedent).
- `Decal.update()`/`updateSprite()` are never called on these decals — the
  carrier is the single writer; a recorder test pins that no `texImage2D`
  follows the creation one.

## The decal draw

`drawStringDecalText` (in `TextDraw.kt`, `internal`) reuses the walk with
`(0, 0)` so the pens are line-relative, and applies the position outside:

```kotlin
font.walkText(text, sizePx, tabSizeInSpaces, 0, 0) { glyph, penX, penY ->
    val placed = font.glyph(sizePx, glyph.glyphId)
    if (placed is AtlasGlyph.Placed) {
        val decal = font.gpuAtlas(sizePx).decalFor(placed)
        collector(
            DrawPartialDecalService.drawPartialDecal(
                position = position + Float2D(
                    (penX + glyph.offset.x) * scale.x + placed.bearing.x * scale.x,
                    (penY - glyph.offset.y) * scale.y + placed.bearing.y * scale.y,
                ),
                decal = decal,
                sourcePosition = placed.source.asFloat2D(),
                sourceSize = placed.size.asFloat2D(),
                scale = scale, tint = color,
                mode = decalMode, structure = decalStructure,
                viewport = screenSize,
            ),
        )
    }
}
```

- **Float, unsnapped**: the destination keeps the walk's fractional pen; the
  service quantises (its own tested contract). The CPU path's `roundToInt` snap
  is E1's, not repeated here.
- **The tint is plain** — the coverage lives in the texture, so the GPU path has
  no `CoverageTint` and no `Pixel.Mode`; `decalMode`/`decalStructure` are
  carried unchanged (C7's decal path).
- **`Blank` glyphs are skipped** (the pen already moved); there is no scale
  guard — C7's/olc's decal path has none, and a degenerate scale draws a
  degenerate quad; empty text collects nothing.
- **The tab stops are the walk's** — line-relative, the same stops the CPU path
  measures, because the walk is driven with `(0, 0)`.

## Pinned expectations (the derivation rule)

Every geometry expectation is **derived by hand** from the round C advance pins
and round D's raster table (chunk `36`) — never pasted from the engine's output.
The instance test's shape is a **comparison**: the collected instance's
vertices must equal the vertices of a hand-built
`DrawPartialDecalService.drawPartialDecal(derivedDest, decal, derivedSource,
derivedSize, …)` — the module's contract is the destination derivation; the
quantise stays the service's own tested behavior. Worked example, `"A"` at
16 px, position `(2, 3)`, scale `(1, 1)`, viewport 30x24: pen `(0, 14.84375)`,
offset `(0, 0)`, bearing `(0, −12)` → destination `(2, 5.84375)` → the
service's quantise lands the quad on pixels `(2, 6)–(13.5, 18)` — the same cell
the CPU path paints (`round(5.84375) = 6`); the half-pixel right expansion is
the service's ceil, not the module's.

The recorder tests pin the **upload sequence** (branching on the expect fact,
one common test on all three targets):

- creation: `createTexture` → the four filter/wrap `texParameteri` → the four
  swizzle `texParameteri` (single-channel only) → `texImage2D(…, R8|RGBA, 512,
  512, 0, RED|RGBA, UNSIGNED_BYTE, null)`;
- per new glyph: `bindTexture` + one `texSubImage2D` whose box is the placed
  box and whose bytes equal the chart's alpha (single-channel) / RGBA pixels —
  read back through the chart's public surface, not hand-pasted;
- the client buffer is packed at the **row stride the driver assumes**: with the
  default `GL_UNPACK_ALIGNMENT` of 4 every row starts on a 4-byte boundary, so
  the stride is `align4(width * bytesPerPixel)` — a no-op on the RGBA path and
  padding on the single-channel one. The bytes are asserted *per row at that
  stride*; a flat-buffer assertion pins a layout the driver never reads. The
  step-4 smoke is what observes the real stride;
- a second draw of the same text uploads nothing; a new glyph uploads exactly
  its box; **no `texImage2D` after the creation one**;
- `Font.close()` records one `deleteTexture` per chart texture, and the
  counting-allocator override (T2) observes the scratch buffer's release;
- drawing decals after `Font.close()` fails fast.

## Steps (TDD: red → green per feature)

1. **The fixture move** (refactor — no red; the core suite is the contract):
   `installGl`/`RecordingGLService`/`RecordedGLCall`/the handle factories into
   `kge-test-support`'s `…testsupport.gl`, `installDriver`/`FakeDriverService`/
   `RecordingDriver` into `…testsupport.engine`, per-platform actuals included;
   `kge-core`'s tests import from there and stay green.
2. **The core enablers** (no independent red — recorded honestly, the E1
   precedent; the consumers' red tests below are their verification): the seven
   `GL` constants, `GLService.getInteger` (+ actuals, + the recorder's record),
   `Layer.decalInstances` under the marker.
3. **The carrier** (red → green): `GlyphAtlasGpu`, the expect fact,
   `Font.gpuAtlas` and the close order — the recorder tests above. Red: the
   carrier does not exist.
4. **The JVM smoke** (verification against the real driver — the step that
   catches a wrong swizzle or format the recorder cannot): `GlfwTestDevice`
   moves to `kge-test-support` jvmMain; the module's jvmTest creates the
   carrier against the hidden context, uploads a glyph box, draws through
   `Renderer.drawDecal`, reads back, and asserts structurally — ink at the
   quantised box, the background preserved elsewhere. `detect() == null`
   (hosted macOS) skips.
5. **`drawStringDecal`** (red → green): the comparison-instance tests (the
   destination derivation), `Blank` skipped, walk order, `Float2D` scale
   applied per axis, `decalMode`/`decalStructure`/`viewport` passed through,
   empty text collects nothing.
6. **The addon's decal variant** (red → green): a headless `Engine` host
   (scripted `Engine` subclass + `installDriver(RecordingDriver())` +
   `installGl()`), one frame whose `onUpdate` calls `drawStringDecal` and
   captures `layers.target.decalInstances` before the flush; the queue holds
   the instances, the viewport is `window.screenSize`, the modes are the host's;
   after the frame the recorder shows one `drawArrays` per instance and the
   queue is empty; the CPU variants are unaffected.
7. **The web smoke** (verification): the module's `webTest` source set (its
   `kotlin.browser` dependency and `karma.config.d` launcher landed in step 3),
   the same structural assertions through the RGBA8 region path.
8. **The benchmark cell** (measurement, not TDD): a benchmark-only
   `GLService` decorator that keeps a CPU shadow and re-issues every
   `texSubImage2D` as a full `texImage2D` from the shadow — the "full
   re-upload" policy with correct content — selected by the query string (the
   chunk 32 pattern); the cell prices region vs. full at 512 on both platforms;
   `getInteger(MAX_TEXTURE_SIZE)` recorded in the log.
9. **Gate, review, decisions entry, commit**: `tools/gradle build`; the
   reported counts read and their XMLs checked against the last source edit
   (the `#35` + `#38` rules); the entry (chunk `40`) staged before the final
   review round; the marker; one squashed commit.

## Out of scope (round E3)

Line breaking/word wrap, rotation, text entry and the console; SDF/MSDF; chart
sizing or a size cap; variable-font axes; the text API unification (its
touch-point follows this round, from option (a′)); batching of per-glyph
instances; a text workload beyond the upload-policy cell; the chart-count/
occupancy measurement battery (deferred with the closed `CHART_SIZE` decision).

## Gate

`tools/gradle build` green: every target's tests (both browser suites
reporting the new tests, never merely exiting 0), ktlint, the metadata/kLIB
compilation, `buildSrcCheck`. The E2 goldens must not move: the CPU path is
untouched by this round.
