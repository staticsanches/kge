## 2026-09-07 — S5 (PNG codec): touch-point decisions

The open items of the roadmap S5 entry (PNG decode/encode of surfaces and
back; service seam per principle 1) were decided at the S5 touch-point (this
session). Micro-plan next (`docs/plans/2026-09-07-s5-png-microplan.md`).

- **Scope — codec + in-memory load now; I/O via `PngSource`; file/URL load
  included; `writePng` (file/stream) deferred to C9.** The codec itself (decode
  bytes → Sprite, encode Sprite → bytes) is synchronous on both platforms
  (JVM STB, web pngjs `.sync`). Reading is brought in now via a typed source
  abstraction; writing PNG to a file/stream has no consumer before the asset
  pipeline of C9 and is not introduced as a provisional API. base64 encoding is
  included (the web download path; JVM asset pipeline can use it too). Owner
  framing: the roadmap S5 "load from file/URL/bytes, write/encode" was refined
  through the touch-point to this boundary.
- **Seam — one `PngCodecService : KGEOverridable`** (package `image`, beside
  the surface). The PNG capability is one observable, user-replaceable
  behavior, so one seam (not separate decoder/encoder services — no evidence
  of independent override). Does NOT extend or modify `SpriteCreationService`
  (C5, log #33): that service's KDoc already states PNG is S5, and decode must
  not duplicate `create`.
- **Codec contract (common, synchronous, container = the C3 `ByteBuffer`):**
  - `decode(data: ByteBuffer, sampleMode = NORMAL, name = null): Sprite` —
    RGBA8 surface; `sampleMode` and `name` mirror the C5 parameters (observable:
    the `Sprite` is born with them; `name` is the immutable diagnostic label).
  - `encode(sprite: Sprite): ResourceWrapper<ByteBuffer>` — PNG bytes in native
    memory, caller owns and closes the wrapper (the engine buffer is the
    uniform container on both platforms; zero-copy into STB/pngjs).
  - `encodeToBase64(sprite: Sprite): String` — the portable PNG payload (web
    download; JVM convenience).
- **Typed source — `PngSource`, an OPEN interface (consumer-extensible), not a
  sealed ADT.** The owner wants extension on the TYPE: a new source format is a
  new `PngSource` implementation, without touching the service. Contract in the
  common module: `interface PngSource { suspend fun read(): ResourceWrapper<ByteBuffer> }`
  — `read()` returns a wrapper with an owner (resource discipline: the bytes
  from URL/fetch/stream are an allocation the caller must close; `read` without
  a close would leak, against C2/C5). Engine-provided implementations:
  - **base64 — in the core (common)**: a `PngSource` over a base64 payload.
  - **JVM — `java.net.URL`**: opens the stream (covers file/classpath/http
    URLs through the platform URL).
  - **web — fetch**: pulls the bytes from a URL.
  The load entry point composes source + codec (below). A future `PngSource`
  over a local file/stream is a new implementation, decided by C9's asset
  pipeline. `suspend read()` — source materialization is inherently async on
  web (fetch) and the engine converges on the unified suspend model (E1 intent).
- **Load in the service (one entry point):**
  `suspend fun load(source: PngSource, sampleMode = NORMAL, name = null): Sprite`
  — the default resolves `source.read()` then `decode`; the service override
  (T2) substitutes the whole load, and new `PngSource`s extend the loadable
  set without a service change. No separate `loadPng(url: String)` taking a raw
  string (the earlier shape): "url" as an untyped String was rejected — the
  source is typed by `PngSource`. No split "codec-in-service / I/O-as-solo
  extensions": I/O as non-overridable top-level functions was rejected
  (principle 1 — loading is an observable capability and must be a seam), and
  the extension point lives on the `PngSource` type.
- **Concurrency — coroutines `suspend`; add `kotlinx-coroutines-core` 1.11.0.**
  The branch has no coroutines today; `main` had 1.10.2 and the roadmap lists
  it in the verified stack. 1.11.0 is the current release (repo1 check,
  2026-05-08). This matches the approved E1 intent (unified suspend loop) and
  avoids the `main` blocking-JVM / `Promise`-web asymmetry (named pain) that a
  later concept would have to break. kotest runs suspend bodies on all three
  targets. JVM blocking I/O (URL stream) dispatches off the main dispatcher
  (Dispatchers.IO) inside the default.
- **base64 add-time question (open):** no base64 exists in the kernel today.
  Kotlin stdlib `kotlin.io.encoding.Base64` is common (JVM/JS/wasmJs); the
  alternative is platform `atob`/`btoa` (web) + `java.util.Base64` (JVM).
  Verify at add-time which is current/viable on all three targets (the encode
  path and the common `PngSource` base64 impl both need it).
- **Open at micro-plan:** exact `PngSource` impl names/package; whether the
  JVM `URL` source is the common interface's only JVM actual or a convenience
  next to it; PNG fixture strategy (a tiny known-good PNG, byte-verified) in
  commonTest and how jvm/js load it on the classpath (main used
  `xmas_5x5.png` resources on jvm/jsTest).
- **Review / gate as usual:** two-axis review (standards + spec) incl. the leak
  audit of every allocate/close path (decode from `read()`'s wrapper; encode
  wrapper ownership) and the no-parameter-without-observable-effect rule;
  `./gradlew build --rerun-tasks` at close.

## 2026-09-08 — S5 (PNG codec): closed (log #34)

Micro-plan delivered. Two-axis review passed after one fix round (report in
`.opencode/reviews/`, machine-local). Decisions recorded at close:

- **Seam name — `PngService` (review round).** The touch-point named
  `PngCodecService`; the owner's review round preferred the `*Service` suffix
  of its siblings (`SpriteCreationService`, `PixelFormatService`). Renamed at
  review; the micro-plan wording is superseded.
- **Sources are factories, not public classes (review round).** The engine
  sources are anonymous implementations behind companion factories:
  `PngSource.base64` (common), `PngSource.url(java.net.URL)` (jvmMain),
  `PngSource.fetch(url: String)` (webMain) — `PngSource` stays an open
  interface for consumer extensions.
- **Test teardown is global (review round).** A module-wide kotest
  `ProjectConfig` (commonTest) resets every `KGEOverridable` override after
  every test on every target, replacing the per-spec `afterTest { resetAll() }`
  boilerplate (removed repo-wide). Proven per target by a cross-test canary in
  `KGEOverridableExtensionTest`.
- **decode is position-independent (review round).** A `java.nio` position on
  the JVM typealias must not change decode's result — STB reads a
  `duplicate().rewind()` view; the contract KDocs and `PngDecodeJvmTest` pin
  it (parity with the position-less web buffer).
- **Add-time verified:** stdlib `kotlin.io.encoding.Base64` is stable (no
  opt-in) on jvm/js/wasmJs — used for `encodeToBase64` and `PngSource.base64`.
  LWJGL STB ships natives in its own artifact (`lwjgl-stb:natives-*`), added
  to the jvmTest runtime. pngjs normalizes any PNG color type to RGBA8 (like
  STB `req_comp = 4`), so both codecs emit the sprite byte layout with no
  conversion. `npm("buffer", "6.0.3")` was required for the browser test
  bundles to resolve the codec's `Buffer` import (node resolves its builtin).
  The wasm npm dependency added a second lock file,
  `kotlin-js-store/wasm/yarn.lock`, now tracked.
- **Gate:** `./gradlew build --rerun-tasks` green on all five suites (jvm, js
  node + browser, wasmJs node + browser) and ktlint.

