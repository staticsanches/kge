# `kge-text-ttf` — micro-plan, round C (face + shaping + layout)

**Date:** 2026-09-17. Round **C** of the text work: the module's first production
code — the native face, HarfBuzz shaping and the public layout type. Rounds:
A bitmap (core) → B scaffold → **C face + shaping + layout** → D raster +
atlas/cache → E blit + addons + decal. Context:
`docs/plans/2026-09-16-r6-text-touchpoint.md` (Revision section) and the round B
micro-plan.

> **Revised 2026-09-19 — the supersession below is applied in this document.**
> The bundled-fonts round landed first (`kge-font-roboto`: Roboto 3.015 /
> Roboto Mono 3.001 variable, OFL-1.1), so: the "Test font" section is rewritten
> around that module (no committed TTF, no inline generator), the pinned
> contract is marked for re-measurement on Roboto 3.015's **default instance**,
> `Font` gains the base64 entry point and the `BufferService` allocation, and
> the seam takes the retained buffer instead of `ByteArray`. The R6 touch-point
> carries the matching revision, including the deferred variable-axes shape.
> Context: `docs/plans/2026-09-17-fonts-module-touchpoint.md`,
> `docs/plans/2026-09-17-font-bundle-findings.md`,
> `docs/plans/2026-09-17-resource-packaging-mechanism.md` §3.2 and
> `docs/plans/2026-09-19-kge-font-roboto-microplan.md`.

## Scope

The seam's first two operations (open/close face, shape), the public `Font`
resource created from decoded bytes, and the public shaped-run layout. **No
rasterization, no atlas, no blit, no addons** — those are D/E. The module gains
its `kge-core` dependency and moves its dependencies from the test source sets
to the production ones (decision 3 of round B).

## Build changes

1. **`kge-core` dependency, as `api`.** The public API names `KGEResource`
   (`kge-core`) and `Float2D` (`kge-core`), so consumers compiling against
   `kge-text-ttf` need them on the compile classpath; `implementation` would hide
   them. `kge-benchmark` uses `implementation` because its API does not expose
   core types — that precedent does not apply here.
2. **Dependencies move to production source sets.** `jvmMain` takes
   `lwjgl-harfbuzz` (+ `lwjgl-core`, the BOM and the natives classifier);
   `webMain` takes the two npm packages **and the HarfBuzz/FreeType externals**
   (round B left them in `webTest`); `commonTest` takes
   `project(":kge-font-roboto")` as the font fixture. `jvmTest` keeps only the
   HarfBuzz/FreeType **runtime** natives it needs to execute, inherited from
   `jvmMain` per the `kge-core` precedent. Round B's comment claiming the deps
   are "test-only" is superseded.

## The seam (`expect`/`actual`, internal)

The touch-point's seam is ~4 operations (open/close face, `shape`,
`rasterizeGlyph`); C implements everything but `rasterizeGlyph`, which arrives in
D. `nativeGlyphCount` is an extra C-only operation: it exists to validate the
face (finding 3), so it may collapse into the open+validate step. Follow the
`ByteBuffer` precedent (`expect` in `commonMain`, `actual typealias`/`actual
class` in `jvmMain` and the shared `webMain`).

```kotlin
// commonMain — internal
internal expect class NativeFace                       // opaque handle owner
internal expect fun createNativeFace(bytes: ResourceWrapper<ByteBuffer>, faceIndex: Int): NativeFace
internal expect fun closeNativeFace(face: NativeFace)
internal expect fun shapeRun(face: NativeFace, codePoints: IntArray, sizePx: Int): NativeRun
internal expect fun faceMetrics(face: NativeFace, sizePx: Int): NativeMetrics
internal expect fun nativeGlyphCount(face: NativeFace): Int
```

- **`bytes` is the `BufferService`-allocated wrapper** (`kge-core`), decoded
  from the caller's bytes or base64 in `commonMain`; the seam never receives a
  bare array, because the JVM side must retain it and the web side must release
  it.
- **Text is fed as code points (`IntArray`) on both backends.** This is not
  cosmetic — see finding 2. Both sides were verified to produce identical
  clusters with `hb_buffer_add_utf32` / `addCodePoints`.
- **Scale is `sizePx * 64`** (HarfBuzz 26.6 fixed point) on both sides;
  advances/offsets convert back with `/ 64f`.
- **Explicit `LTR` + `Latn`**, never `guess_segment_properties`: guessing
  depends on the platform's Unicode tables and the segment content, so it is not
  a contract a cross-target test can pin. Explicit properties were verified to
  give the same result as guessing for this run, and are deterministic.
- JVM: the retained direct `ByteBuffer` **is** the blob's data, with
  `HB_MEMORY_MODE_READONLY` — HarfBuzz references it and owns nothing, so the
  face keeps the `ResourceWrapper` alive and the wrapper is closed **last**
  (font, face, blob, buffer). A non-direct buffer cannot be used here: LWJGL
  would stage a temporary that the retained blob outlives. Then
  `hb_face_create` → `hb_font_create`.
  `hb_font_create` installs the OpenType funcs lazily from the face's tables —
  no `hb_ot_font_set_funcs` call is needed (verified: shaping from a bare
  `hb_font_create` matched the FT-backed path's ids and advances). (The
  `MemoryStack`-allocated blob buffer must not be used for a ~490 KB font — it
  overflows the stack; the buffer comes from `BufferService`.)
- Web: `new Blob(uint8) → new Face(blob) → new Font(face)`, driven through
  `@file:JsModule("harfbuzzjs")` externals now in `webMain` (round B left them
  in `webTest`). `new Blob` copies the `Uint8Array` into wasm memory, so the
  engine buffer is staging and is closed right after the face exists; the
  `ByteArray` → `Uint8Array` conversion is a `webMain` detail.

**Non-obvious constraint found (do not plan around a deterministic HB close on
web):** `harfbuzzjs` exposes **no** `destroy`/`free` on `Blob`/`Face`/`Font`/
`Buffer` — release is a `FinalizationRegistry` callback. `KGEResource.close()`
on web therefore cannot release the HarfBuzz handles deterministically; it can
only drop the references. Record this in the KDoc rather than pretending
otherwise. On JVM the close is real and ordered.

## Public API (round C)

```kotlin
// package dev.staticsanches.kge.text.ttf
class Font : KGEResource {
    val faceIndex: Int
    fun shape(text: String, sizePx: Int): ShapedRun
    companion object {
        fun load(bytes: ByteArray, faceIndex: Int = 0): Font
        fun load(base64: List<String>, faceIndex: Int = 0): Font
    }
}

data class ShapedGlyph(
    val glyphId: Int,
    val cluster: Int,
    val offset: Float2D,
    val advance: Float2D,
)
data class ShapedRun(val glyphs: List<ShapedGlyph>, val metrics: TextMetrics)
data class TextMetrics(val ascender: Float, val descender: Float, val lineGap: Float)
```

- **`shape` takes `String`** and converts to code points internally; the seam
  takes `IntArray`.
- **`load(base64: List<String>)`** accepts the chunked form the data module
  emits, so the common case is `Font.load(Roboto.variableFont)`; decoding and
  the `BufferService` allocation happen inside (direct on JVM, staging on web),
  and the retained wrapper is what the native face owns and closes last.
- **`cluster` is a code point index** (verified identical on both backends), not
  a UTF-8/UTF-16 byte offset. It is exposed because D's glyph cache keys on the
  glyph id while the future addon layer needs cluster→character mapping.
- **Single run, no line breaking.** Newlines, tabs, multi-line metrics and the
  olc-like `drawString*`/`getTextSize*` defaults are the E addon layer's concern
  (olc's multi-line behavior is a draw-loop concern, not a shaping one).
  Extending the addon layer later is additive.
- **`sizePx` must be positive**; `load` must fail fast on bytes that are not a
  usable font (finding 3).
- The atlas and the `ResourceScope` registration of `Font` land in D, with the
  rest of the resource story. `Font` is a `KGEResource` from C on, so D adds
  rather than breaks.

## Test font (the shipped module, not committed here)

`commonTest` depends on `kge-font-roboto` and decodes the fixture from its
accessors: `Roboto.variableFont` (20 chunks) and `RobotoMono.variableFont`
(8 chunks), chunked base64 of the committed `Roboto[wdth,wght].ttf` /
`RobotoMono[wght].ttf`. `kotlin.io.encoding.Base64` (stdlib, already used by
`GoldenMatcher`) does the decoding; no new dependency, no generated accessor and
no committed TTF in this module. The module's own tests already pin the chunk
shape and the decoded fingerprints, so this round pins only what it consumes:
the shaping constants below, each asserted next to `Roboto.VERSION`/`FAMILY`, so
a future font bump fails as a **fixture change** instead of silently re-pinning
advances.

This is the compiler-side half of the packaging decision
(`docs/plans/2026-09-17-resource-packaging-mechanism.md` §3); the runtime half is
`Font.load(base64)`, which avoids materialising an intermediate `ByteArray`
beyond the decode itself.

## Pinned contract — to be re-measured on the shipped font

**Superseded 2026-09-19: the table below was measured on Roboto 2.137, which is
no longer the fixture — do not copy its numbers into tests.** It is retained as
the shape of the assertion and as evidence of the qualitative behaviours (kerning
on by default; the `"AéB"` cluster parity). Round C step 4 re-measures every
value on `Roboto[wdth,wght].ttf` (3.015) at its **default instance**
(`wght 400`, `wdth 100`), scale 16 px, and pins the result on JVM and both web
targets.

Superseded values — `shape("AV To Wave 123", 16)`, Roboto-Regular 2.137,
identical on JVM and web:

| field | value |
|---|---|
| `glyphId` | `37,58,4,56,83,4,59,69,90,73,4,21,22,23` |
| `advance` (26.6) | `625,652,234,562,584,254,893,550,490,543,254,575,575,575` |
| `offset` | all `0` |
| `cluster` | `0..13` |

`advance` as Float: `9.765625, 10.1875, 3.65625, 8.78125, 9.125, 3.96875,
13.953125, 8.59375, 7.65625, 8.484375, 3.96875, 8.984375 ×3`.
`metrics(16)`: ascender `950/64 = 14.84375`, descender `-250/64 = -3.90625`,
lineGap `0.0`. Kerning applied by default: `"A"` alone advances `668`, in `"AV"`
it advances `625` (−43). `"AéB"` → ids `37,675,38`, clusters `0,1,2`.

What step 4 must produce on 3.015, per assertion (values blank until measured):

| assertion | check |
|---|---|
| glyph ids, in order | `shape("AV To Wave 123", 16)` |
| advances (26.6 → `Float`) | the same run, per glyph |
| offsets | the same run — all zero for this text |
| clusters | the same run — `0..13` |
| kerning | `"AV"` first advance < `"A"` alone |
| cluster parity | `"AéB"` → `0,1,2` (finding 2: the code-point feed) |
| metrics | `metrics(16)` ascender/descender/lineGap, and that they scale with `sizePx` |
| fixture identity | `Roboto.FAMILY`/`VERSION` asserted beside the constants |

Procedure: the round B `jvmTest` wiring already loads LWJGL HarfBuzz, so print
the run there first; paste values only once JVM and web agree, and record a
disagreement as a finding rather than pinning per-target constants.

## Findings (measured; they correct the touch-point record)

1. **The touch-point's spike numbers do not match the font that was measured.**
   The spike records ids `[38,59,5,57,84,5,60,70,91,74,5,22,23,24]` — every value
   **+1** against the 2.137 fixture — and `7.75` at index 8 where that fixture
   gives `7.65625`. Three Roboto weights on `fontDevelopment` (`Regular`,
   `Light`, `Medium`) all give the same values, so the spike most likely used a
   different Roboto build. **Do not copy the spike's numbers into tests**; pin
   the values measured on the shipped 3.015 font, and treat the touch-point's
   parity claim as directionally sound (JVM == web holds) but its constants as
   stale.
2. **Feeding text differs by backend and silently changes `cluster`.** The JVM
   `hb_buffer_add_utf8(CharSequence)` binding encodes **UTF-8**; the web
   `Buffer.addText` encodes **UTF-16**. Real divergence: for `"AéB"` the JVM
   returns clusters `0,1,3` and the web `0,1,2`. HarfBuzz's `cluster` is a
   monotone index into the *fed* encoding, so the same text yields different
   cluster values per target. Feeding **code points on both sides** makes them
   agree (`0,1,2`, verified on both). This is the single most important thing
   this round must get right, and it is invisible to ASCII-only tests.
3. **Invalid bytes do not fail on either backend.** Junk bytes yield a face with
   `upem=1000`, `glyphCount=0` and a silent glyph `0` — no error. `load` must
   validate explicitly: JVM `hb_face_get_glyph_count == 0`, web
   `nominalGlyph(cp) === undefined` / a missing `cmap` table both discriminate
   junk from Roboto. Pin a fail-fast test.
4. **HarfBuzz versions differ by target** (JVM native **14.3.0**, web wasm
   **14.4.0**). Shaping still matches exactly for this font, but the parity test
   is a real cross-build claim, not a same-binary one. Pinning the re-measured
   values is what makes a later HarfBuzz bump visible.
5. **FreeType is not on the round C path.** Shaping reads the face's own tables;
   `hb_ft_font_create*` is unnecessary and would add a FreeType dependency to
   shaping. FreeType enters in D for rasterization. (`hb_ft_font_create_referenced`
   also proved unusable directly from LWJGL here.)
6. **Metrics come from `hb_font_get_h_extents` and follow the scale** (verified),
   so `sizePx` alone determines them.

## Decisions (recorded)

1. **Code-point text feed** (finding 2) — the seam takes `IntArray`, both
   backends use the utf32/codepoint path. This is a divergence from the old
   `fontDevelopment` `HarfBuzzShaper`, which used `hb_buffer_add_codepoints`
   with a shared `IntBuffer`; the code-point *feed* is retained, the
   `TextShaper`/`FPU`/`ScriptTag`/`GlyphAtlas` scaffolding is not (it was
   JVM-only, `java.nio`-bound, and its HarfBuzz path was deleted in `a867d1d`).
2. **Explicit `LTR`/`Latn`** rather than guessing — determinism across targets.
   `ScriptTag`'s 231-line script table is not ported; Latin-only is already the
   touch-point's scope decision.
3. **Float, not a fixed-point type.** `fontDevelopment`'s `FPU` (26.6) existed to
   avoid Float in the JVM-only pipeline. Here the values cross an `expect/actual`
   boundary and are consumed by `Float2D` in the public API, so the seam converts
   26.6 → `Float` at the boundary and `FPU` is not ported.
4. **`Font` from bytes or base64, not from a decoder.** The caller supplies the
   payload — decoded bytes, or the chunked base64 the data module emits — so the
   module does not depend on `ImageService`'s decoder generics and works
   identically with `BytesDecoder`, `FetchDecoder` or a test accessor.
5. **The non-deterministic web release is documented, not hidden** (seam notes).
6. **The payload is an engine resource.** `load` allocates through
   `BufferService` and the seam receives the `ResourceWrapper<ByteBuffer>`; JVM
   passes the direct buffer to HarfBuzz read-only and retains it, web copies
   into wasm memory and releases the staging buffer. Supersedes the 2026-09-17
   sketch whose seam took a `ByteArray` (mechanism §3.2).

## Steps (TDD: red → green per feature)

1. **Wire the build (config, not TDD).** Add `api(project(":kge-core"))`, move
   the deps and the web externals to `jvmMain`/`webMain`, add
   `project(":kge-font-roboto")` to `commonTest`. Verify
   `./gradlew :kge-text-ttf:build` stays green with the round B smoke tests
   intact.
2. **Public layout types (red → green).** Add `ShapedGlyph`/`ShapedRun`/
   `TextMetrics` with a `commonTest` test pinning their shape and the
   `Float2D`-typed `offset`/`advance`.
3. **Face + validation (red → green).** `Font.load(bytes|base64)` + `faceIndex`;
   test that `load(base64)` from the fixture matches `load(decoded)`, that the
   Roboto fixture loads, and that junk bytes fail fast (finding 3). Red:
   unresolved `load`. Green: seam `createNativeFace`/`closeNativeFace` on both
   targets.
4. **Shaping (red → green).** Re-measure the contract on the shipped font (see
   above), then pin it: ids, advances, offsets and clusters for
   `"AV To Wave 123"`, `"AV"` vs `"A"`/`"V"` kerning, and `"AéB"` clusters
   `0,1,2`, with `Roboto.FAMILY`/`VERSION` asserted beside them. Runs on JVM
   **and** both web targets from the shared `commonTest` suite. Red: `shape`
   unimplemented.
5. **Metrics (red → green).** `TextMetrics` at 16 px, and that they scale with
   `sizePx`.
6. **Resource behavior (red → green).** `close` is idempotent and use-after-close
   fails fast (the `KGEResource` contract); document the web GC caveat.

## Out of scope (round C)

- FreeType rasterization, glyph bitmaps/coverage, glyph cache/atlas, `Sprite`
  upload — round D.
- CPU/decal blit, `drawString*`/`getTextSize*` addons, `ResourceScope`
  registration of `Font` — round E (and D for the atlas they need).
- Multi-line, tabs, `scale`, bidi, script itemization, font fallback.
- Variable-font axes and named weights: the round shapes the **default instance**
  (`wght 400`); the deferred shape — discovery by reading `fvar`/`name`, one
  `Font` per instance over the same data — is in the R6 touch-point revision.
- A compatible API across bitmap (C7) and TTF text — the recorded follow-up.

## Gate

`./gradlew build` green — all three targets' tests, the `webMain` metadata
compilation and `buildSrcCheck` (wired into `check` 2026-09-19). Tests always
execute, so no `--rerun-tasks` is needed. Then the two-axis review, then the
decisions-log entry, then one commit for the round.
