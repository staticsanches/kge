# S5 (PNG codec) micro-plan

**Date:** 2026-09-07. Touch-point done (roadmap S5 entry; decisions in
`docs/decisions/phase-1.md`, this session). Concept: decode/encode PNG surfaces
to and from the C5 `Sprite`, plus loading from a typed source. Container = the
C3 `ByteBuffer`; codec is synchronous on both platforms (JVM STB, web pngjs
`.sync`); I/O via `PngSource`; coroutines `suspend` at the load boundary.

## Contract

```kotlin
// image/PngSource.kt — common
interface PngSource {
    /** Materializes the source bytes; the caller owns and must close the returned wrapper. */
    suspend fun read(): ResourceWrapper<ByteBuffer>
}

// image/PngCodecService.kt — common
interface PngCodecService : KGEOverridable {
    /** Decodes [data] (a PNG) into a [width]x[height] RGBA surface. */
    fun decode(data: ByteBuffer, sampleMode: Pixmap.SampleMode = NORMAL, name: String? = null): Sprite
    /** Encodes [sprite] into PNG bytes; the caller owns and must close the returned wrapper. */
    fun encode(sprite: Sprite): ResourceWrapper<ByteBuffer>
    /** Encodes [sprite] into a base64 PNG payload (portable download / convenience). */
    fun encodeToBase64(sprite: Sprite): String
    /** Loads a PNG from [source]: `source.read()` then `decode`; closes the read wrapper. */
    suspend fun load(source: PngSource, sampleMode: Pixmap.SampleMode = NORMAL, name: String? = null): Sprite
    companion object : KGEOverridable.Proxy<PngCodecService>(PngCodecService::class, pngCodecDefault) { ... }
}
```

Implementation notes:
- The default is `internal expect val pngCodecDefault: PngCodecService`
  (platform-defaulted service, per C3): JVM actual = STB; web actual (one
  `webMain` file for js + wasmJs) = pngjs.
- **Storage is byte-compatible between the kernel buffer and the decoders.**
  A `Sprite` pixel is the LE int at `(y*w+x)*4` → memory bytes R,G,B,A;
  pngjs `png.data` and STB output (requested `comp = 4`) are the same
  R,G,B,A row-major bytes. Decode/encode can move raw bytes in/out of the
  buffer without per-pixel conversion.
- **JVM decode (zero-copy, main's pattern):** `STBImage.stbi_load_from_memory`
  returns a native buffer; wrap it as the `Sprite`'s `ResourceWrapper` with a
  `KGECleanAction` calling `stbi_image_free` (the wrapper owns the STB memory
  — no extra copy, no leak on the `Sprite` closing it). Encode:
  `STBImageWrite.stbi_write_png_to_func` into an engine-allocated buffer.
- **Web decode/encode:** `PNG.sync.read`/`PNG.sync.write`; copy
  `png.data` (a `Uint8Array`) into/from the engine buffer's backing array.
- **`load` default:** `val bytes = source.read()` then
  `try { decode(bytes.resource, ...) } finally { bytes.close() }` — the read
  wrapper is always closed (C2 discipline; `letClosingIfFailed` on decode).

## TDD (kotest, commonTest on all targets; red → green per step)

Steps run codec-first (pure, no I/O), load second; gate at close.

1. **Add-time:** `kotlinx-coroutines-core` 1.11.0 to the catalog +
   `commonMain` deps. Verify base64: stdlib `kotlin.io.encoding.Base64` on all
   three targets (jvm/js/wasmJs) vs platform `atob`/`java.util.Base64` —
   record the finding. Add the fixture PNG.
2. **`PngSource`** — red: the interface + `read()`; green (compile).
3. **decode** — red: decode a tiny known-good PNG (embedded bytes, verified
   separately) → dimensions; row pixels equal expected RGBA; `sampleMode` and
   `name` land on the `Sprite` (`toString` carries the name). `decode` with
   garbage bytes throws. green.
4. **encode** — red: `encode(sprite)` → decode the result back (round-trip)
   → same dimensions and pixels; the returned wrapper closes. green. Round-trip
   is the independent decode proof (no fixture-oracle dependence).
5. **encodeToBase64** — red: sprite → base64 → decode the base64 payload →
   same pixels; base64 string is a valid PNG (magic bytes after decode).
   green.
6. **`load` + sources** — red: a common `PngSource` over base64 bytes → `load`
   returns the surface and closes the read wrapper (leak path: overridden
   `LeakReporterService` reports nothing after `load`; a source whose `read()`
   returns an open wrapper is closed by `load`). green. JVM `java.net.URL`
   source and web fetch source are platform tests (see below).
7. **Service seam** — extension-contract test: decorator `PngCodecService`
   overriding `decode` → observable change (the T2 pattern). `load` respects
   a decorator overriding `decode`.
8. **Platform I/O tests:** JVM (`jvmTest`) — `java.net.URL` source loads a
   classpath resource PNG (sync body); wasmJs/js (`webMain`+platform test) —
   fetch source against a data: URL or served fixture, `suspend` body.
9. **Gate** — `./gradlew build --rerun-tasks` green, then decisions-log entry.

## Files & open micro-details

- New common: `image/PngSource.kt`, `image/PngCodecService.kt` (+ defaults via
  expect/actual). New platform: `jvmMain` (STB actual + `java.net.URL`
  `PngSource`), `webMain` (pngjs actual + fetch `PngSource`), base64
  `PngSource` in common. Build: coroutines dep; `npm("pngjs", "7.0.0")` for
  js + wasmJs (one web target source set); JVM STB already on the LWJGL BOM
  (STB is an LWJGL module — add `lwjgl-stb`).
- Fixture PNG: a tiny known-good PNG (2x2, distinct RGBA) embedded in
  commonTest as bytes (generated offline, byte-verified) — resources don't
  read uniformly across jvm/js/wasm test classpaths. Round-trip (step 4) is
  the decode oracle that needs no external fixture.
- `name` default: decode sets it on the `Sprite`; a null `name` stays null
  (the resource wrapper message falls back to the size label, C5 behavior).
- base64 add-time result decides the common `PngSource` base64 impl and
  `encodeToBase64` (stdlib common vs platform actuals).
- Review / gate as usual: leak audit of decode (STB wrapper ownership), encode
  (output wrapper), `load` (read wrapper closed on every path incl. decode
  failure); every public parameter observable (sampleMode/name pinned).
