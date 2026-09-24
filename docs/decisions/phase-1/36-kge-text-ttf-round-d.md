## 2026-09-22 — `kge-text-ttf` round D: FreeType rasterization, glyph atlas, service

Round **D** of the text work (`R6`): FreeType enters production on both targets,
the seam gains rasterization, `Font` grows the per-size glyph atlas, and the
module declares its first service. Design material:
`docs/plans/2026-09-22-r6-round-d-touchpoint.md` (owner-confirmed decisions and
the corrected wrapper facts) and
`docs/plans/2026-09-22-kge-text-ttf-round-d-microplan.md` (the TDD contract).

### Seam — `rasterize` and the coverage carrier

- `NativeFace.rasterize(glyphId, sizePx): GlyphCoverage`, with `GlyphCoverage`
  normalized at the seam: row-major, top-down, one byte per pixel, `bearing`
  `Int2D(bitmap_left, -bitmap_top)` in whole pixels. `FT_Bitmap` layout, `pitch`,
  `num_grays` and the pixel mode stay inside the platform actual; only
  `FT_PIXEL_MODE_GRAY` is accepted, and a glyph with no ink returns an empty
  coverage before the pixel-mode guard (FreeType documents an empty bitmap whose
  mode need not be GRAY).
- **The seam carries no advance.** Advances and offsets come only from HarfBuzz;
  mixing FreeType's hinted slot advance into layout would disagree with the
  shaped run. The `fontDevelopment` branch took the slot advance — rejected.
- **Parity by construction:** both backends call
  `FT_Load_Glyph(FT_LOAD_DEFAULT)` then
  `FT_Render_Glyph(FT_RENDER_MODE_NORMAL)`, i.e. exactly the two calls the web
  wrapper makes internally; `FT_Set_Pixel_Sizes(face, 0, sizePx)` is re-issued
  only when the size changes. The pinned rasters are therefore a cross-build
  claim tied to FreeType 2.14.3 on both sides.
- **The library is process infrastructure; the face is the resource.** One
  file-private, single-flight library per process (JVM `FT_Library by lazy`, web
  module behind a `Mutex`), never torn down; the per-font `FT_Face` is released
  inside `closeNativeFace`, ordered with the HarfBuzz handles and before the
  payload. The web module is single-flight because `initFreeType()` is **not**
  idempotent: it caches only the dynamic import, and every call builds a new
  wasm instance, `FT_Library` and heap. `FreeType.destroy()` is never called —
  it destroys every face of the shared module; `Face.destroy()` per font is the
  release path.
- **The released 2.14.3 web wrapper does not normalize `pitch`.** It slices
  `abs(pitch) * rows` from the bitmap's buffer pointer; the `pitch < 0` row
  reordering exists only on the package's `main` branch. The web actual
  therefore reverses rows itself, exactly as the JVM actual does, and the two
  backends share one contract. (The touch-point's original claim — that the
  wrapper normalizes — was corrected against the installed package.)
- `createNativeFace` and `Font.load` became **`suspend`**: the web library is
  reachable only asynchronously, and principle 5 makes the common contract take
  the full form on both targets while the JVM actual never suspends. This
  supersedes the round C synchronous signature (same concept, no external
  consumer yet).
- **The resource guard moved into a file-private suspend helper.**
  `letClosingIfFailed` is `inline … crossinline` and cannot host a suspension
  point, so `openNativeFace(bytes)` owns allocate → copy → factory and closes
  the payload on any failure with the same semantics. The written discipline
  names `letClosingIfFailed`; a suspend variant in `kge-core` was not added for a
  single module-local need.

### The atlas

- `GlyphAtlas(sizePx, rasterize)` is pure logic — the rasterizer is a
  constructor parameter, so its tests need no natives — and packs glyphs into
  `512x512` `Sprite` charts by shelf rows: the first row that fits, else a new
  row at the glyph's height, else a new chart. Deterministic placement.
- Pixels are opaque white with the coverage byte as alpha (`0 ->
  Colors.TRANSPARENT`, `255 -> Colors.WHITE`), the `fontDevelopment` encoding:
  the atlas is tint- and mode-agnostic. A glyph with no ink is cached as
  `AtlasGlyph.Blank`, so a space never grows a chart; an oversized coverage
  fails fast. Untouched chart pixels stay unspecified (the surface contract);
  only placed boxes are read.
- The atlas is `internal` and `Font`-owned: `Font.glyph(sizePx, glyphId)`
  creates the per-size atlas lazily and `Font.close()` releases every atlas
  and then the face (`KGEResource` implemented explicitly instead of
  `by face`). No public raster or atlas API — E's blit is the consumer.
- `fontDevelopment`'s atlas knobs (a global mutable `Configuration` with
  `glyphChartWidthHeight`/`RowHeightThreshold`/`RowHeightIncrease` and system
  properties) are rejected: global mutable defaults are banned by the facade
  contract, and the heuristics are packing-density tuning with no consumer.

### The service, and a T2 rule revision

- `TtfTextService.createResources(scope, font)` adopts a loaded `Font` into the
  caller's scope under a **fresh key per adoption** (`ResourceScope` matches keys
  by identity), so one scope owns several fonts. The service stays stateless, E's
  draws take the `Font` directly, and neither an accessor nor a key is public.
  The first cut registered under one singleton key and the standards axis caught
  it: the second adoption threw `Resource key is already registered`, so the
  shape contradicted the very reason C7's was rejected. Rejected: C7's literal
  shape (draws resolving the font from the scope, one font per scope), a
  `FontResources` holder owning font + atlases, and a public text handle.
- **T2 is revised: the `Proxy` constructor is no longer `internal`.** The
  settle at chunk `06` read "engine-declared services only — the Proxy
  constructor is `internal`, consumers override, they do not declare new ones",
  which was written when `kge-core` was the only module and it forbids exactly
  what principle 1 requires of a satellite capability module. The constructor is
  now `protected` and carries `@KGESensitiveAPI`: the engine and its satellite
  modules declare services, applications override them, and a declaring module
  must opt in **at the declaration site** (`kge-core` opts in project-wide; the
  module does not). `resetAll()` stays `internal`; the module's restore pattern
  is `override(original)` in a `finally`. The extension-contract proof lives in
  `TtfTextServiceTest` and is observable through the scope's effect rather than
  through an accessor: the decorator adopts a different font via `original`, so
  after the scope closes the decorator's font is released while the one the
  caller passed still shapes.

### The `@JsPlainObject` question (measured — do not retry)

`@JsPlainObject` is a compiler plugin in Kotlin 2.4.10 and **does not serve
wasmJs**: its Gradle subplugin applies only to `KotlinPlatformType.js`, and
forcing it through the compiler-plugin classpath makes its FIR generator resolve
`kotlin.js` and call `single()` on an empty list — the wasm stdlib has no such
package, so the compiler fails internally. The options object is therefore an
`internal external interface LoadGlyphOptions` built by the wasm-native literal
`js("({ index: index })")`, which compiles and runs on both web targets; the
wrapper's `LOAD_DEFAULT` + render + `RENDER_MODE_NORMAL` defaults stay in force
and `renderMode` is not declared because it is never set. Also measured, against
the root comment's prior: the root `apply false` arrangement **configures
cleanly** in this build (kotest's and ktlint's failures do not reproduce for
this plugin) — the plugin's platform applicability, not the classloader, is
what rules it out. No build change was needed or kept.

### Deviations recorded

- **Module-local `closeAll`**: `kge-core`'s helper is `internal` and
  unreachable from a satellite module; widening a utility to `public` is not
  justified by the extension-contract rule, so the module keeps a ten-line copy
  whose KDoc states the semantics are the core helper's.
- `Font`'s atlas map uses `getOrPut`, so an oversized-glyph failure leaves an
  empty atlas cached; it is a valid resource closed by `Font.close()`, not a
  leak.
- Untested by any fixture, and recorded rather than pretended: the
  `abs(pitch) != width` padding path and the `num_grays != 256` scale path (the
  shipped font measures `pitch == width`, `num_grays == 256`, pixel mode GRAY at
  every glyph and size), and the non-GRAY guard (the module ships no bitmap-only
  font).
- `kge-core`'s `KGEOverridable` gained ktlint-mandated re-indentation around the
  annotated constructor; the module's service signatures are one parameter per
  line for the same reason. Formatting only.
- **The non-GRAY guard fails with the same type and message on both actuals**
  (`require` → `IllegalArgumentException`, `"the glyph bitmap is not grayscale:
  pixel mode <n>"`). It shipped as `check` on JVM and `require` on web; the axes
  flagged the split, which would have forced a per-target expectation the day a
  MONO/color fixture lands.
- **The web factory guards the window after `newFace(...)`**: the FreeType face's
  heap copy is freed only by `Face.destroy()` — the wrapper registers no
  finalizer — so a throw from the HarfBuzz font or the face constructor destroys
  it, and the staging payload is still released only once both faces exist.
- The new leak test's `shouldNotContain "glyph atlas"` half was dropped after
  review: the collection trigger fires one wrapper's state machine, so the
  assertion could never fail. The surviving half (rasterizing does not clean the
  face) is what the test pins.

### Pinned raster contract

Roboto 3.015 default instance (`wght 400`), measured on jvm, js and wasmJs and
**identical on all three**; box `width x rows`, bearing `(bitmap_left,
-bitmap_top)`, Σ = sum of unsigned coverage bytes. Space is `0x0` with Σ 0 at
both sizes.

| glyph | 16 px | Σ | 32 px | Σ |
|---|---|---|---|---|
| A | 11x12 (0,-12) | 9983 | 21x23 (0,-23) | 38047 |
| V | 10x12 (0,-12) | 8878 | 20x23 (0,-23) | 33689 |
| o | 9x9 (0,-9) | 7634 | 16x17 (1,-17) | 29348 |
| g | 8x12 (0,-9) | 11256 | 15x24 (1,-17) | 42512 |
| 1 | 5x12 (1,-12) | 5418 | 10x23 (2,-23) | 20645 |
| é | 8x12 (0,-12) | 8765 | 15x25 (1,-25) | 34659 |

The pin is the box, the bearing and Σ — not byte-for-byte bitmaps. A FreeType
version bump on either side must re-measure, as round C recorded for HarfBuzz.

### Review rounds

Round 1 over tree `c1495d60…`: **Spec PASS** (0 Critical, 0 Important, 2 Minor)
and **Standards FAIL** (0 Critical, 1 Important, 3 Minor). The Important finding
was real, and the code — not the record — was wrong: the service registered under
one singleton key, so a second adoption into the same scope threw, while
decision 6 and this entry both claim a scope can own several fonts. The delta
resolved it (fresh key per adoption, the test-only accessor deleted, the
extension proof reworked to observe which font the scope releases), aligned the
non-GRAY guard across the actuals (Standards Minor and Spec Minor, the same
defect seen twice), guarded the web factory window, and dropped the tautological
leak assertion; Spec's remaining Minor was a wording defect in this entry and is
corrected above. Round 2 reviews the tree that carries both this entry and the
delta.

### Verification

`tools/gradle build` green over that tree (every target, ktlint,
assemble/metadata, `buildSrcCheck`). Suites: `kge-core:jvmTest` 715,
`kge-text-ttf` jvm 44 / js 47 / wasmJs 47, all with zero failures;
`GlyphRasterTest` 5/5, `GlyphAtlasTest` 9/9 on all three, `TtfTextServiceTest`
3/3 on all three. No round C assertion changed; the two round C test files gained
the five tests micro-plan step 7 requires. Both axes' reports for round 2 carry
this tree's hash and are named by the marker.

### Carry-forward

- **E**: whether per-frame `drawString` shaping needs a cached shaped run — open
  in the roadmap's `R6` entry, with the `fontDevelopment` pooled-buffer evidence;
  measure before pooling.
- Multi-face (TTC) selection returns whole, with a fixture that pins real
  selection (round C removed the unpinnable `faceIndex`).
- Padding for scaled atlas sampling, a module-local golden harness, and the
  variable-font axes shape recorded at the 2026-09-19 revision.
