## 2026-10-06 — Text API unification, round U4: the legacy TTF surface retires

The round that deletes what U3 replaced. The round list named U4 (CPU draw) and
U5 (decal draw); U3 shipped both draws, so the two rounds had no work left but
the retirement, and `Font` anchors both legacy paths *and* the shaping, atlas
and resource specs, so it can only be deleted once — U4 absorbs U5 (owner,
2026-10-05). Touch-point and micro-plan:
`docs/plans/2026-10-05-text-api-unification-u4-touchpoint.md` and
`docs/plans/2026-10-05-text-api-unification-u4-microplan.md`.

### Shipped

- **The legacy public surface is gone.** `Font.kt`, `TtfTextService.kt` and
  `TtfDrawStringAddon.kt` are deleted, as are the four adapter overloads in
  `TextDraw.kt` (`Font.walkText`, `measureText(Font, …)`, `drawText(Font, …)`,
  `drawStringDecalText(Font, …)`). The `(NativeFace, atlasFor, gpuAtlasFor)`
  forms beside them were already the unified path and are untouched. The
  module now publishes `KGETtfFontService` and `TtfFontAddon` and nothing else.
- **What survived the deletion moved beside the concept it serves.**
  `wrapNativeFace` (its only non-`Font` consumer is `TtfFace.font`) and
  `String.toCodePoints` with its private offset moved verbatim into
  `NativeFace.kt`, still `internal`, `KGECleanAction` and the closing guard on a
  failed hand-off unchanged.
- **Visibility narrowed where the consumer disappeared.** `ShapedRun` (only
  `Font.shape` named it) is deleted with its `TextLayoutTest` case, and
  `ShapedGlyph`/`TextMetrics` are `internal` — the module's internals, the
  `NativeFace` seam and the test source sets are their only readers.
- **`kge-benchmark` runs the unified path.** `FpsBenchmarkEngine` implements
  `TtfFontAddon` (not `TtfTextSceneTarget`), the two TTF cells adopt a lease
  through `loadFontBase64` and assign it to `textFont`, `onUserUpdate` routes
  them through `renderTextScene`, and `Scene.kt` loses `renderTtfTextScene`,
  `TtfTextSceneTarget` and `SCENE_TTF_TEXT_SIZE`. The `tabSizeInSpaces` diamond
  override and the `Font?` field are gone; `onUserDestroy` no longer closes a
  font because the engine's scope owns the family.
- **The coverage moved with the deletion, not after it.** 18 pins were added
  before the surface went: five measurement pins, five CPU-draw pins, two decal
  pins, four addon pins, the loader's extension-contract proof and one shaping
  pin for the combining marks (the micro-plan's inventory, in
  `TtfFontMeasureTest`, `TtfFontDrawTest`, `TtfFontDecalTest`,
  `TtfFontAddonTest`, `ShapingTest` and a new `KGETtfFontServiceTest`). Eight
  legacy specs were deleted — `TextDrawTest`, `golden/TextGoldenTest`,
  `TtfDrawStringAddonTest`, `TtfTextServiceTest`, `TtfTextServiceDecalTest`,
  `FontResourceTest`, `FontLoadTest`, `FontLeakReportTest` — and the specs that
  keep their coverage moved onto the module's `internal` seam through the shared
  `RobotoFixture` anchor (`withRobotoFace`/`withRobotoAtlas`/`RobotoAtlas`).
- **`TextGoldenTest` was a full duplicate.** Its eleven scenes, canvases,
  arguments and `text/*.png` references are `TtfFontGoldenTest`'s, so no
  reference moved and a moved reference is a finding (touch-point Decision 7).

### Decisions

- **One retirement round absorbs U5** (owner, 2026-10-05): a CPU-only U4 could
  delete nothing structural while `Font` still anchored the decal path and the
  shaping/atlas/resource specs, and would re-anchor the same test surface twice.
- **The module's `internal` declarations are the test anchor; nothing was
  widened for tests.** Shaping, metrics, atlas, GPU-carrier, coverage-smoke and
  web-payload pins drive `TtfPayload`, `createNativeFace`, `NativeFace`,
  `GlyphAtlas` and `GlyphAtlasGpu` from the test source sets, which are already
  friends of the module. No production type or member was widened and no new
  seam was introduced.
- **The loader's extension-contract proof is added here** (roadmap principle 1).
  `KGETtfFontServiceTest` overrides the seam with a delegating decorator that
  loads Roboto Mono while the caller passes Roboto, and asserts the caller
  receives the decorator's family; the retirement deleted the only spec that
  exercised the seam's predecessor.
- **The family/lease leak contract stays U6** (owner, 2026-10-05). The lease
  cache, its single-flight, reference counting and the leak contract are U6's by
  chunk `41`; the round records the resulting one-round gap — no spec pins the
  family/lease leak identity between this close and U6's — instead of widening a
  private type to reproduce the legacy `Font` pin.
- **The benchmark's teardown order is the engine scope's, not a manual close.**
  `Engine.start` closes its `ResourceScope` inside `DriverService.create(...).use`
  and the scope closes in reverse registration order, so registering the
  `UploadPolicyGLCalls` decorator before adopting the family releases the
  family's textures first, with the context alive.
- **No new behavior-reference claim.** The round removes adapters whose behavior
  the unified path already reproduced on all three targets; no `olc` parity is
  argued here, so no header extraction was owed (touch-point fact 9).

### Divergences and retained differences

- **The shared test fixture is `internal`.** `AGENTS.md` asks for `private`
  first and then no modifier in test source sets; Kotlin's exposure check
  rejects an unmodified helper that names the module's `internal` seam types
  (`'public' function exposes its 'internal' parameter type argument`), and the
  alternatives were `@Suppress` (hiding a real diagnostic) or duplicating the
  fixture per spec. `internal` is a no-op across the module's test source sets
  and widens no production declaration.
- **`MetricsTest`'s non-positive-size pin moved to the surviving guard.** The
  deleted `Font.shape` carried `require(sizePx > 0)`; `NativeFace` has none, so
  the pin now drives `walkText`'s `require` through the internal
  `measureText(face, text, 0, tabSize)` route. The unified public path cannot
  express a non-positive size at all — `KGEFont.Size` rejects it at
  construction, pinned since U1.
- **Two `GlyphAtlasGpuTest` pins were adapted, not dropped.** The legacy
  `font.gpuAtlas(sizePx)` accessor has no internal analog (the anchor owns the
  carrier), so the after-close case asserts the atlas fails fast while the
  carrier assertion is unchanged; two case names follow the new subject.
- **The carrier's premise is preserved, not re-pinned.**
  `TtfCarrierUploadTest`'s assertion list is unchanged through the migration; the
  observed counts reproduce it exactly (`counts=[0, 21, 21, 21, 21]`,
  `perFrame=[21, 0, 0, 0]` — the build frame uploads 21 placements, every
  steady-state frame uploads none).
- **Non-regression against `main` is vacuous.** `git ls-tree -r main` has no
  `kge-text-ttf`: the round deletes code `main` never had, and the module's
  baseline is the base commit.

### Not in this round

The per-family configuration cache, its `Mutex` single-flight, reference
counting and the family/lease leak contract (U6); any new drawing behavior; any
seam change; any golden regeneration. `@JsName("Font")` in
`HarfBuzzWebExternals.kt` is a JS external's name, not the legacy type, and is
untouched.

### Verification

`tools/gradle build` green on the staged tree (301 actionable tasks, exit 0):
every target's tests, ktlint, the metadata/kLIB compilation and `buildSrcCheck`.
Counts per module and target, from the gate's own result XMLs:

| module | jvm | js | wasmJs |
|---|---|---|---|
| `kge-text-ttf` | 148 | 150 | 150 |
| `kge-core` | 746 (3 skips) | 781 | 781 |
| `kge-font-roboto` | 10 | 10 | 10 |
| `kge-test-support` | 8 | 9 | 9 |
| `kge-benchmark` | 21 | — | 22 |
| `buildSrc` | 27 | — | — |

`kge-text-ttf` moved from 201/203/203 to 148/150/150: 70 legacy `commonTest`
cases and one `TextLayoutTest` case deleted, 18 pins added, and the residue map
is the touch-point's. Every other module is unchanged, and no target reports
zero tests (chunk `35`).

The deletion's red was the compiler: after the production removal,
`compileTestKotlinJvm` named exactly the inventoried files —
`TextDrawTest` 191, `golden/TextGoldenTest` 57, `TtfDrawStringAddonTest` 41,
`FontResourceTest` 40, `TtfTextServiceDecalTest` 39, `TtfFontDecalTest` 28,
`TtfTextServiceTest` 16, `FontLoadTest` 10, `DecalDrawTestFixtures` 9,
`FontLeakReportTest` 5, `TextLayoutTest` 3 — with `commonMain` compiling clean,
so no reference fell outside the plan. Two accuracy defects of the plan were
corrected in the tree rather than in the plan: `TtfFontDecalTest` had seven
legacy-oracle cases, not six, and two class KDocs that named the deleted surface
were re-worded.
