# Image service (S6) micro-plan (TDD)

**Date:** 2026-09-11. Touch-point decisions in
`docs/decisions/phase-1/19-image-s6.md` (plus the follow-up entry recorded this
session). Concept: retire the PNG-only `PngService` (S5) and introduce
`ImageService` — a platform-generic image codec seam with generic
`Decoder<T>`/`Encoder<T>`, suspend `load`/`save`, an RGBA-only `Sprite`, PNG+JPEG
uniform encode, and per-platform decode breadth. **Prerequisite:** the web
targets become **browser-only** (node dropped) so the browser-native
`createImageBitmap` decode is usable and testable in CI.

## Working mode

- **Step-by-step TDD.** One step = test (red) → run → implement (green on jvm +
  js browser + wasmJs browser) → mark. Doubts resolved in-session with the
  owner.
- **Implementation/test writing may be delegated** to a fresh sub-agent per step
  (context hygiene); the owner reviews the diff at the step boundary.
- **Two-axis review** (Standards + Spec) at the close, via two fresh general
  sub-agents, report + marker as usual.
- **Gate:** `./gradlew build --rerun-tasks` (jvm + js browser + wasmJs browser +
  ktlint + assemble/metadata). Only a force-executed green counts.
- **Sources:** olc v2.30 (`.tmp/olc/olcPixelGameEngine.h`) for pixel math;
  `main` for the old codec (`git show main:<path>`); the S5 code/test suite in
  the working tree as the migration base.

## Resume tracker

- [x] **0. Browser-only web targets + browser tests in CI** (infra prerequisite)
- [x] **1. `ImageService` seam** (`Decoder`/`Encoder`, `load`/`save`)
- [x] **2. Web codec spike** (`createImageBitmap` decode + canvas encode, js+wasm)
- [x] **3. `BytesDecoder`** + platform decode primitive (STB / browser)
- [x] **4. `PngEncoder`/`JpegEncoder`** + platform encode primitive
- [x] **5. `Base64Decoder`/`Base64PngEncoder`/`UrlDecoder`/`FetchDecoder`**
- [x] **6. Multi-format decode** (JVM STB breadth; browser `createImageBitmap` breadth)
- [x] **7. Retire S5** (`PngService`/`PngSource`, pngjs/buffer) + migrate tests
- [x] **8. Gate + review + decisions-log close**

## Contract

### Public seam (commonMain, `dev.staticsanches.kge.image`)

```kotlin
interface ImageService : KGEOverridable {
    fun interface Decoder<in T> {
        suspend fun decode(
            data: T,
            consume: (width: Int, height: Int, pixels: ResourceWrapper<ByteBuffer>) -> Unit,
        )
    }

    fun interface Encoder<out T> {
        suspend fun encode(sprite: Sprite): T
    }

    suspend fun <T> load(
        data: T,
        decoder: Decoder<T>,
        sampleMode: Pixmap.SampleMode,
        name: String?,
    ): Sprite

    suspend fun <T> save(sprite: Sprite, encoder: Encoder<T>): T

    companion object :
        KGEOverridable.Proxy<ImageService>(ImageService::class, imageServiceDefault),
        ImageService { /* forwards */ }
}
```

- **Generic-pure surface.** The call site always passes the codec; no shorthand
  overloads. This disambiguates same-typed payloads (`String` base64 vs `String`
  URL) and keeps the engine extensible (a new payload/format is a new
  `Decoder`/`Encoder`).
- **`load`/`save` default bodies are platform-independent**, so
  `imageServiceDefault` is a **common** object (supersedes the touch-point's
  "expect/actual" wording): platform choice lives in the codec extensions, not
  in the service. A service override (T2) still replaces `load`/`save` wholesale.
- **`load` orchestration (amended 2026-09-12 — see the decisions-log amendment).**
  `decoder.decode(data) { w, h, pixels -> ... }` — **adopt** `pixels` (a
  `ResourceWrapper<ByteBuffer>` of exactly `w*h*4` bytes) as the `Sprite`'s
  storage, constructing the `Sprite` directly over it (zero-copy; adoption
  cannot go through `SpriteService.create`, which would allocate and copy).
  `consume` is called exactly once; a second call closes the extra wrapper and
  throws. Construction is wrapped in `letClosingIfFailed`; if the decoder throws
  after `consume`, the adopted `Sprite` is closed before rethrowing; a decoder
  that never calls `consume` throws. `load` never closes `data`.
- **No default parameters.** `load`'s `sampleMode`/`name` are required
  (amended 2026-09-12), matching the explicit user-facing seam style
  (`SpriteService.create`, the raster sub-services).
- **`save`** returns `encoder.encode(sprite)` unchanged (ownership passes to the
  caller).
- **Ownership.** A `Decoder` owns every intermediate it allocates **and** the
  pixels buffer until it calls `consume`; the call transfers ownership to the
  consumer (`load` adopts or closes it). An `Encoder` owns the returned payload; a
  `ResourceWrapper<ByteBuffer>` payload is caller-owned/closable.
- **Dispatch.** CPU decode/encode on `Dispatchers.Default`; blocking read
  (`UrlDecoder`) on `Dispatchers.IO`; web fetch is already non-blocking.
- **No `ImageFormat`, no explicit sniff.** The backend auto-detects the input
  format; the output format is the chosen `Encoder`. `Sprite` stays RGBA-only.
- **Failure is by exception** (as S5).

### Defaults (`image/extension/`, platform backends behind `internal expect`)

- common: `object BytesDecoder : Decoder<ResourceWrapper<ByteBuffer>>`,
  `object Base64Decoder : Decoder<String>`, `object PngEncoder :
  Encoder<ResourceWrapper<ByteBuffer>>`, `object JpegEncoder :
  Encoder<ResourceWrapper<ByteBuffer>>`, `object Base64PngEncoder :
  Encoder<String>`.
- jvm: `object UrlDecoder : Decoder<java.net.URL>`.
- web: `object FetchDecoder : Decoder<String>` (platform `fetch`).
- Platform primitives behind `internal expect` in commonMain with a jvm actual
  and a web actual (the web actual delegates to `WebImageCodec` in `webMain`; no
  js/wasm leaf split — the spike proved shared `webMain` compiles for both):
  `decodeImageBytes` (STB / `createImageBitmap`), `encodePngBytes`,
  `encodeJpegBytes` (STB / canvas `toDataURL`).

### Platform backends

- **JVM:** STB (`stbi_load_from_memory` with `req_comp = 4`,
  `stbi_write_png_to_func`, `stbi_write_jpg_to_func`); decode reads a
  `duplicate().rewind()` view (position-independent).
- **Web (browser):** `createImageBitmap(Blob(bytes))` → draw to a `document`
  canvas → `getImageData(...).data` (RGBA) for decode; canvas `putImageData` +
  `toDataURL("image/png"|"image/jpeg")` → base64-decode → bytes for encode.
- **Dependencies:** remove `npm("pngjs")` and `npm("buffer")`; no new npm deps.
- **Documented divergence** (not a parity requirement): decode breadth —
  JVM STB (JPEG/PNG/TGA/BMP/PSD/GIF/HDR/PIC/PNM) vs browser
  (PNG/JPEG/GIF/BMP/WEBP/AVIF/ICO); encode — PNG+JPEG on both.

### Migration

`PngService`→`ImageService`, `PngSource`→`Decoder` (`PngSource.base64`→
`Base64Decoder`, `PngSource.url`→`UrlDecoder`, `PngSource.fetch`→`FetchDecoder`),
`encodeToBase64`→`save(sprite, Base64PngEncoder)`. The S5 codec files, the
pngjs/buffer web interop, and the S5 test suite are retired/reworked.

## Steps

### 0. Browser-only web targets + browser tests in CI

- **Change:** drop `nodejs()` from the `js(IR)` and `wasmJs` targets
  (`kge-core/build.gradle.kts`); configure `browser { testTask { useKarma {
  useChromeHeadless() } } }` for both (plus `--no-sandbox`/`--disable-dev-shm-usage`
  if the CI run needs it). Regenerate and commit both `kotlin-js-store` lock
  files.
- **CI:** remove `-x :kge-core:jsBrowserTest -x :kge-core:wasmJsBrowserTest` from
  `.github/workflows/build.yaml` (the "no Chrome" comment is wrong) and run the
  full gate. Chrome/Chromium is confirmed preinstalled on `ubuntu-latest` and
  `windows-latest`; verify `macos-latest` (install Chrome there, or run browser
  tests only where Chrome is present).
- **Verify:** `./gradlew build --rerun-tasks` green; `jsBrowserTest` /
  `wasmJsBrowserTest` execute the suite in Chrome; `jsNodeTest` /
  `wasmJsNodeTest` no longer exist. Record the toolchain facts in the decisions
  log.
- **No unit test** (infra step); the green gate and the test-count report are the
  evidence.
- **Decided:** done 2026-09-11 — `nodejs()` dropped from both web targets, Karma
  `useChromeHeadless()`, CI exclusion removed (and `--rerun-tasks` added),
  `kotlin-js-store/yarn.lock` regenerated. `./gradlew build --rerun-tasks` green
  (jvm 429, jsBrowser 450, wasmJsBrowser 450); no node test tasks remain.

### 1. `ImageService` seam

- **Tests (red first), commonTest** — driven by fake codecs, no platform I/O:
  `load` adopts the codec's `(w, h, pixels)` wrapper as the `Sprite`'s storage
  (amended 2026-09-12; the original "gives `(w, h)` to `SpriteService.create`
  and copies" was superseded), landing `sampleMode`/`name`. `consume` called
  twice closes the extra wrapper and throws. A decoder that never calls
  `consume` throws with no leaked `Sprite`. A decoder that throws **after**
  `consume` closes the adopted `Sprite` and propagates. A wrong-sized buffer
  leaves no leaked `Sprite`/buffer. `save` returns exactly the encoder's value.
  A decorator overriding `load` is observable; a decorator overriding only
  `save` composes with the default `load`.
- **Files:** new `image/ImageService.kt` (interface + nested fun interfaces +
  companion `Proxy` + common `imageServiceDefault`). No platform code.
- **Decided:** done 2026-09-11 (amended 2026-09-12) — `ImageService` with
  generic `Decoder<in T>`/`Encoder<out T>`, default-bodied `load`/`save` on the
  interface, companion `Proxy` forwarding to a bare common
  `object : ImageService {}`; `consume` hands over a `ResourceWrapper<ByteBuffer>`
  adopted zero-copy, and `load`'s `sampleMode`/`name` are required. Tests on all
  targets (dims/sampleMode/name/pixels, adoption identity + close, double/missing
  `consume`, post-`consume` throw, wrong-size buffer via a `BufferService` stub,
  `save` pass-through, decorator override + save-only composition); all green
  (jvm/browser).

### 2. Web codec spike

- **Goal:** prove, on js+wasmJs browser, the decode and encode mechanism before
  committing the layout: a `ByteBuffer` of encoded bytes →
  `createImageBitmap` → canvas → `getImageData` RGBA → `consume`; and a
  `Sprite` → canvas `putImageData` → `toDataURL` → bytes. Determine whether the
  code can live in `webMain` using kotlinx-browser types (needs `Blob` from a
  `Uint8Array`; `document.createElement("canvas").unsafeCast<HTMLCanvasElement>()`)
  or must be a `webMain` `expect` with js/wasm `actual`s (the S5 `WebPngJs`
  pattern) because of interop-annotation differences and Blob/JsArray construction.
- **Tests:** a throwaway js/wasm browser test round-trips the PNG fixture through
  the chosen mechanism (kept as the seed of step 3/4 tests). Native `Promise`
  bridging via `kotlinx.coroutines.await`.
- **Decided:** done 2026-09-11 — **no js/wasm split needed**: `internal object
  WebImageCodec` in `webMain` using kotlinx-browser (`window.createImageBitmap`,
  `document`/canvas/`getImageData`, `toDataURL`) + web stdlib (`toJsArray`,
  `unsafeCast`), with `org.khronos.webgl.DataView` for byte I/O (the TypedArray
  `get`/`set` operators do not resolve in shared `webMain`). Decode copies RGBA
  into a `BufferService`-allocated wrapper closed via `use`; the `ImageBitmap` is
  closed in `finally`. Encode goes `putImageData` → `toDataURL` → `atob`. Test
  duplicated in `jsTest`/`wasmJsTest` (no `webTest` source set). Green (jvm not
  involved). **Carry to steps 3/6:** the `Blob` is currently built with a
  hardcoded `"image/png"` type — make it format-agnostic for multi-format decode;
  and canvas `getImageData` un-premultiplies alpha, a rounding risk for
  translucent pixels (PNG fixture round-trips exactly; JPEG is lossy by nature).

### 3. `BytesDecoder` + platform decode primitive

- **Tests:** `load(tinyPngBytes.asEngineBuffer(), BytesDecoder)` returns the
  fixture surface (dims, pixels, `sampleMode`/`name`); garbage bytes throw; the
  transient buffer and any intermediate are freed on success and on failure (no
  leak report). JVM: decode ignores the `java.nio` position. Web: the same
  fixture decodes in the browser.
- **Files:** common `image/extension/BytesDecoder.kt` + `internal expect suspend
  fun decodeImageBytes(source: ByteBuffer, consume: (Int, Int, ByteBuffer) -> Unit)`;
  jvm actual (STB) and web actual (spike mechanism).
- **Decided:** done 2026-09-11 — `object BytesDecoder` on `Dispatchers.Default`
  (package `image.extension`); jvm actual adapts the STB decode (hands the native
  buffer to `consume`, `stbi_image_free` in `finally`, no `Sprite`) and web
  actual delegates to `WebImageCodec`. `WebImageCodec`'s Blob is now
  format-agnostic (empty type). 6 commonTest + 1 jvmTest (position
  independence). Green (jvm 445, jsBrowser/wasmJsBrowser 468 including the
  spike test notes).

### 4. `PngEncoder`/`JpegEncoder` + platform encode primitive

- **Tests:** `save(distinctSprite(), PngEncoder)` round-trips losslessly through
  `BytesDecoder` (pixels equal); the payload is a real PNG (signature).
  `save(sprite, JpegEncoder)` round-trips dimensions and is a real JPEG
  (signature); pixels compared approximately (lossy). The returned wrapper is
  caller-owned: close idempotent, use-after-close fails fast.
- **Files:** common `image/extension/{PngEncoder,JpegEncoder}.kt` + `internal
  expect fun encodePngBytes(...)` / `encodeJpegBytes(...)`; jvm actual (STB) and
  web actual (canvas).
- **Decided:** done 2026-09-11 — `PngEncoder`/`JpegEncoder` on
  `Dispatchers.Default`, wrapping bytes through a new `image.extension`
  `ByteArray.toEngineBuffer` (the S5 helper is retired in step 7); jvm actuals
  via STB (`stbi_write_png_to_func` → `check(written)`;
  `stbi_write_jpg_to_func` returns `int` → `check(written != 0)`, quality 90);
  web actuals delegate to `WebImageCodec`. 4 tests (PNG signature + lossless
  round-trip, JPEG signature + dims, wrapper ownership, leak canary). Green (jvm
  449, browser 472).

### 5. Base64 + URL/fetch decoders/encoders

- **Tests:** `save(sprite, Base64PngEncoder)` decodes back to the same pixels;
  `load(base64, Base64Decoder)` returns the fixture. JVM:
  `load(classpathUrl, UrlDecoder)`; web: `load(dataUrl, FetchDecoder)`. Each
  closes its intermediate read buffer on success and on failure (no leak).
- **Files:** common `image/extension/{Base64Decoder,Base64PngEncoder}.kt`; jvm
  `UrlDecoder`; web `FetchDecoder`.
- **Decided:** done 2026-09-11 — `Base64Decoder` (base64 → transient buffer,
  closed on every path → `BytesDecoder`), `Base64PngEncoder` (`PngEncoder` +
  `Base64.Default`, `ByteBuffer.asByteArray` helper in `image.extension`),
  `UrlDecoder` on `Dispatchers.IO`, `FetchDecoder` via a browser `fetch`
  external. 5 commonTest + 1 jvmTest + 1 jsTest + 1 wasmJsTest. Green (jvm 455,
  browser 478).

### 6. Multi-format decode

- **Tests:** add offline, byte-verified fixtures (a 2x2 JPEG and BMP; a GIF (and
  optionally WebP) for the browser) alongside `tinyPngBytes`. JVM: STB decodes
  the JPEG/BMP fixtures through `BytesDecoder`. Web browser: `createImageBitmap`
  decodes JPEG/GIF/BMP (and WebP) — the browser-only suite now runs in CI, so no
  capability guard is needed. Assert dimensions + (lossy-aware) pixels.
- **Files:** `commonTest` fixtures; jvm/web test cases.
- **Decided:** done 2026-09-11 — `ImageTestFixtures.kt` with `tinyJpegBytes`/
  `tinyBmpBytes`/`tinyGifBytes`/`tinyWebpBytes` generated offline (ImageMagick
  `magick`, `cwebp -lossless`) from the same 2x2 surface and byte-verified.
  commonTest decodes JPEG (dims), BMP (dims + exact pixels; Chrome honors the
  32-bit alpha mask), GIF (dims, palette-quantized); browser-only WEBP (STB has
  no WEBP decoder). Green (jvm 458, browser 482).

### 7. Retire S5

- **Change:** delete `PngService.kt`, `PngSource.kt`, `PngServiceJvm.kt`,
  `PngServiceWeb.kt`, `PngSourceJvm.kt`, `PngSourceWeb.kt`, `PngJsPng.kt`,
  `WebPngJs.kt`, `Buffer.kt` (js+wasm), and the S5 tests
  (`PngDecodeTest`, `PngEncodeTest`, `PngLoadTest`, `PngServiceSeamTest`,
  `PngEncodeToBase64Test`, `PngDecodeJvmTest`, `PngUrlSourceTest`,
  `PngFetchSourceTest`) — their coverage is carried by the new suites. Remove
  `npm("pngjs")`/`npm("buffer")` and regenerate the lock files. Update the
  `SpriteService` KDoc reference to `PngService`.
- **Verify:** no dangling reference; `./gradlew build --rerun-tasks` green.
- **Decided:** done 2026-09-11 — 12 S5 production files + 9 S5 test files deleted;
  `pngjs`/`buffer` removed (their js/wasm interop files gone); `SpriteService`
  KDoc → `ImageService`; `kotlin-js-store/yarn.lock` regenerated and
  `kotlin-js-store/wasm/yarn.lock` deleted (the wasm target now has no npm
  dependencies — the tool-canonical empty state). `grep` clean; gate green
  (clean count: jvm 377, jsBrowser 402, wasmJsBrowser 402).

### 8. Gate + review + close

- **Gate:** `./gradlew build --rerun-tasks` green (jvm + js browser + wasmJs
  browser + ktlint + assemble/metadata).
- **Audits:** resource discipline on every allocate/close path (the transient
  RGBA buffer, read wrappers, encoded payload wrappers, the `Sprite`) including
  construction/consume failure branches; no-parameter-without-observable-effect.
- **Two-axis review** (two fresh general sub-agents). Decisions-log close entry
  in `docs/decisions/phase-1/19-image-s6.md`; roadmap current-state update.
- **Decided:** done 2026-09-11 — gate green (jvm 377, jsBrowser 402,
  wasmJsBrowser 402; ktlint + assemble/metadata). Two-axis review (two fresh
  sub-agents) with one fix round: docs/CI Hard findings (stale `AGENTS.md`/
  roadmap node+PngService narrative; macOS browser-test scope) fixed; JPEG
  divergence recorded; JPEG test gained a luminance/not-blank invariant;
  `UrlDecoder`/`FetchDecoder` failure-path leak tests added; `Base64PngEncoder`
  now dispatches on `Default`. Verify passes clean (standards/spec reports in
  `.opencode/reviews/`).

## Files

New: `image/ImageService.kt`; `image/extension/{BytesDecoder,Base64Decoder,
PngEncoder,JpegEncoder,Base64PngEncoder}.kt`; jvm `image/extension/UrlDecoder.kt`;
web `image/extension/FetchDecoder.kt`; platform primitive actuals (jvm web);
commonTest (seam + migration suites, extra-format fixtures). Modified:
`kge-core/build.gradle.kts`, `.github/workflows/build.yaml`,
`kotlin-js-store/{,wasm/}yarn.lock`, `image/SpriteService.kt` (KDoc). Deleted: the
S5 service/source/web-interop files and S5 tests (step 7).

Out of scope: an `ImageFormat` enum or format sniffing; GIF/animated decode;
cropping/scaling at decode; texture upload (renderer); quality configuration
beyond the encoder defaults; WEBP/AVIF encode; a decode consumer beyond C8/C9.
