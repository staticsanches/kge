## 2026-10-03 — `kge-text-ttf` round E3: the coverage texture and the decal draw

The module's seventh round and the last of the `E` split: the one-channel
coverage texture at the upload seam, the per-glyph decal instances and the
addon's decal variants. Rounds: A bitmap (core) → B scaffold → C face + shaping +
layout → D raster + atlas → E1 CPU blit + addons → E2 golden harness + text
goldens → **E3 coverage texture + decal**. Touch-point and micro-plan:
`docs/plans/2026-10-01-r6-round-e3-touchpoint.md` and
`docs/plans/2026-10-01-kge-text-ttf-round-e3-microplan.md`.

### Shipped

- **Core, six constants and one seam**: `RED`, `R8`, `TEXTURE_SWIZZLE_R/G/B/A`
  in the `GL` namespace, and `GLService.getInteger(pname)` with the two backend
  implementations — the seventh constant, `MAX_TEXTURE_SIZE`, belongs to the
  measurement and was added by touch-point decision 6 over decision 2's
  "constants only".
- **`Layer.decalInstances`** reopened as a public `@KGESensitiveAPI` list
  (decision 3); the core's queueing addons and the render step are unchanged.
- **The carrier** (`GlyphAtlasGpu`, module-`internal`): one texture per atlas
  chart, created lazily at the first decal draw for a size, boxes uploaded once,
  `Font.gpuAtlas(sizePx)` beside the CPU atlas, `Font.close()` releasing
  carriers → atlases → face. The single platform fact is an
  `internal expect val coverageTextureIsSingleChannel`; everything else is
  file-private.
- **The decal draw**: `TtfTextService.drawStringDecal` (interface, companion
  forwarder, default body) over `drawStringDecalText` — the shared walk driven
  with `(0, 0)`, the destination applied outside with the pen and the bearing
  multiplied per axis, plain tint, `Blank` skipped, no scale guard.
- **The addon** (`TtfDrawStringAddon`): gains `HasWindow`/`HasLayers` and
  `drawStringDecal`, queuing through `layers.target.decalInstances::add`.
- **`kge-test-support`**: the GL/driver fixtures, the handle factories and
  `GlfwTestDevice` moved in from `kge-core`'s test source sets, `WebGlTestDevice`
  from its `webTest`, so the module can run both real-GL smokes.
- **`kge-benchmark`**: the upload-policy cell (region against whole-chart
  re-upload) and its premise test.

### The platform split is the recorded divergence (decision 1)

WebGL2 has no texture swizzle, so the coverage reduction is desktop-only: the
JVM chart is `R8` with `TEXTURE_SWIZZLE_R/G/B/A` = `ONE/ONE/ONE/RED` and uploads
`RED`/`UNSIGNED_BYTE`, while the web chart stays `RGBA8` white-alpha and uploads
per box. Both sample `(1, 1, 1, coverage)`, so the drawn pixels are identical and
the built-in quad shader is untouched on both. The `S6` precedent (JVM STB x web
`createImageBitmap`); no `pixelStorei` is used anywhere, which the fix below
preserves literally.

### The client row stride: a defect the recorder could not see

The JVM `R8` region upload was read by the driver at the default
`GL_UNPACK_ALIGNMENT` of 4: the carrier packed rows of exactly `width` bytes, but
`texSubImage2D(RED, UNSIGNED_BYTE, w)` starts each client row on a 4-byte
boundary, so an 11-byte row was read at a stride of 12 and every row sheared by
one more byte. Caught by the round's own real-GL smoke on the first run, proved
by the readback matching `data[12*row + col]` exactly and by a temporary
`glPixelStorei(GL_UNPACK_ALIGNMENT, 1)` that made the smoke green before any fix.

Fixed **in the client buffer**, not in the GL state: one uniform
`clientRowStride(width) = align4(width * bytesPerPixel)` — a no-op on the RGBA
path — the scratch sized `stride * height` and the pack writing at
`row * stride + column * bytesPerPixel`. Setting `UNPACK_ALIGNMENT` to 1 would
have mutated context-global state nothing restores, changing how every later
upload in that context is read, and widened the core surface this round fixed;
decision 1's "no `pixelStorei`" therefore stays literally true.

**The lesson is the test, not the bug.** The step-3 recorder test asserted the
uploaded bytes on the flat client buffer, i.e. it pinned a layout no driver reads
that way, so it stayed green on a sheared upload across all three targets. That
is exactly the gap touch-point decision 9's real-GL smoke exists to close, and
the smoke earned its cost on its first run. The recorder assertion was re-indexed
at the stride and now pins both a ragged-width box (the defect case) and an
aligned one.

### A driver-dependent assertion, corrected on both platforms

The web smoke first failed on a single pixel just outside the box: the service's
documented `ceil` puts the quad's right edge exactly through that column's pixel
centres, and whether a centre on the edge is covered is the rasterizer's
tie-break — SwiftShader inks it, the desktop driver does not. Both smokes now
assert one predicate: inside the box achromatic and coverage-matched within
tolerance; on the one-pixel right/bottom ring achromatic only; everywhere else
exactly the background. The JVM smoke was relaxed with the web one on purpose:
its strict form passed only because of this host's tie-break, which is a latent
flake on the Linux and Windows drivers the gate runs on. The relaxation was
proved not to cost power: reintroducing the stride defect and dropping the
swizzle calls each still fail **inside the box**.

### The measurement (decision 6)

The upload policy was priced on both platforms, real GPU, uncapped, with a
same-decorator control cell. Neither platform distinguishes region from
whole-chart re-upload under this load: the corrected mean delta is +2.2% on the
JVM with per-run signs inverting, and −3.8% on the web with the direction
repeating but inside the ±5% band. The apparatus, the numbers and the correction
that produced them are in
`docs/plans/2026-10-03-e3-upload-policy-measurements.md`.

**The first round of that measurement was wrong and is recorded as such.** The
region side paid a per-frame CPU extraction that belongs to the replay helper and
not to the policy, and it flattered the whole-chart policy by 11% on the web —
enough to have shipped the opposite verdict. Pre-packing both sides' payloads
outside the measured window moved the deltas to +2.2% / −3.8% and cut the web
spread from 8–13% to 2.7–3.6%.

**Finding**: the carrier uploads each placement exactly once, so the region
policy is a build-time cost, not a per-frame one; the cell replays the real
recorded boxes to have any steady-state traffic at all, and that replay premise
is now pinned by `TtfCarrierUploadTest` rather than left in a deleted scratch
test.

### The carrier's decal is reachable, and the plan said it was not

The touch-point's `Decal.update()`/UV-mirror bullet recorded that the carrier's
`Decal` is "buried in the carrier" and therefore unreachable, and that the entry
would document the coupling. **The first half is false and the second was never
done.** The object escapes: `GlyphAtlasGpu.decalFor` returns the `Decal` into
`DecalInstance.decal`, which is a public `val`, through the collector of the
public `TtfTextService.drawStringDecal` — no sensitive opt-in stands between a
caller and it.

The consequence is one-way on the JVM. `Decal.update()` re-specifies the chart
through `Texture.update`, which issues `GL.RGBA`, while the
`(ONE,ONE,ONE,RED)` swizzle stays armed: the sample becomes `(1, 1, 1, 1)`, so
every glyph box draws opaque, and `uploadedPlacements` never re-uploads the
region to repair it — permanent for that font's lifetime. `updateSprite()` would
overwrite the shared CPU chart, which the CPU path draws from.

**No guard was added, and none is cheap.** The `Decal` is a core type and the
carrier cannot intercept its methods; re-arming the swizzle does not undo a
format re-specification; and re-uploading every box on every frame to self-heal
is the full-chart cost the region policy exists to avoid. The disposition is
therefore a recorded contract plus an executable pin: the public member's KDoc
states that the instances' decal belongs to the font and must not be updated, and
a recorder test calls `update()` on a collected instance and asserts the
`RGBA` re-specification that follows — the residual is documented out loud and
fails if the path ever changes shape silently. In-tree code never calls it; the
hazard is reachable only by a caller who reaches past the contract, which is
exactly what the KDoc now forbids. The touch-point bullet is superseded by this
record.

### Deviations recorded

- **`kotlin.browser` in the module's `webMain`**: the carrier names `GLTexture`,
  whose web actual is a kotlin-wrappers DOM type; the same line `kge-core`,
  `kge-test-support` and `kge-benchmark` carry.
- **The `karma.config.d` launcher landed in step 3, not step 7**: the micro-plan
  sequenced the launcher wiring with the web smoke, but the step-3 common
  recorder suite already needed a real WebGL2 context (6 of 8 web tests failed
  without it). The micro-plan was amended rather than the plan being followed
  into a broken step.
- **The GL test devices duplicate the production devices' members**:
  `GlfwGpuDevice` and `WebGpuDevice` are `internal` to `kge-core`, so the moved
  `GlfwTestDevice` and `WebGlTestDevice` implement `GpuDevice` directly instead
  of delegating. Publicizing a concrete production class for a test fixture would
  have breached the scope discipline. Drift risk recorded: nothing keeps the two
  implementations in sync, and the module's smokes are what fail if the test
  device stops making the context current.
- **`kge-benchmark` gained `kge-text-ttf` and the font data module**, plus its own
  `karma.config.d/webgl.js` and `webpack.config.d/harfbuzz-freetype.js`: the
  module now draws TTF text, and the wasmJs distribution needs the FreeType
  webpack fallback the text module already carried.
- **The micro-plan lost the `tools/jev-ask` row and its "rides the commit"
  clause**: the file was removed eight minutes after the plan was written, when
  the Jev MCP was installed, and the wrapper it described is superseded. The
  plan's stride contract was added in its place.
- **The benchmark module reports its own test counts one too high** in the
  implementation report; the XMLs sum to 18 jvm / 19 wasmJs. Recorded here
  because it is the second reported count this round that did not match the
  measurement.

### Gate and review

`tools/gradle build` green twice: once over the round before the review, and
again over the review delta. Counts read from the JUnit XMLs with their freshness
checked against the last source edit, per the `#35` and `#38` rules.

| | core jvm/js/wasmJs | text-ttf | test-support | benchmark | font-roboto |
|---|---|---|---|---|---|
| pre-review | 707 (3 skipped) / 742 / 742 | 104 / 107 / 107 | 8 / 9 / 9 | 18 / 19 | 6 / 6 / 6 |
| post-delta | 708 (3 skipped) / 743 / 743 | 107 / 110 / 110 | 8 / 9 / 9 | 18 / 19 | 6 / 6 / 6 |

The three real-GL smokes — the JVM `R8` smoke and both web `RGBA8` smokes — are
reported as **executed**, not skipped, in every run.

**Review round 1.** Standards: PASS, 0 Critical / 0 Important / 6 Minor. Spec:
FAIL, 0 Critical / **1 Important** / 2 Minor. The Important is the reachable
carrier decal recorded above — a defect of the touch-point's own argument, not of
the code that followed it. The minors: `getInteger` untested and the seven new
constants absent from the core's constant table; the decal path's `sizePx` pinned
only at 16 and the addon's `color`/`scale` never exercised off their defaults; the
dead `BenchmarkWorkload.isTtfText`; `GL.RED` placed against the file's
ascending-value grouping; the decal test helpers duplicated across two files; and
the two real-GL smokes near-verbatim copies of each other.

**The delta closed all of them**, and the two whose subject matter is evidence
rather than code were closed by mutation: stripping the re-specification out of
`Texture.update` failed the new pin and nothing else; hardcoding the size failed
exactly the two new `sizePx` pins; ignoring the tint and ignoring the scale each
failed the new addon pin. The smoke deduplication moved the whole body into one
`commonTest` helper parameterized by `GpuDevice` — the platform files keep only
device acquisition and the skip policy — and its power was proved to survive by a
cross-platform mutation (`packBox` writing zeros) that turned all three smokes
red on their real drivers before being restored. Every mutated file was restored
byte-identically, checked by sha256.

One deviation recorded: the new `sizePx` pin uses **32**, not a smaller non-16
value, because the raster table pinned in chunk `36` carries 16 px and 32 px
only, and a 24 px expectation could not be hand-derived without inventing a new
measurement — which the micro-plan forbids and the implementer may not record.
32 is fully pinned and cross-checks against the CPU path's own measure.

### Carry-forward

- **Text API unification** (owner direction): the round that follows E3, starting
  from option (a′) of `docs/plans/2026-10-01-text-api-unification-findings.md`,
  with the TTF decal contract this round fixed.
- **The fixture duplication** above: a shared device seam inside
  `kge-test-support`, or a production `GpuDevice` factory, would remove the drift
  risk; neither is needed while two members are all that is duplicated.
- **Per-glyph batching** (`#1` of the renderer levers): still the measured
  deferred candidate, 17x / 2.4x.
- **The deferred occupancy battery** of the glyph-atlas sizing research.
- **The Jev payload budgets** measured on this deployment, recorded in
  `docs/plans/2026-10-02-jev-payload-limits-findings.md`.
