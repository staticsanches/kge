# `R6` round E3 — the GPU path: coverage texture + decal: touch-point

**Status: decided with the owner, 2026-10-01.** The round ships the GPU half of
the text draw: the one-channel coverage texture at the upload seam, the
per-glyph `DecalInstance`s and the addon's decal variants. The 2026-09-24
direction stands where the new facts allow it and is amended where they do not:
**WebGL2 has no texture swizzle**, so the swizzle trick is desktop-only and the
web backend keeps the white-alpha RGBA8 encoding behind the same region-update
contract.

**Date:** 2026-10-01. Touch-point for round **E3** of the text work
(`kge-text-ttf`), the module's seventh round and the last of the `E` split.
Rounds: A bitmap (core) → B scaffold → C face + shaping + layout → D raster +
atlas → E1 CPU blit + addons → E2 golden harness + text goldens → **E3 coverage
texture + decal**. The micro-plan is written just-in-time on top of this; it is
the test contract.

Material: the round E touch-point
(`docs/plans/2026-09-29-r6-round-e-touchpoint.md`, whose § "Round E3" is this
round's agenda), the glyph-atlas sizing research
(`docs/plans/2026-09-24-glyph-atlas-sizing-research.md`, §5 seam facts and §6
measurement agenda), decisions chunks `36` (round D), `38` (E1) and `39` (E2),
and the 2026-09-24 owner direction recorded in the roadmap.

## Verified facts (this touch-point)

1. **WebGL2 has no texture swizzle.** The `WebGL2RenderingContext` IDL
   (specification editor's draft, fetched 2026-10-01) declares no
   `TEXTURE_SWIZZLE_*` constants, and the specification's differences section
   (§5.18) states it: neither WebGL 1.0 nor WebGL 2.0 supports texture swizzle
   (cited verbatim in G-Truc's
   [texture-swizzling survey](https://www.g-truc.net/post-0734.html)). OpenGL
   3.3 core has both the per-component (`TEXTURE_SWIZZLE_R/G/B/A`) and the vec4
   (`TEXTURE_SWIZZLE_RGBA`) forms; the per-component form fits the existing
   `texParameteri` seam, so no `texParameteriv` is needed.
2. **`R8` samples as `(r, 0, 0, 1)` by default** (the internal-format tables of
   GL 3.3 and ES 3.0). The per-component swizzle `(ONE, ONE, ONE, RED)` makes it
   sample `(1, 1, 1, coverage)` — identical to round D's white-alpha RGBA8
   encoding — so the built-in quad's `texture * tint` fragment shader stays
   untouched on the JVM path.
3. **The GL seam is already complete for the carrier.** `GLService` declares
   `createTexture`/`deleteTexture`/`bindTexture`/`texParameteri`/`texImage2D`/
   `texSubImage2D`; `texSubImage2D` has no production caller today (research
   §5). The missing pieces are constants only: `R8` (0x8229), `RED` (0x1903 —
   confirmed in the fetched WebGL2 IDL) and `TEXTURE_SWIZZLE_R/G/B/A`
   (0x8E42–0x8E45).
4. **Two access walls shape the core-side seams.** `Layer.decalInstances` is
   `internal` to `kge-core`, so the module's addon cannot queue instances (every
   existing call site is core); and `Texture` is RGBA8-hardcoded
   (`create`/`update` upload `GL.RGBA`) while `Decal`'s sensitive constructor
   requires a core `Texture`, so the module's R8 texture must be produced
   through a core type.
5. **The upload payload exists ready-made.** `GlyphCoverage` is one byte per
   pixel, row-major, top-down — exactly what
   `texSubImage2D(RED, UNSIGNED_BYTE)` consumes — and the chart `Sprite`'s
   alpha channel holds the same bytes after placement.
6. **The module's decal test needs a headless engine.** `LayerStack`'s
   constructor is `internal` to `kge-core`, so only a started `Engine` builds
   one; `Driver` is a public interface and `Engine` a public abstract class
   with a public suspend `start()`, so the module's test can run the loop
   against a fake driver and the recording GL service.
7. **An adopted Font closes automatically at engine end.** `Engine.start()` runs
   its scope in a `use` block; `TtfTextService.createResources(engine.resourceScope, font)`
   adopts the Font into that scope, and the teardown closes it with the context
   current — the same guarantee `LayerStack`'s decal-texture close chain already
   relies on.

## Decisions

### Decision 1 — the web path: R8 on the JVM, RGBA8 on the web (owner)

The uniform contract is the **region-updated coverage upload**; the GPU format
is the platform's:

- **JVM:** the chart texture is `R8`, swizzled per component
  (`TEXTURE_SWIZZLE_R/G/B/A` ← `ONE/ONE/ONE/RED`) so it samples
  `(1, 1, 1, coverage)` and the built-in quad shader is untouched; region
  updates upload the coverage bytes with `RED`/`UNSIGNED_BYTE`.
- **Web:** the chart texture is RGBA8 and region updates upload the box's
  white-alpha pixels with `RGBA`/`UNSIGNED_BYTE` — the encoding round D already
  ships, now written per box instead of per chart.

The drawn pixels are identical on all three targets: the sampled texel is
`(1, 1, 1, coverage)` on both paths, the CPU charts are the same sprites, and
the goldens pin the CPU output while the smoke tests (decision 9) pin the GPU
output. The divergence is documented, the `S6` precedent (JVM STB × web
`createImageBitmap`). The 2026-09-24 direction's letter — "`GL_R8` plus
swizzle" — is amended by fact 1; its substance (the reduction at the upload
seam, region update, no `pixelStorei`, charts and encoding untouched) stands.

Rejected: `R8` on both platforms behind a shader variant (a second program or a
uniform switch plus a format notion on the decal path) — a core renderer change
for a format the web cannot express with the shared shader; and full-chart
uploads on web — the region update is the uniform part of the contract.

### Decision 2 — the coverage texture is a module-private carrier over the raw GL seam (owner)

The carrier (module-`internal`) creates the texture handle through the public
`GL` facade, sets the filter/wrap/swizzle parameters, and wraps it in the core
`Texture` through its `@KGESensitiveAPI` constructor — the constructor's
documented seam for "an external backend creating a wrapper around a handle it
owns". The module opts in at the carrier's declaration site, the same pattern
its service declarations already use.

**The core changes by six GL constants and nothing else** (`R8`, `RED`,
`TEXTURE_SWIZZLE_R/G/B/A` in the `GL` namespace — the home of the engine's raw
constants; module-local duplicates would fork the namespace). No `Texture` or
`Renderer` API change: a coverage factory or format parameters on `Texture`
would design a text-module need into the core, and a generic region-update API
would have this single consumer (YAGNI).

Rejected: a coverage capability on core `Texture`/`Renderer` — the core's
`Texture` is "the GPU storage of a `Sprite`" and an `R8` coverage texture is
not that; the invariant (white-alpha mirror, coverage extraction, swizzle) is
the atlas's business, not the renderer's.

### Decision 3 — the queue seam: the list opens under `@KGESensitiveAPI` (owner)

`Layer.decalInstances` becomes
`@KGESensitiveAPI val decalInstances: MutableList<DecalInstance>` — public,
gated by the opt-in, with the risk doc on the marker: the list is the render
step's working set, cleared by the flush each frame; append only. The module's
addon opts in **at its declaration site** (`@OptIn(KGESensitiveAPI::class)` on
`TtfDrawStringAddon`, the pattern the module already uses for the sensitive
resource constructors) and passes `layers.target.decalInstances::add` as the
service's collector — mirroring the core's `DrawStringAddon` line for line and
keeping the C7 service signature (`decalInstanceCollector`) intact. The core's
three queueing addons migrate nothing: they sit inside the project-wide
opt-in.

Rejected: a plain-public enqueue method (the same reach without the stop-and-think
callout) and exposing the list without the marker (a mutable engine working set
as unconstrained API).

### Decision 4 — the carrier is Font-owned, created lazily, closed by `Font.close()` (owner)

One carrier per `sizePx`, created at the **first decal draw** (the context is
current there), held by the `Font` beside its per-size atlases — the GPU shadow
of the CPU cache. `Font.close()` is the single release path, in the order
carrier → atlases → face; the atlas stays pure logic (its tests need no
natives), and the carrier never contaminates a Font used CPU-only — a Font that
never draws a decal allocates no GPU object.

Closing context-currency: an adopted Font is closed by the caller's
`ResourceScope` at engine teardown, where the context is current (fact 7, the
`LayerStack` precedent); a Font closed manually outside an engine is the
caller's T1 responsibility, as for any hand-closed `Texture`; a never-closed
Font reports the carrier's textures through the leak detector — each
`ResourceWrapper` reports itself on collection.

Rejected: scope-owned carrier resolved through a per-font key — it couples the
`Font` to its scope key and amends round D decision 6's spirit (draws take the
`Font` directly; the scope is ownership only) for no resource-safety gain.

### Decision 5 — the upload payload is extracted from the chart at upload time (owner)

The carrier packs each newly placed glyph's box by reading the chart `Sprite`'s
alpha through the public per-pixel surface — once per glyph, into a reusable
scratch buffer (allocated through `BufferService`, grown on demand, closed with
the carrier) — and hands the packed bytes to `texSubImage2D`. Nothing is
retained beyond what the charts already hold, and no sensitive surface is
touched.

Rejected: retaining the `GlyphCoverage` bytes per placed entry — a direct
upload, but a permanent `w×h`-byte duplicate of the alpha the chart already
stores.

### Decision 6 — the measurement: region vs. full at 512, both platforms (owner)

The round carries one benchmark cell with the `kge-benchmark` apparatus (the
chunk 32 pattern: a benchmark-only lever toggle selected by query string):
**region update vs. full-chart re-upload at 512** on JVM GL 3.3 core and
WebGL2, priced at the renderer-lever round's rigor (real GPU, uncapped, control
cell). `GL_MAX_TEXTURE_SIZE` is queried and recorded in the log while the
apparatus runs. The research §6 battery's remaining items — the real-set
occupancy for 256/512/1024 and the chart-count effect — are **deferred**: they
serve a `CHART_SIZE` decision the 2026-09-24 direction closed ("the `512`
constant is not what changes"), and no open decision consumes them.

### Decision 7 — sampling: NEAREST, no padding (owner)

The carrier's chart decals are created `Filter.NEAREST` +
`Wrap.CLAMP_TO_EDGE`, matching the bitmap-font sheet and the atlas's design;
the shelf packing stays untouched (the direction keeps `GlyphAtlas` as it is).
`DrawPartialDecalService`'s `0.0001` UV epsilon keeps the sampled texel inside
the glyph box, so no padding is needed at integer positions; **edge bleeding at
fractional scale is documented as accepted** — olc's own decal text has the
same property. Rejected: `LINEAR` + 1 px padding — a packing change with no
consumer today.

### Decision 8 — the fixture move: GL and driver fixtures to `kge-test-support` (owner)

`installGl`, `RecordingGLService`/`RecordedGLCall`, the handle factories
(expect/actual) **and** `installDriver`/`FakeDriverService`/`RecordingDriver`
move from `kge-core`'s test source sets into `kge-test-support`'s main source
sets (per-platform actuals included); `kge-core`'s tests import them from
there. The module's decal test then runs a **headless `Engine`**: its own
scripted `Engine` subclass and fake `Driver` (a public interface) against
`installDriver` + `installGl`, driving one scripted frame whose `onUpdate`
calls the addon's decal draw.

### Decision 9 — real-GL smoke on both platforms (owner)

The recorder pins sequences and geometry but verifies no pixels — a wrong
swizzle enum is a silent `INVALID_ENUM` and garbage text only on a real driver.
The round therefore carries smoke tests on both platforms:

- **JVM:** `GlfwTestDevice` moves to `kge-test-support`'s jvmMain (compile-only
  LWJGL; the consumer's test runtime provides the natives), and the module's
  jvmTest gains a coverage-texture smoke: create the carrier against the hidden
  context, upload a glyph box, draw through the decal path, read back, assert
  the pixels.
- **Web:** the module gains a `webTest` source set wired per the chunk 21
  recipe (kotlin-browser WebGL2 + the SwiftShader Karma launcher) with the same
  smoke against the RGBA8 region path.

### Established (not re-decided here)

- **Decal geometry:** one `DecalInstance` per ink glyph through
  `DrawPartialDecalService`; the source is the glyph's atlas box; the tint is
  the caller's color **plain** — the coverage lives in the texture, so the GPU
  path has no `CoverageTint` and no `Pixel.Mode` (the `Decal.Mode`/
  `Decal.Structure` come from `HasDrawModes`, as C7's decal path); `Blank`
  glyphs are skipped; positions stay float and unsnapped (the walk yields float
  pens; the service quantises); the scale is `Float2D` (olc's `vf2d` scale —
  the CPU path keeps `Int` scale).
- **The addon:** gains `HasWindow`/`HasLayers`;
  `drawStringDecal(font, position: Float2D, text, sizePx, color = WHITE,
  scale: Float2D = (1,1))` — the C7/olc shape; the viewport is
  `window.screenSize`; the collector is `layers.target.decalInstances::add`.
- **The `Decal.update()`/UV-mirror honesty:** the carrier is the single writer
  of the coverage texture; the `Decal` is buried in the carrier (private), so
  `update()`/`updateSprite()` are unreachable from outside it and never called
  by it; a recorder test pins the call sequence (no `texImage2D` after
  creation); the entry documents the coupling.
- **The carrier's chart decals:** created with `null` data (uninitialized
  storage; only placed boxes are uploaded and sampled — round D's surface
  contract), `Filter.NEAREST` + `Wrap.CLAMP_TO_EDGE` (decision 7).
- **Untouched by direction:** `GlyphAtlas`, its `Sprite` charts, the
  white-alpha encoding, `CHART_SIZE = 512`, the CPU path and every E1/E2
  expectation.

## Carried to the micro-plan

- The carrier's decomposition: the per-platform format piece (constants +
  packing walk) as `internal expect`/`actual`; the scratch buffer's growth
  policy; the uploaded-box bookkeeping (a per-chart set of placed entries).
- The recorder tests' platform-conditional assertions — the creation sequence
  differs per platform (the swizzle calls are JVM-only); the expect/actual
  format fact gates them.
- The smoke tests' exact assertions (JVM: the `R8` round-trip and the drawn
  pixels; web: the drawn pixels through the RGBA8 region path) and the
  `webTest` source set's build wiring.
- The benchmark cell's workload shape and the lever toggle's selection key.
- The `GL` constants' placement and KDoc (two lines, the contract only).

## Out of scope (E3)

Line breaking/word wrap, rotation, text entry and the console; SDF/MSDF;
chart sizing or a size cap; variable-font axes; the text API unification (its
touch-point follows E3, starting from option (a′) of
`docs/plans/2026-10-01-text-api-unification-findings.md`, with the TTF decal
contract this round fixes); a text workload beyond the upload-policy cell in
`kge-benchmark`; batching of per-glyph instances (the renderer draws one
instance per call — lever `#1`'s merge is the recorded deferred measurement).
