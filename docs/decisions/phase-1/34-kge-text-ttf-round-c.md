## 2026-09-20 — `kge-text-ttf` round C: native face, shaping, layout

The module's first production code: the platform face seam, the public `Font`
resource and the public shaped run. Design material:
`docs/plans/2026-09-17-kge-text-ttf-round-c-microplan.md` (revised twice on
2026-09-20) and `docs/plans/2026-09-16-r6-text-touchpoint.md` (Revision). Round
`C` of the text work: A bitmap (core) → B scaffold → **C** → D raster/atlas → E
blit. No rasterization, atlas, blit or addon ships here. The round was reopened
once: the owner rejected the first close on the resource model and the seam
width.

### Shipped

- Build: `api(project(":kge-core"))`; LWJGL HarfBuzz and its natives in
  `jvmMain`; the `harfbuzzjs` npm package and its externals in `webMain`
  (FreeType stays test-only on both platforms until round D consumes it);
  `kge-font-roboto` as the `commonTest` fixture; `-Xexpect-actual-classes`.
- Seam (`internal expect`): `NativeFace` with `shape`/`metrics` methods, its
  `createNativeFace` factory, and `closeNativeFace`.
- Public: `Font` (`load(bytes)`, `load(base64)`, `shape`), `ShapedGlyph`,
  `ShapedRun`, `TextMetrics`.
- The pinned contract was re-measured on the shipped `Roboto[wdth,wght].ttf`
  (3.015) at its default instance, 16 px, and is identical on jvm/js/wasmJs: ids
  `37,58,4,56,83,4,59,69,90,73,4,21,22,23`; advances 26.6 ending `…,576,576,576`;
  offsets all zero; clusters `0..13`; `"AéB"` ids `37,703,38` with clusters
  `0,1,2`; a non-BMP pair still clusters `0,1,2`; metrics 14.84375/−3.90625/0.0,
  exactly 2× at 32 px. Against the superseded 2.137 table: the same ids, advances
  11–13 are 576 (not 575) and `é` is glyph 703 (not 675). `Roboto.FAMILY`/
  `VERSION` are asserted beside the constants, so a font bump fails as a fixture
  change rather than silently re-pinning advances.

### Decision — the face is a handle; the wrapper is the resource

`NativeFace` owns the payload and the native handles but does **not** implement
`KGEResource`: it is what `ResourceWrapper<NativeFace>` owns, exactly as
`ByteBuffer` is what `ResourceWrapper<ByteBuffer>` owns in `kge-core`. The
wrapper is built in `Font.load` by a file-`private` helper
(`@OptIn(KGESensitiveAPI::class)`, the `kge-benchmark` precedent) with
`closeNativeFace` as its clean action, and `Font : KGEResource by` it. The leak
detector therefore reports a font **face**, never the byte buffer, and the
use-after-close guard lives in the object being guarded on both backends.

The first close shipped the opposite — the resource contract applied to the
payload, a hand-rolled `Boolean` for idempotency on JVM, a nullable handle for
the guard on web, and the handles' own leak undetected — which is why the owner
rejected it.

- **The payload's lifetime follows the platform.** JVM retains the wrapper (the
  blob references that memory with `HB_MEMORY_MODE_READONLY`) and releases it
  after `font → face → blob`; web copies into wasm memory and releases the
  staging buffer as soon as the face exists. Pinned by a `webTest` test asserting
  the allocation is already cleaned *before* any close, and by the shared test
  asserting it is cleaned after close.
- **The leak report's representation is pinned.** It was not: `kge-core`'s
  deterministic collection trigger was `internal` to that module, and `Font` is
  the first resource outside the core, so nothing could assert what a leak
  reports. The trigger is now `@KGESensitiveAPI public` on `ResourceWrapper`,
  and `Font` follows the core's own convention (`Sprite`, `Texture`) with an
  `internal fun onCollectionObserved()`. `FontLeakReportTest` fires it on an
  unclosed font and asserts the report names the face and not a byte buffer —
  verified by reverting the delegation and watching the test fail.
- **That widening of the core is deliberate and minimal**, justified by the
  roadmap's principle 2: detection "should be *testable* … so a leak fails a
  test rather than being observed in a log", which held only inside `kge-core`
  until a second module owned a resource. One function, marked sensitive; no
  other core surface moved.
- **No module-observable red existed for the ownership itself:** the previous
  close already released the payload, so the defect was the reporting identity.
  The red that does exist — the web early release — was demonstrated by
  reverting it.

### Decision — the seam is three names wide (roadmap principle 7)

The touch-point fixed the seam's *shape* and not its *width*: six `internal`
declarations plus three internal carriers put 31 top-level `internal`
declarations in the module. The seam now exposes only `NativeFace`,
`createNativeFace` and `closeNativeFace`: the operations became methods, the
carriers gave way to the public `ShapedGlyph`/`TextMetrics`, the JVM `actual
typealias` indirection went, `FIXED_POINT_SCALE` became a `private` constant per
actual, and the wrapper helper is file-`private`.

- **Recorded exception:** the nine harfbuzz externals stay `internal` in their own
  `@file:JsModule` file. Kotlin allows only `external` declarations in such a
  file, so the `actual class` cannot share it, and a `private` top-level is
  invisible across files; a declaration-level `@JsModule` compiles but breaks the
  module binding at runtime (`harfbuzzjs.default is not a constructor`). The
  exception is a language constraint, not a design choice.

### Supersessions and carry-forward

- **FreeType stays test-only, and `versionString()` is gone.** Round C has no
  production consumer for FreeType — rasterization is round D — so the npm
  package and its externals return to `webTest` rather than shipping as
  production surface whose only consumer is a test (`initFreeType`). That
  matches the JVM side, where `lwjgl-freetype` is already `jvmTest`-only, and it
  is what the roadmap's "nothing with no consumer yet" and principle 7 require.
  `versionString()` and the web HarfBuzz probe went for the same reason:
  `ShapingTest` exercises the whole HarfBuzz path on js and wasmJs, so the probe
  was redundant and its only consumer was a test. `WebNativeStackSmokeTest` keeps
  the FreeType half; the JVM smoke keeps its version probe, which costs no module
  surface.
- **Round D's seam width is decided at its touch-point.** This round's lesson is
  that a touch-point must fix a seam's width and not only its shape: FreeType
  enters in D, and its externals and entry points are exactly the vocabulary that
  would otherwise land module-visible by default.
- **The module has no `ProjectConfig`:** `kge-core`'s reset of service overrides
  is `internal` to it, so a test that overrides `BufferService` restores the
  default in a `finally`. A module-local config is the follow-up.
- **`faceIndex` removed** (review round 1): HarfBuzz honours no positive index on
  a single-face payload, so `load(bytes, 1)` opened face 0 while the getter
  reported 1 — an unpinnable parameter whose property could misreport the loaded
  face. Multi-face (TTC) support returns whole, with a fixture that pins real
  selection; re-adding the parameter is additive.
- **The validity predicate is asymmetric per backend:** JVM rejects
  `hb_face_get_glyph_count == 0`, web a missing `cmap` table. harfbuzzjs exposes
  no glyph-count accessor, so no cross-target predicate exists — an outline font
  with no character map loads on JVM and throws on web.
- **Public `Font` KDoc corrected:** it claimed close releases the native face,
  which is false on web — harfbuzzjs registers every handle with a
  `FinalizationRegistry` and exposes no `destroy`/`free`.
- **Steps 5 and 6 of the micro-plan had no separate red:** the metrics are part
  of the shaped run, so they arrived with step 4, and the close/use-after-close
  guards were already required in steps 3–4. A red would have meant deliberately
  shipping a double-free or a use-after-free.

### Verification

`tools/gradle build` green (every target, ktlint, assemble/metadata and
`buildSrcCheck`). Two-axis review of the first close: round 1 Spec FAIL on
`faceIndex`, round 2 PASS on both axes over that fix, round 3 PASS on both axes
over this entry. The owner then rejected the close on the resource model and the
seam width, so the round was reopened and the reopened tree carries its own
review. olc parity is **N/A** — olc v2.30 has no
HarfBuzz/FreeType/TTF API and its 8×8 bitmap text is the `C7` concept in
`kge-core`, closed in round A — and `main` carries no font code, so there is no
Kotlin-level baseline to regress.
