# Packaging resources across KGE targets — mechanism findings

**Date:** 2026-09-17. **Status:** research complete; **one decision open**
(delivery mechanism, below). Companion to
`docs/plans/2026-09-17-font-bundle-findings.md`, which holds the font choice and
the delivery-format spike numbers; this file generalizes the mechanism.

Question: the engine ships data its consumers must read at runtime (fonts now;
default atlases, shaders, codec data later) and a consumer must not copy files
into its own build. What mechanism delivers a resource to **every** target KGE
supports (jvm, js/browser, wasmJs/browser) from a published artifact?

## 1. Why the first attempt did not work

Kotlin Multiplatform defines **no cross-platform resource runtime**. A source set
has `resources`, and the base plugin only *processes* them
(`build/processedResources/<target>/<sourceSet>`): there is no accessor and no
per-target delivery. The Compose Multiplatform docs are explicit that a
"special library and Gradle plugin" is what provides access "in common code
across all supported platforms" — packaging is a **plugin's** job, not the
KGP's.

Measured in the spike (companion doc §2):

| Target | Mechanism | Result |
|---|---|---|
| JVM | `jvmMain/resources` | ✅ jar entry; compresses 57.5% (488,584 → 280,930 B) |
| JS / wasmJs | `webMain/resources` | ❌ emitted to `processedResources`, **never copied into the served webpack bundle**; `fetch` → 404 on five probed paths |

The 404 was **silent**: the browser `fetch` *resolves* with `ok = false` instead
of rejecting, so a test that only awaits resolution passes. It surfaced only when
the test asserted the real `byteLength`. Any future web-asset test must assert
content, not mere resolution.

## 2. Delivery families

| | A. Bytes in the artifact | B. Per-target native resources | C. External (URL/fetch) |
|---|---|---|---|
| Mechanism | generated Kotlin (chunked base64) compiled into klib/jar | `resources` + a Gradle plugin that copies per target | caller-supplied URL |
| jvm | ✅ class-file constant | ✅ jar entry (compressed) | ✅ `UrlDecoder` |
| js / wasmJs browser | ✅ inside the JS/wasm bundle | ❌ no copy step by default; needs plugin/webpack | ✅ server-hosted files only, not shipped assets |
| a future target (native, android, …) | ✅ no new code | ⚠️ new packaging rule + consumer phase | ✅ |
| Consumer wiring | none | plugin applied in the consumer app (Xcode phase, webpack config) | a server/URL |
| Cost | +33% source bytes, compile time, bundle size | smallest artifacts | hosting |
| Proven today | ✅ jvm + js + wasmJs (spike) | JVM only | existing API |

## 3. Recommendation

**A is the engine's default packaging mechanism.** It is the only family that is
target-agnostic *by construction*: the payload is ordinary Kotlin, so any Kotlin
target — including ones the engine adds later — compiles it with no new
packaging rule, no consumer-side plugin, and no server. It is also the only
family already proven on all three current targets, and it removes the
published-artifact risk entirely: there is no asset to serve, so the web path
cannot 404.

The size argument for a hybrid (B on JVM, A elsewhere) is weak. base64+gzip is
**−32% vs raw** on the wire (330,706 vs 488,584 B), and the JVM jar carries the
base64 constants compressed, so the delta against a binary jar entry is on the
order of **50 KB per font** (330,706 vs 280,930 B) — not the 3× that raw sizes
suggest.

Rules that keep A honest:

- **Chunk the base64** (32768-char chunks — the repo's existing precedent) so no
  backend hits a literal/constant limit. A single JVM Java literal over 65,535
  chars fails; the chunked form removes the question on every backend.
- **One Gradle task, one generated accessor per module**, with the package and
  entry names generated from the resource tree — the `GenerateGoldenImagesTask`
  pattern already in `kge-core/build.gradle.kts`.
- **Extract it to a convention (build-logic) at the second consumer module**, not
  before: one inline task now, a shared plugin when it is copied.

Escalation path if a resource is ever too large to embed: add a single
`expect/actual` to the *loader* (JVM classpath entry) **without changing the
generated accessor's public shape**, and keep C (`UrlDecoder`/`FetchDecoder`,
already shipped) for user assets and oversized files.

### 3.1 Module boundary: mechanism vs data

A needs **no runtime module**: the mechanism is a Gradle generator plus a
generated accessor, and the accessor can be dependency-free (`ByteArray` out,
standard library only). The artifact boundary is therefore a *content* decision,
not a mechanism one.

Bundled fonts stay in their own data-only module (`kge-font-*`), as already
decided in the companion doc §4 — and A makes that boundary nearly free: no
plugin, no `expect/actual`, no second pipeline, just a source-set directory and
one generated file per module. Folding them into `kge-text-ttf` instead would:

- force the font payload and its `OFL.txt` onto every consumer of the text
  mechanism, including those shipping their own brand font;
- couple the mechanism to one content choice, against round C decision 4
  (`Font` from `ByteArray`, indifferent to its provenance);
- mix the OFL obligations into the engine module's artifact;
- recompile and re-index ~900 KB of generated Kotlin alongside the codec.

Separation is also the reversible direction: an umbrella module can later depend
on both for a batteries-included default, while un-bundling would be a breaking
artifact change. Bundling wins only if the product decision is that TTF text
*always* ships a default font and alternate fonts are not expected.

### 3.2 Off-heap font data and the kernel question

`BufferService.allocate` already returns a `ResourceWrapper<ByteBuffer>`, and the
platform default is off-heap exactly "when possible": LWJGL `memAlloc` on JVM
(freed at close), a `TypedArray` on web (GC-released, close is a documented
no-op). A font accessor *could* hand out precisely that.

But then the data module depends on the buffer and resource contracts, whose only
home today is `kge-core` — which would put LWJGL GLFW/OpenGL/STB (and their
natives) plus the WebGL bindings on a data module's classpath. A thin kernel
module removes that. Its contents are `annotations`, `overridable`, `resource`,
`buffer` and `math.vector`; the import scan confirms these packages have **no
edge into** `image`/`rasterizer`/`renderer`/`engine`/`text`, and their only
external dependencies are kotlin-logging, kotlinx-collections-immutable (via
`KGEOverridable`) and lwjgl-core on JVM.

**However, the off-heap requirement does not by itself force that module, and it
does not remove the allocation either.** Whoever consumes base64 must decode it
into binary memory — base64 is text and HarfBuzz needs bytes. Zero-copy can only
remove the *duplicate*, not the allocation: the buffer is allocated once, by
`kge-text-ttf` through `BufferService` (so the engine's allocator and leak
detection apply), and HarfBuzz references it instead of copying it.

Whether HarfBuzz *can* reference it is platform-shaped (verified 2026-09-17):

- **JVM: yes.** LWJGL exposes
  `hb_blob_create(java.nio.ByteBuffer, int mode, long user_data, hb_destroy_func_tI)`;
  for a **direct** buffer LWJGL passes its address and copies nothing
  ("When a direct buffer is passed as an argument to an LWJGL method, no data is
  copied" — `BufferUtils`), and `HB_MEMORY_MODE_READONLY` (1) makes HarfBuzz
  neither modify nor free the data. The condition is lifetime: the buffer must
  outlive the blob (the face/font), so the native face retains the
  `ResourceWrapper` and closes it last. A non-direct buffer is unusable this way
  — LWJGL would stage a temporary that the retained blob outlives — so the seam
  must require a direct buffer.
- **Web: no.** `harfbuzzjs`'s `Blob` constructor always `malloc`s on the wasm
  heap and copies the `Uint8Array` in, creating the blob
  `HB_MEMORY_MODE_WRITABLE` with a free callback (`src/blob.ts`). The engine
  buffer is a staging buffer there and should be released right after the copy.

So the retained font bytes *can* live in an engine `ResourceWrapper<ByteBuffer>`
on JVM — which extends leak detection to the payload — while the web pays the
copy it always paid. The public surface can stay base64-only (stdlib), the
allocation happens in `kge-text-ttf`, and no kernel module is needed. If instead
the *data* module is to hand over an allocated buffer, it needs the
`resource`/`buffer` contracts and the kernel becomes necessary again — that is
the trade, and it cannot be avoided.

So the kernel is justified only by a *second*, non-font consumer that needs
`resource`/`buffer` without `kge-core`. Absent that consumer, the roadmap rule
("modules grow on demand — the skeleton contains nothing with no consumer yet")
says not to create it, and the `closeAll`/`CompositeResource` visibility problem
disappears with it.

### 3.3 Source vs materialization (streaming)

Two concepts are easy to conflate:

- **Transport** — how the bytes arrive (base64 chunks, file, URL). This is where
  chunking and streaming pay off.
- **Materialization** — the contiguous region the consumer requires, with a
  lifecycle.

HarfBuzz requires the whole font contiguous: a blob wraps a chunk of binary data
with random table access (`hb_face_create(blob, index)`, tables by offset), and
there is no incremental blob API — only `hb_blob_create_from_file` (file/mmap)
and `hb_blob_create_sub_blob` (slice). Zero-copy also *requires* contiguity, so
streaming and zero-copy pull in opposite directions. For an embedded font,
materialization is inherent, and slicing is served better by one buffer plus
views than by a stream.

The instinct is right for resources a consumer reads **sequentially** (row-based
image codecs, audio, network transfer): the engine's current model is
whole-payload (`ImageService.Decoder` consumes a complete payload), so a source
seam would be additive there. Its real cost is platform shape — JVM sources block
(dispatcher) while browser streams are asynchronous (`suspend`) — and a source is
itself a `KGEResource` under C2. `kotlinx-io` (`Source`/`RawSource`) ships a
`wasm-js` artifact and is the obvious vehicle.

No consumer exists today (fonts cannot stream), so the roadmap rule says not to
add it yet, and the base64-chunk design does not block it: adding a
`load(source)` entry point later is additive, and a chunk list is already a
stream-friendly transport.

### 3.4 The converter is shared build tooling

`buildSrc` already exists (currently only `lwjglNativesClassifier()` in
`LwjglNatives.kt`), so the converter belongs there rather than in a new
`build-logic` build: an abstract `EmbedResourcesTask` plus a small registration
helper, mirroring the existing plain-function style. It must stay **KGP-free**
(no `KotlinMultiplatformExtension` import): the root build script already
documents cross-plugin class-visibility problems around root-declared KGP, and
KGP resident in `buildSrc` is the same hazard. The module therefore wires
`kotlin.sourceSets[...].kotlin.srcDir(task.map { it.outputDir })` itself and
declares `kotlinx-collections-immutable` — which also keeps the root version
catalog out of `buildSrc`.

Task contract: recursive; deterministic (sorted walk, no absolute paths, no
timestamps); `@CacheableTask` with `@InputDirectory @PathSensitive(RELATIVE)`
and `@OutputDirectory`; configurable chunk size (default 32768); one
`List<String>` property per file, backed by `persistentListOf`; fail on
duplicate identifiers.

Two consumers exist at once — round C's test font and the fonts data module —
which justifies extracting it now instead of writing the inline task the round C
micro-plan currently specifies. Out of scope: image decoding (the golden-image
task keeps its own ImageIO concern) and moving files around the module tree.

### 3.5 The license travels with the data

The license is not optional metadata: OFL-1.1 requires the license text and the
copyright notice to accompany the Font Software, and it is **version-specific** —
Roboto 2.137 is Apache-2.0 while 3.016 is OFL-1.1 (companion doc §1), so the
committed license must match the exact build that is embedded, and the version
decision picks which one. An Apache-2.0 font additionally needs its `NOTICE`.

Placement has three layers, because "alongside the files" means different things
per target:

- **Repository** — the verbatim license file next to the font sources
  (`kge-font-<family>/OFL.txt`), as the upstream download ships it, plus a
  provenance note (family, version, source URL, files, license id): what a
  reviewer reads.
- **Artifact, every target** — the license text embedded by the same generator as
  a plain `String` entry. This is the only form guaranteed to reach the web
  artifacts, where resources are not packaged at all (§1), and it is what an app
  needs to render an open-source-licenses screen without shipping a second asset.
- **Artifact, JVM convention** — the file also at `jvmMain/resources/META-INF/…`
  so the jar carries a real file (the path the spike proved reaches the jar), and
  the POM `<licenses>` entry when publishing lands. Both asserted by a test, not
  assumed — the phantom-green discipline of §1.

The generator therefore takes an optional `licenseFile(…)` and emits a provenance
header (family, version, license id) into the generated source; it does not move
files around the module tree, which stays the module layout's job.

### 3.6 JVM resource compression

A jar DEFLATEs every entry, class-file constants included, so the base64 payload
is **already compressed** in a JVM artifact — the spike's 651,448 → 330,706
`base64+gzip` is the proxy for the Roboto constant. What A gives up against a
binary jar entry is base64's 33%: ~50 KB for Roboto, ~16 KB for Roboto Mono.

Three ways to use the compression better:

- **A′ — store `base64(gzip(raw))`.** The jar's DEFLATE brings the entry back to
  roughly the binary-entry size (~281 KB vs ~331 KB for Roboto), and the web
  bundle drops from 651 KB to ~374 KB per family (896 KB → 545 KB for the pair).
  Cost: an inflate step at load — JVM `java.util.zip.Inflater`, web/wasmJs
  `DecompressionStream('gzip')`, both dependency-free, but the browser API is
  asynchronous, so the web load path becomes `suspend`; a synchronous
  alternative needs a KMP compression library. The data module stays
  zero-dependency (the inflate lives with the loader) and the base64-only API is
  unchanged.
- **B (hybrid)** — a binary `jvmMain/resources` entry plus generated base64 for
  web. Smallest JVM artifact and the best JVM load path (read straight into the
  off-heap buffer, no base64 decode), but it contradicts the decided base64-only
  public surface: a uniform API would have to expose bytes or an engine buffer,
  or branch per target — the platform split this design removed.
- **Do nothing** — the jar already compresses the embedded base64; the remaining
  loss is ~66 KB for both families.

Recommendation: record A′ as the measured escalation path and do nothing now. The
saving is large where the bundle is the constraint (web, ~351 KB) and small on
the JVM (~66 KB), and bundle size is observable — the spike already measured the
JS test artifact at 915 KB. Adopt A′ when the web bundle, not the JVM jar, is the
binding constraint.

**Caveat:** jar compression of the *generated class file* is inferred from the
`base64+gzip` proxy, not measured on a real generated artifact; verify by
inspecting the built jar before relying on it.

## 4. Rejected for now

- **Compose Multiplatform resources** — solves exactly this problem (resources in
  publication artifacts since CMP 1.6.10; `composeResources/` copied into the web
  `dist`; async `fetch` on web; `Res.readBytes` is `suspend` and non-composable).
  But it requires the `org.jetbrains.compose` Gradle plugin plus
  `compose.components.resources` and its runtime/qualifier/localization model —
  it is a UI toolkit's resource system, and its web delivery still depends on the
  plugin being applied on the consumer side. Reconsider only if streaming or
  per-platform asset variants become a requirement.
- **moko-resources** — js/wasm badges and klib-packed resources with build-time
  extraction; JS/Browser emits JSON "included in webpack by default". But it is a
  localization/`MR`-accessor framework aimed at Android/iOS, with the same
  plugin-in-the-consumer constraint.
- **webpack copy (`copy-webpack-plugin` in `webpack.config.d`)** — fixes the
  engine's own test/dist, but a library cannot inject webpack config into a
  consumer's build, so it does not answer "published artifact".

## 5. Open verification (only if A is not chosen)

- Consumer-path spike (companion doc §3.3): a real consumer importing the
  published module, to see whether a web asset path works once published.
- If B is ever needed for web: which copy step survives publication (Gradle
  plugin vs documented consumer config) — untested.

## 6. Next step

**Decided (owner, 2026-09-17): option A.** The fonts data module and the
`buildSrc` converter land first, as their own round; round C then takes the data
module as a `commonTest` dependency instead of committing its own TTF. That
supersedes the companion doc's open item 4 (test and shipped font separate) —
recorded there — and means round C's shaping constants must be re-measured on the
font that ships.

**Fonts decided (owner, 2026-09-17): Roboto + Roboto Mono, variable 3.016,
OFL-1.1**, one file per family. The fonts module touch-point
(`docs/plans/2026-09-17-fonts-module-touchpoint.md`) records the module, its
base64-only surface, the `buildSrc` converter and the license layers.

**Next:** the micro-plan for that round — the converter in TDD, the
zero-dependency data module and its tests — then implementation. Round C's
micro-plan is revised on top of it (fixture from the module, `Font`'s base64
entry point and the `BufferService` path, contract re-measured).
