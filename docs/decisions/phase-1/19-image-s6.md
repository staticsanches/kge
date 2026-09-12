## 2026-09-11 — Image service (S6): touch-point decisions

`S5` (the PNG codec, log #34) is generalized. A new concept `S6` replaces the
PNG-only `PngService` with a platform-generic codec that decodes any format the
platform backend supports and encodes a uniform set. Driver (owner): user assets
loaded from file/URL in varied formats. Encode is in scope in the same concept.
Detail decided at this touch-point.

### Verified facts (research, 2026-09-11)

- **Current S5.** `PngService` (`KGEOverridable`, `expect`/`actual`) with
  `decode(ByteBuffer)`, `encode(Sprite)`, `encodeToBase64`, `load(PngSource)`;
  decode/encode are **synchronous**, only `load` is suspend (the read boundary).
  JVM default STB (`stbi_load_from_memory`/`stbi_write_png_to_func`); web default
  pngjs `.sync`.
- **JVM STB formats.** Decode JPEG/PNG/TGA/BMP/PSD/GIF/HDR/PIC/PNM; encode
  PNG/BMP/TGA/HDR/JPEG.
- **Web formats.** `createImageBitmap` (async, off-main-thread) decodes
  PNG/JPEG/GIF/BMP/WEBP/AVIF/ICO; `ImageDecoder`/`OffscreenCanvas` are not in
  kotlinx-browser 0.5.0 (would need `external`). Pure-JS **synchronous** decoders
  cover only a subset (jpeg-js/gifuct-js/bmp-js/utif) and **none** for
  WebP/AVIF.
- **Sync tension.** A uniform synchronous `decode` across JVM+web is not
  achievable for the full set (WebP/AVIF need the async browser path), so the
  synchronous contract is dropped.
- **`Sprite` is RGBA.** The decoded surface carries only pixels; the encoded
  format is a boundary concern (decode input / encode output), never a `Sprite`
  property.

### Decisions (owner)

- **New concept `S6`, superseding `S5`.** `PngService` → `ImageService`
  (`KGEOverridable`, platform default `expect`/`actual`). Scheduled after the
  line-pattern widening, before `C8`. The `S5` PNG-only contract is retired.
- **Suspend contract.** `load`/`save` and the codec `decode`/`encode` are
  suspend. Rationale: the web decode path is inherently async
  (`createImageBitmap`) and the JVM wants the CPU off the frame thread. Reading
  uses `Dispatchers.IO`; CPU decode/encode uses `Dispatchers.Default`; the
  `Sprite` may be born off the engine thread (the lifecycle/leak audit covers
  it). Failure is by **exception**, as in `S5`.
- **Generic codecs nested in the service.** `ImageService.Decoder<in T>` and
  `ImageService.Encoder<out T>`:
  - `Decoder<T>.decode(data: T, consume: (width, height, pixels: ByteBuffer) -> Unit)`
    — the codec owns the transient RGBA buffer; `ImageService.load(data,
    decoder, sampleMode, name)` allocates the `Sprite`, copies the RGBA and
    closes on failure.
  - `Encoder<T>.encode(sprite: Sprite): T` — binary encoders have
    `T = ResourceWrapper<ByteBuffer>` (caller-owned); a base64 encoder has
    `T = String`.
- **Generic-pure surface.** `load(data, decoder)` / `save(sprite, encoder)` —
  the call site always passes the codec; no shorthand overloads. This keeps the
  engine extensible (a developer implements `Decoder<T>`/`Encoder<T>` for a new
  payload/format) and disambiguates same-typed payloads (`String` base64 vs
  `String` URL) via the explicit decoder.
- **No `ImageFormat`, no explicit sniff.** The `Decoder` encapsulates the decode
  and the platform backend auto-detects the input format; the output format is
  the chosen `Encoder`. `Sprite` stays RGBA-only.
- **Defaults in separate extension files** (idiomatic extensions, like
  `image/extension/`): `BytesDecoder`, `Base64Decoder`, `UrlDecoder` (JVM) /
  `FetchDecoder` (web); `PngEncoder`, `JpegEncoder` (`ResourceWrapper<ByteBuffer>`)
  and `Base64PngEncoder` (`String`). `PNG`+`JPEG` encode is the **uniform** set;
  decode divergence (JVM STB × web `createImageBitmap`) is **documented**, not a
  parity requirement.
- **Migration.** `PngService`→`ImageService`, `PngSource`→`Decoder`
  (`PngSource.base64`→`Base64Decoder`), `encodeToBase64`→`save(sprite,
  Base64PngEncoder)`. Supersedes log #34; the `S5` tests and codecs are
  reworked.

### Open for the micro-plan

- The exact callback/`consume` signature and generics; whether `load`/`save` are
  type-parameterized methods (`<T> load(data: T, decoder: Decoder<T>)`).
- Web implementation: `createImageBitmap` + canvas `getImageData` (decode) and
  `toBlob`/canvas (encode); the `external` declarations needed (no
  `OffscreenCanvas` in kotlinx-browser); wasmJs specifics.
- Extension file names/layout and the default codec object names.
- Test inventory: suspend tests on all targets, per-format fixtures, the
  documented platform-divergence behavior, the resource/leak audit of the
  callback path.

Micro-plan next: `docs/plans/2026-09-11-image-s6-microplan.md`, TDD per step,
then the usual gate/review.

## 2026-09-11 — S6 web backend + test target resolved (owner)

The web-implementation item above was decided in the micro-plan session.

- **Node support is dropped; the web targets are browser-only (owner).** The
  engine targets the browser, so node was only a CI convenience. `nodejs()` is
  removed from `js` and `wasmJs`, and `jsBrowserTest`/`wasmJsBrowserTest` become
  the web suites. **Browser tests are enabled in CI**: the workflow's
  `-x :kge-core:jsBrowserTest -x :kge-core:wasmJsBrowserTest` and its "no Chrome
  on the runners" comment were wrong — verified `Google Chrome`/`Chromium` are
  preinstalled on `ubuntu-latest` and `windows-latest` (runner-images), and
  `jsBrowserTest` ran the full suite locally in Chrome. Enabling them also
  unblocks future browser-only capabilities (the WebGL renderer, R6 text,
  `createImageBitmap`). macOS runner Chrome is unverified — the plan runs browser
  tests where Chrome is confirmed.
- **Web backend is browser-native, not pngjs.** Decode via
  `createImageBitmap` → canvas → `getImageData` RGBA; encode via
  `putImageData`/`toDataURL` (`image/png`/`image/jpeg`) → base64 → bytes. This
  removes the `pngjs` and `buffer` npm dependencies and the js/wasm `Buffer`
  interop. Feasibility: kotlinx-browser 0.5.0 provides `window`,
  `createImageBitmap`, `ImageBitmap`, `ImageData`, `HTMLCanvasElement`,
  `CanvasRenderingContext2D` (incl. `getImageData`/`putImageData`/`drawImage`),
  `toDataURL`/`toBlob`, and `Blob`; `ImageDecoder`/`OffscreenCanvas` are absent
  (a `webMain` `expect` with js/wasm `actual`s may still be needed for Blob
  construction — resolved by the micro-plan's spike). No generic lightweight
  node image library exists: `jimp`/`image-js` are wrappers over
  `pngjs`+`jpeg-js`+`bmp-ts`+`utif2` with heavy cores (`file-type@21` ESM,
  `ml-*`) and browser-export gaps; `sharp`/`canvas` are native node-only.
- **The service default is common, not `expect`/`actual`** (supersedes the
  touch-point wording): `load`/`save` orchestration has no platform behavior —
  the platform choice lives entirely in the `Decoder`/`Encoder` extensions.
  `imageServiceDefault` is a common object; T2 override still replaces
  `load`/`save` wholesale.
- **The seam is generic-pure and the codec owns its buffers.** `Decoder<T>` owns
  the transient RGBA buffer handed to `consume`; `load` owns the `Sprite` it
  allocates and closes it on a post-allocation failure; `Encoder<T>` owns the
  returned payload. Both `load` and `save` remain suspend.
- **Encode set is PNG+JPEG on both platforms; decode breadth diverges and stays
  documented** (JVM STB × browser `createImageBitmap`), not a parity requirement.
  Micro-plan: `docs/plans/2026-09-11-image-s6-microplan.md`.

## 2026-09-11 — S6 (image service): closed (log #19)

Micro-plan delivered; `PngService`/`PngSource` and the pngjs/buffer web interop
are retired. Two-axis review passed after one fix round (reports in
`.opencode/reviews/s6-image-*.md`, machine-local). Decisions recorded at close:

- **Seam.** `ImageService : KGEOverridable` with nested `fun interface
  Decoder<in T>` (`suspend decode(data, consume: (w, h, ByteBuffer) -> Unit)`)
  and `fun interface Encoder<out T>` (`suspend encode(sprite): T`); generic
  `suspend fun <T> load(data, decoder, sampleMode, name): Sprite` and `suspend
  fun <T> save(sprite, encoder): T`. `load` owns the `Sprite` it allocates
  (closed on a post-allocation failure via `letClosingIfFailed` and on a
  decoder throw) and never closes `data`; the codec owns its transient RGBA
  buffer and every intermediate. `imageServiceDefault` is a plain common object
  using the interface defaults (no platform code in the service).
- **Extensions (`image/extension/`).** `BytesDecoder`, `Base64Decoder`,
  `PngEncoder`, `JpegEncoder`, `Base64PngEncoder` (common); `UrlDecoder` (jvm,
  `Dispatchers.IO`); `FetchDecoder` (web, browser `fetch`). CPU decode/encode on
  `Dispatchers.Default`.
- **Platform backends.** JVM STB (`stbi_load_from_memory` with `req_comp = 4`,
  `stbi_write_png_to_func`, `stbi_write_jpg_to_func`); web browser-native
  (`WebImageCodec`: `createImageBitmap` → canvas → `getImageData` RGBA;
  `putImageData` → `toDataURL` → base64). The spike proved the code lives in
  shared `webMain` (no js/wasm split needed).
- **Documented divergence (not parity).**
  - Decode breadth: JVM STB (JPEG/PNG/TGA/BMP/PSD/GIF/HDR/PIC/PNM) × browser
    (PNG/JPEG/GIF/BMP/WEBP/AVIF/ICO). WEBP is browser-only.
  - **JPEG encode quality**: JVM STB fixed quality 90; web `toDataURL`
    browser-default quality — the `Encoder` carries no quality parameter.
  - **Canvas alpha**: `getImageData`/`toDataURL` un-premultiply, a rounding risk
    for translucent pixels; binary-alpha/opaque surfaces round-trip (pinned by
    the PNG tests with alpha 128).
- **Test targets / CI.** The web targets are **browser-only** (`nodejs()`
  dropped); `jsBrowserTest`/`wasmJsBrowserTest` are the web suites. CI removes
  the browser-test exclusion and runs `build --rerun-tasks`; browser suites run
  where Chrome/Chromium is preinstalled (ubuntu/windows) and the macOS job runs
  the JVM suite only. Removing the npm deps left the wasm target with no npm
  dependencies, so `kotlin-js-store/wasm/yarn.lock` is deleted (tool-canonical
  empty state).
- **Leak-canary note.** On the JVM the decode transient is STB's native buffer
  (freed via `stbi_image_free`, not a `BufferService` wrapper), so the
  deterministic `cleaned` canary is meaningful on web/encode paths; the JVM
  STB free is asserted by behaviour, not the canary. On web the
  `FinalizationRegistry` can fire a previous test's intentional leak during a
  later test, so the suites use `ResourceWrapper.cleaned` rather than
  `LeakReporterService`.
- **Add-time verified:** kotlinx-browser 0.5.0 provides `window`/`document`,
  `createImageBitmap`/`ImageBitmap`/`ImageData`, `HTMLCanvasElement`/
  `CanvasRenderingContext2D` (`getImageData`/`putImageData`/`drawImage`),
  `toDataURL`/`toBlob`, `Blob` (`ImageDecoder`/`OffscreenCanvas` absent). The
  Blob built for decode carries no MIME type (the browser sniffs the format).
  No generic, lightweight pure-JS image library exists (jimp/image-js are heavy
  wrappers; sharp/canvas are native node-only).
- **Gate:** `./gradlew build --rerun-tasks` green (jvm 377, jsBrowser 402,
  wasmJsBrowser 402; ktlint + assemble/metadata).

## 2026-09-12 — S6 post-close amendment (owner review of the diff)

A Hunk review of the closed S6 diff raised two points; both applied. This
supersedes the touch-point's `load`-copies decision.

- **`load` adopts the decoder's buffer (zero-copy) — supersedes "load allocates
  the Sprite, copies the RGBA".** `Decoder.consume` now hands over a
  `ResourceWrapper<ByteBuffer>` (`consume(width, height, pixels)`): the decoder
  owns the buffer until the call, the call **transfers ownership** to the
  consumer, and the decoder must not close or use it afterwards (whatever
  happens). `load` builds the `Sprite` directly over that wrapper instead of
  allocating through `SpriteService.create`/copying. Effect: the JVM STB buffer
  becomes the sprite storage (`stbi_image_free` as the clean action — the S5
  zero-copy restored), and the web path drops one allocation and one copy
  (canvas → buffer is now the only copy). `load` closes the buffer on every
  failure path (`letClosingIfFailed` for construction; a second `consume` closes
  the extra wrapper; a post-`consume` decoder throw closes the adopted sprite).
- **No default parameters on `load`.** `sampleMode`/`name` are required,
  matching the explicit user-facing seam style (`SpriteService.create`, the
  raster sub-services); every call site passes them explicitly. (`load` bypasses
  `SpriteService.create` on purpose — adoption cannot go through the allocator;
  an overridden `SpriteService` therefore no longer sees `load`-created
  surfaces.)
- **Gate:** `./gradlew build --rerun-tasks` green (jvm 377, jsBrowser 402,
  wasmJsBrowser 402; ktlint + assemble/metadata).
