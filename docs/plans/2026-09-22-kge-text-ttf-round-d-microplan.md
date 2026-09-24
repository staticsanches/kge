# `kge-text-ttf` — micro-plan, round D (FreeType rasterization + glyph atlas)

**Date:** 2026-09-22. Round **D** of the text work: FreeType becomes production
code, the seam gains rasterization, and `Font` grows the per-size glyph atlas
plus the module's capability service. Rounds: A bitmap (core) → B scaffold →
C face + shaping + layout → **D raster + atlas** → E blit + addons + decal.
Context: the round D touch-point (`docs/plans/2026-09-22-r6-round-d-touchpoint.md`
— stack, seam, atlas, service and the verified FreeType package facts), the R6
touch-point, the round C micro-plan and decisions chunks `31`, `34`, `35`.

## Scope

The seam's third operation, the coverage carrier, the atlas/cache and the
service with its scope registration. **No blit, no addons, no
`drawString*`/`getTextSize*`, no decal** — those are E. The only public changes
are `Font.load` becoming suspend and the new `TtfTextService`.

## Build changes (configuration, not TDD)

1. **FreeType moves to production.** `jvmMain` takes `libs.lwjgl.freetype` and
   its natives classifier (`runtimeOnly`, mirroring HarfBuzz); `webMain` takes
   the `@zkl2333/freetype-wasm` npm dependency and moves
   `FreeTypeWebExternals.kt` out of `webTest`. The round B smoke tests stay.
2. **`webMain` takes `libs.kotlinx.coroutines.core`** (`Promise.await`,
   `Mutex`); `webTest` keeps its own copy for the concurrency test.
3. `kge-text-ttf:build` stays green with rounds B/C' tests intact before any
   production change lands (step 1's own check).

## The seam (`expect`/`actual`, internal)

```kotlin
internal expect class NativeFace {
    fun shape(codePoints: IntArray, sizePx: Int): List<ShapedGlyph>
    fun metrics(sizePx: Int): TextMetrics
    fun rasterize(glyphId: Int, sizePx: Int): GlyphCoverage
}

internal suspend expect fun createNativeFace(bytes: ResourceWrapper<ByteBuffer>): NativeFace
internal expect fun closeNativeFace(face: NativeFace)

/** One rendered glyph: row-major, top-down, one coverage byte per pixel. */
internal class GlyphCoverage(
    val width: Int,
    val height: Int,
    /** Pen to bitmap top-left, in whole pixels, y down. */
    val bearing: Int2D,
    /** `width * height` bytes; empty when the glyph has no ink. */
    val coverage: ByteArray,
)
```

- **`createNativeFace` becomes `suspend`** (decision 2): the JVM actual never
  suspends; the web actual awaits the shared module. `shape`/`metrics`/
  `rasterize` stay synchronous.
- **The library is process-scoped and single-flight** (decision 3). JVM: a
  file-private `FT_Library` behind `by lazy`. Web: a file-private
  `Mutex` + nullable module in `freeTypeModule()`, which is `internal` so
  `webTest` can pin that two resolutions are the same instance. `initFreeType()`
  is **not** idempotent — the wrapper builds a new wasm instance per call.
- **JVM rasterize:** `FT_Load_Glyph(face, glyphId, FT_LOAD_DEFAULT)` then
  `FT_Render_Glyph(slot, FT_RENDER_MODE_NORMAL)` — the same two calls the web
  wrapper makes, so parity is by construction. `FT_Set_Pixel_Sizes(face, 0,
  sizePx)` only when the size changes (file-private `lastSizePx`). Read
  `width`/`rows`/signed `pitch` from the bitmap and copy rows top-down (`pitch <
  0` starts at the last row); alpha is `num_grays == 256 ? byte : byte * 256 /
  num_grays`; bearing is `Int2D(bitmap_left, -bitmap_top)`.
- **Web rasterize:** `face.setPixelSize(sizePx)` on a size change, then
  `face.loadGlyph(...)` — its defaults are already `LOAD_DEFAULT` + render
  `RENDER_MODE_NORMAL`. The returned buffer is a copy out of the heap but **not**
  row-normalized: the released 2.14.3 dist slices `abs(pitch) * rows` from the
  buffer pointer and ignores the sign, so the actual reverses the row order when
  `pitch < 0`, exactly as the JVM actual does; bearing is
  `(bitmapLeft, -bitmapTop)`.
- **The `loadGlyph` options object uses the wasm-native plain-object literal.**
  `@JsPlainObject` in Kotlin 2.4.10 is a compiler plugin that does not serve
  wasmJs: its Gradle subplugin applies only to `KotlinPlatformType.js`, and
  forcing it through the compiler-plugin classpath makes the FIR generator
  resolve `kotlin.js` and call `single()` on an empty list — the wasm stdlib has
  no such package, so the compiler fails internally. Both paths were measured,
  so neither is re-tried. The options type is an `internal external interface`
  built by `js("({ index: index })")`, which compiles and runs on both web
  targets; the wrapper's `LOAD_DEFAULT` + render + `RENDER_MODE_NORMAL` defaults
  stay in force and `renderMode` is not declared, because it is never set.
  (Contrary to the root comment's prior, the root `apply false` arrangement
  configures cleanly in this build; the plugin's platform applicability, not the
  classloader, is what rules it out.)
- **The web load path opens both faces while the payload is alive.**
  `createNativeFace` builds the HarfBuzz face and then the FreeType face
  (`newFace` copies the bytes into the wasm heap) from the same staging buffer,
  and only then closes it; the FT face keeps its own heap copy until
  `destroy()`. No top-level await is added anywhere: the wasm is fetched inside
  the first suspend load, so the web bundle entry stays exactly as round C left
  it (nothing new for the `#35` guard to catch).
- **Only `FT_PIXEL_MODE_GRAY` is accepted** on both actuals: a check with a
  clear message. Nothing in the module ships a bitmap-only font, so the branch
  has no fixture; the decisions entry records that instead of pretending it is
  covered.
- **Close order is the resource contract:** JVM `release()` destroys the FT
  face, then the HarfBuzz font/face/blob, then the payload wrapper; web drops
  the FT face via its `destroy()` and then the HarfBuzz reference. We never call
  `FreeType.destroy()` — it destroys every face of the shared module.
- Externals (web): default `initFreeType`, the `FreeType`/`Face` classes, a
  `LoadedGlyph` interface (`width`, `rows`, `pitch`, `pixelMode`, `numGrays`,
  `bitmapLeft`, `bitmapTop`, `buffer: Uint8Array`) and the `LoadGlyphOptions`
  plain-object interface the literal builder fills (see the mechanism bullet).

## The atlas (`commonMain`, internal)

```kotlin
internal class GlyphAtlas internal constructor(
    private val sizePx: Int,
    private val rasterize: (glyphId: Int) -> GlyphCoverage,
) : KGEResource {
    internal fun glyph(glyphId: Int): AtlasGlyph
    internal val charts: List<Sprite>
}

internal sealed interface AtlasGlyph {
    /** A glyph with no ink (space, control): nothing to draw. */
    data object Blank : AtlasGlyph

    data class Placed(
        val chartIndex: Int,
        val source: Int2D,
        val size: Int2D,
        val bearing: Int2D,
    ) : AtlasGlyph
}
```

- **The rasterizer is a constructor parameter**, so the atlas is pure logic and
  its tests need no natives; `Font` passes
  `{ face.resource.rasterize(it, sizePx) }`.
- Charts are `512x512` `Sprite`s (`SpriteService.create`, `SampleMode.NORMAL`,
  named `"glyph atlas (<n>px) #<i>"` for leak messages). A chart packs shelf
  rows: the first row that fits the glyph in both dimensions, else a new row at
  the glyph's height below the last, else a new chart.
- Pixels: alpha is the coverage byte, RGB opaque white; `0 -> Colors.TRANSPARENT`
  and `255 -> Colors.WHITE`, otherwise `Pixel.rgba(255, 255, 255, alpha)` (the
  `fontDevelopment` encoding), written with `uncheckedSet`.
- **A blank glyph is cached** as `Blank`, so a space is rasterized once and
  never grows a chart.
- A coverage wider or taller than a chart fails fast (`require`) with a clear
  message.
- Untouched chart pixels stay unspecified (`SpriteService` content is
  unspecified): only placed boxes are ever read, by E with NEAREST at 1:1.
- `close()` closes the charts (`closeAll`) and is idempotent; `glyph` after
  close fails fast.
- `charts` is `internal` because E needs the sheets for the decal and the tests
  read the pixels; it is the module's own surface, never public (decision 4).

## `Font` changes

- `suspend fun load(bytes)` / `suspend fun load(base64)`; the constructor keeps
  the face wrapper as its only native owner.
- `Font : KGEResource` is implemented explicitly instead of `by face`, so
  `close()` closes the atlas map first and the face wrapper after (idempotent,
  `closeAll` keeps going past a failure). The leak report still names the face.
- `internal fun glyph(sizePx: Int, glyphId: Int): AtlasGlyph`: `checkNotReleased`
  and a positive `sizePx`, then the lazily created per-size atlas
  (`mutableMapOf<Int, GlyphAtlas>` inside `Font`).
- `internal fun atlas(sizePx: Int): GlyphAtlas?` for the tests' pixel reads;
  `checkNotReleased()` moves to cover the raster path too.

## The service (`TtfTextService`)

```kotlin
interface TtfTextService : KGEOverridable {
    /** Adopts [font] into [scope]: the scope owns and closes it. */
    fun createResources(scope: ResourceScope, font: Font)

    companion object :
        KGEOverridable.Proxy<TtfTextService>(TtfTextService::class, TtfTextServiceDefault),
        TtfTextService {
        override fun createResources(scope: ResourceScope, font: Font) =
            delegate.createResources(scope, font)
    }
}
```

- Default: `scope.register(FontKey, font)`, with `private object FontKey :
  ResourceScope.Key<Font>`; `internal fun fontIn(scope): Font = scope.get(FontKey)`
  for the module's draws and the tests.
- `createResources` is the C7 name even though it adopts instead of builds; the
  service stays stateless, and E's draws take the `Font` directly (decision 6).
- **Extension-contract test:** an overriding decorator registers a different
  font, and `fontIn(scope)` reports the decorator's font; `resetAll()` in a
  `finally`.

## Fixture and the pinned raster contract

`commonTest` keeps using `robotoFontBytes()`/`Roboto.variableFont`. The raster
constants are measured on the shipped **Roboto 3.015 default instance**
(`wght 400`) and pinned beside `Roboto.FAMILY`/`Roboto.VERSION`, so a font bump
fails as a fixture change.

| assertion | check |
|---|---|
| bitmap box | `"A"`, `"V"`, `"o"`, `"g"`, `"1"`, `"é"` at 16 px: `width x rows` each |
| bearing | the same glyphs: `bitmap_left` / `bitmap_top` |
| coverage | the same glyphs: Σ coverage bytes |
| scale | the same text at 32 px: different box, coverage scales, no clipping |
| blank | `" "` at 16 px: zero-size coverage, and its `AtlasGlyph` is `Blank` |

Procedure: print the values from `jvmTest` first, then confirm on js and wasmJs;
**paste only once the three agree**. A disagreement is a finding (a per-target
constant is not an option) — both sides ship FreeType 2.14.3, and that
version-equality is what the pin depends on.

## Steps (TDD: red → green per feature)

1. **Build wiring** (above). Config only; the round B/C suite is the net.
2. **Suspend load + seam factory** (refactor under the existing suite): make
   `Font.load` and `createNativeFace` suspend; the round C tests compile and
   pass unchanged. Red is the compiler over the old sync seam.
3. **Web single-flight module** (red → green): a `webTest` test asserting that
   two `freeTypeModule()` resolutions — sequential and concurrent — are the same
   instance. Red: the accessor does not exist / each call inits. Green: the
   `Mutex`-guarded cache. The round B web smoke test resolves through the shared
   accessor rather than calling `initFreeType()` itself, so it does not
   instantiate a second module.
4. **`GlyphCoverage` + JVM `rasterize`** (red → green): `GlyphRasterTest`
   asserts the fixture rasterizes and, after the measurement step, the pinned
   constants; red is the missing `actual`. Pin on JVM, then run the same
   `commonTest` on both web targets in step 5.
5. **Web `rasterize`** (red → green): the externals and the web actual; the same
   `GlyphRasterTest` must pass on js and wasmJs with the JVM-measured values.
   Any divergence is reported as a finding, not papered over per target.
6. **Atlas** (red → green): `GlyphAtlasTest` with a stub rasterizer —
   deterministic placement of a fixed glyph sequence, pixel content (white RGB,
   alpha = coverage, transparent for zero coverage), Σ alpha equal to Σ
   coverage, `Blank` for an empty coverage and no chart growth, cache hit on
   repeat (`shouldBeSameInstanceAs`), a second chart when the first fills, an
   oversized coverage failing fast, close idempotent.
7. **`Font` wiring** (red → green): lazy per-size atlas, `glyph` after close
   fails fast, `Font.close()` closes the atlases (extend `FontResourceTest`/
   `FontLeakReportTest`), shaping still returns round C's pinned run after a
   rasterization at the same and at a different size.
8. **Service** (red → green): `TtfTextServiceTest` — `createResources` makes the
   scope the owner, closing the scope closes the font (use after close fails
   fast), and the extension contract above.
9. **Gate, review, decisions entry, commit.** `tools/gradle build` once, then
   the two axes in parallel; the `#35` guard makes the web counts trustworthy.

## Out of scope (round D)

Blit (CPU and decal), `drawString*`/`getTextSize*`, addons, line breaking, tabs,
subpixel positioning, hinting/rendering-mode options, MONO and color-bitmap
glyphs, variable-font axes, padding for scaled sampling, the golden harness, and
any public raster/atlas API.

## Gate

`tools/gradle build` green: every target's tests (both browser suites included),
ktlint, the `webMain` metadata compilation and `buildSrcCheck`; test tasks
always execute, so no `--rerun-tasks` is needed — but the reported counts are
read, per the `#35` guard.
