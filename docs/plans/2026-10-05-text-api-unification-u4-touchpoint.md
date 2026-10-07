# Text API unification — round U4 (legacy TTF surface retirement): touch-point

**Status: decided with the owner, 2026-10-05.** U3 shipped the unified
`KGEFont` path for the TTF module; this round deletes the legacy public surface
that path replaces. It **absorbs U5**: the round list named U4 (CPU draw) and U5
(decal draw), but U3 landed both draws, so U4/U5 are retirements and the shared
anchor (`Font`) can only be deleted once.

Rounds: U1 core vocabulary + bitmap backend → U2 bundled italic fixtures → U3
TTF family/loading/naming/discovery/axes + measure + draws → **U4 legacy surface
retirement (absorbs U5)** → U6 caching/ownership hardening.

Material: the unification touch-point
(`2026-10-04-text-api-unification-touchpoint.md`, §"Replacement and compatibility
policy", §"Micro-plan test contract" 36); the U3 touch-point and entry
(`2026-10-04-text-api-unification-u3-touchpoint.md`, chunk `44`, Decision 11);
the current module, benchmark and test sources; the gate's `kge-text-ttf` result
XMLs.

## Verified facts (this touch-point)

1. **The legacy public surface is three names plus four adapter overloads.**
   `Font` (`Font.kt`), `TtfTextService` (`KGEOverridable`), `TtfDrawStringAddon`
   (the four engine roles), and `TextDraw.kt`'s `Font.walkText`,
   `measureText(Font, …)`, `drawText(Font, …)` and
   `drawStringDecalText(Font, …)`. The re-anchored
   `(NativeFace, atlasFor, gpuAtlasFor)` forms beside them are what the unified
   `TtfFont`/`TtfFontFamily` call.
2. **No production consumer lives inside `kge-text-ttf`.** `TtfTextService` and
   `TtfDrawStringAddon` call each other and nothing else; no unified source
   names either. The only production consumer outside is `kge-benchmark`:
   `FpsBenchmarkEngine.kt` (`Font` field, `Font.load`, `TtfTextSceneTarget`) and
   `Scene.kt` (`TtfTextSceneTarget`, `renderTtfTextScene`, `SCENE_TTF_TEXT_SIZE`).
3. **The module's public surface after the round is `KGETtfFontService` +
   `TtfFontAddon`.** `ShapedGlyph`/`ShapedRun`/`TextMetrics` are public only
   because `Font.shape` is; a repository-wide search finds no name outside
   `kge-text-ttf`, and inside it only the module's internals and
   `TextLayoutTest`.
4. **The legacy-anchored test surface is 87 `commonTest` cases across eleven
   specs**, plus one `webTest` case and one benchmark case. Counts are the
   gate's own result XMLs on the current tree:

   | spec | cases | spec | cases |
   |---|---|---|---|
   | `TextDrawTest` | 24 | `GlyphAtlasGpuTest` | 9 |
   | `TextGoldenTest` | 11 | `FontResourceTest` | 8 |
   | `TtfTextServiceDecalTest` | 9 | `ShapingTest` | 6 |
   | `TtfDrawStringAddonTest` | 9 | `FontLeakReportTest` | 3 |
   | `FontLoadTest` | 3 | `TtfTextServiceTest` | 3 |
   | `MetricsTest` | 2 | plus `WebNativeFaceTest` 1, `TtfCarrierUploadTest` 1 | |

   `GlyphAtlasGpuTest` is mixed: the stride, later-chart and
   scratch-reallocation cases already drive `GlyphAtlas`/`GlyphAtlasGpu`
   directly; the other six only use `Font` to obtain a real face and its atlas.
5. **`TextGoldenTest` is a full duplicate of `TtfFontGoldenTest`.** The two
   suites carry the same eleven test names, the same canvas sizes, the same
   text/color/scale/tab/mode arguments and the same `text/*.png` references;
   they differ only in the call surface (`TtfTextService.drawString(font, …)`
   against `configuredFont.drawText(…)`). Deleting it moves no reference.
6. **Residue the unified specs do not carry.** Legitimate pins with no current
   unified counterpart: the tab grid measured from the line origin rather than
   the draw origin; measuring without rasterizing; the scale anchor on the line
   box; the drawn newline advance; drawing the same text twice adding no chart;
   the non-positive tab-size rejection through the unified route; the decal
   geometry of two leases at different sizes over one face; and the
   `Decal.update()` hazard — it re-specifies the chart as `RGBA` while the
   coverage swizzle stays armed. `TtfFontMeasureTest`'s ink pin (`9983`) shows
   U3 already moved some legacy pins; this is the remainder.
7. **`TtfTextServiceTest`'s overriding-decorator case has no unified
   counterpart.** `KGETtfFontService` is a `KGEOverridable` seam, yet no spec
   overrides it; the gap appears exactly when the legacy spec dies. The core
   service has the analog (`CoreFontFamilyTest`, `KGECoreFontService.override`).
8. **The legacy per-size atlas map is structurally obsolete.** A unified lease
   carries one `Size` (one atlas and one GPU carrier); two sizes are two leases.
   `TtfFontFamilyTest`, `TtfFontMeasureTest` and `PayloadAllocationTest` already
   pin the lease/family close, inert-value, sibling-lease and payload-buffer
   rules the legacy `FontResourceTest` pinned on one object.
9. **No new behavior-reference claim.** The round removes adapters; the CPUs
   and decals they served are already pinned on the unified path on all three
   targets against the same committed references (fact 5). No `olc` parity claim
   is argued here, so no header extraction is owed; the R6 parity oracle stays
   rounds C–E3's.
10. **The seam width does not change.** `NativeFace`'s `expect`/`actual` is
    untouched; this round only lets the module's `internal` declarations act as
    the test anchor, which is the recorded trade of roadmap principle 7.

## Decisions

### Decision 1 — one retirement round, absorbing U5 (owner, 2026-10-05)

U3 made the unified draws the working path, so the round list's "U4 CPU draw"
and "U5 decal draw" name rounds whose work no longer exists. `Font` is the
anchor of both legacy paths *and* of the shaping, atlas and resource specs, so a
CPU-only round could delete nothing structural and would re-anchor the same test
surface twice. One round deletes the surface and re-anchors its pins.

Rejected: the literal U4/U5 split (two gates, two review pairs and two entries
for one deletion); keeping `Font` as a test-only façade (a public type with no
production consumer contradicts the removal the unification touch-point
promises).

### Decision 2 — the module's `internal` declarations are the test anchor; nothing is widened for tests (orchestrator)

After the public surface goes, `ShapingTest`, `MetricsTest`, `GlyphAtlasGpuTest`,
`CoverageTextureSmoke` and `WebNativeFaceTest` drive `TtfPayload`,
`createNativeFace`, `NativeFace.shape`/`metrics`/`rasterize`, `GlyphAtlas` and
`GlyphAtlasGpu` from `commonTest`, which is already a friend of the module. No
production type or member is widened, and no *new* seam is introduced: the pins
move inward, they do not disappear.

Rejected: an internal test-only factory in `commonMain` (production surface for
a test); keeping a minimal `Font` subclassed or decorated for tests (the
throwaway-API the roadmap forbids); pushing the shaping and atlas pins through
`KGEFont` (the public route cannot observe glyph ids, clusters, advances or an
upload box, so the pins would be weakened, not moved).

### Decision 3 — the loader's extension-contract proof is added here (orchestrator)

`KGETtfFontService` is a `KGEOverridable` seam (roadmap principle 1), and the
retirement deletes the only spec that exercised its predecessor. The round adds
a delegating decorator override whose construction is observable in the
`KGEFont.Family` the caller receives, matching `CoreFontFamilyTest`'s proof for
the core service.

Rejected: leaving the seam unexercised until a later concept (principle 1 asks
for the proof, not for a consumer); an override that decorates the returned
family instead of the construction (that tests the family, not the seam).

### Decision 4 — the family/lease leak contract stays U6; the legacy leak spec dies with its subject (owner, 2026-10-05)

`FontLeakReportTest` pins the leak identity of an object this round deletes, and
chunk `41` already assigns the per-family cache, its single-flight and the lease
leak contract to U6. The round records the resulting one-round gap explicitly:
no spec pins the family/lease leak identity between this close and U6's, and U6
owns it.

Rejected: adding an internal seam on the private `TtfFont` to reproduce the pin
now (widening a type whose privacy is the round's point, for a contract U6 is
about to restructure); a mechanism-only pin that rebuilds the wrapper in the
test (it would assert the string, not that the family uses it).

### Decision 5 — the benchmark's TTF cells move to the unified path (orchestrator)

`FpsBenchmarkEngine`'s `text-ttf-region`/`text-ttf-full` cells and
`TtfCarrierUploadTest` reach the legacy `Font` and `TtfDrawStringAddon`. The
configured font carries its own size, so the cells load through `TtfFontAddon`,
assign the lease to `textFont` and draw with `TextAddon`; `TtfTextSceneTarget`,
`renderTtfTextScene` and `SCENE_TTF_TEXT_SIZE` collapse into `renderTextScene`
and the lease's `Size`. The `UploadPolicy` decorator and the E3 upload-policy
premise keep working unchanged.

Rejected: deleting the two TTF cells (they are the pricing apparatus behind the
E3 upload-policy measurement); keeping a local TTF scene function that
duplicates `renderTextScene`.

### Decision 6 — the round narrows what loses its consumer (orchestrator)

`ShapedGlyph`/`ShapedRun`/`TextMetrics` become `internal`; `ShapedRun` is
deleted if the internals use only the `List<ShapedGlyph>` + `TextMetrics` pair
that remains (principle 7: widening is what needs justifying). `wrapNativeFace`
and `toCodePoints` move out of `Font.kt` beside the concept they serve
(`NativeFace.kt`), as U3's review recorded for this round.

Rejected: leaving them public because tests name them (tests can see
`internal`); a new `Shaping.kt` file for two helpers with one consumer file.

### Decision 7 — no golden reference moves (orchestrator)

`TtfFontGoldenTest` already reproduces all eleven `text/*.png` references on all
three targets, so a changed reference in the staged diff is a finding, not a
regeneration — the U3 gate rule, restated.

## The U4 slice

**Ships.** Deletion of `Font.kt`, `TtfTextService.kt`, `TtfDrawStringAddon.kt`
and the four legacy adapters in `TextDraw.kt`; the `wrapNativeFace`/
`toCodePoints` move and the scope narrowing; the re-anchored shaping, metrics,
atlas, resource, smoke and web specs; the benchmark migration; and the loader's
extension-contract test.

**Deferred.** The per-family configuration cache, its `Mutex` single-flight,
reference counting and the family/lease leak contract (U6); the legacy modules'
migration to the unified text model beyond the benchmark (none exists).

**Explicitly not in this round.** Any new drawing behavior, any seam change, any
golden regeneration, named instances, `avar`, TTC/OTC, bidi/script itemization.

## Coverage map (each legacy spec's fate)

| legacy spec (cases) | fate | anchor |
|---|---|---|
| `TextGoldenTest` (11) | delete | duplicate of `TtfFontGoldenTest` (fact 5) |
| `TextDrawTest` (24) | delete, residue migrated | `TtfFont*` specs; the fact-6 pins move to them |
| `TtfTextServiceDecalTest` (9) | delete, two pins carried | `TtfFontDecalTest`; the two-size lease geometry and the `Decal.update()` hazard |
| `TtfTextServiceTest` (3) | 2 covered, 1 re-authored | `TtfFontAddonTest`, `TtfFontFamilyTest`; the decorator proof is Decision 3 |
| `TtfDrawStringAddonTest` (9) | residue migrated | `TtfFontAddonTest` (host tab size, default-vs-explicit color/scale, host pixel mode) |
| `FontLoadTest` (3) | delete | `TtfFontFamilyTest` (base64 ≡ bytes, invalid payload rejected) |
| `FontResourceTest` (8) | delete | `TtfFontFamilyTest`, `TtfFontMeasureTest`, `PayloadAllocationTest` (fact 8) |
| `FontLeakReportTest` (3) | delete | U6 owns the contract (Decision 4) |
| `GlyphAtlasGpuTest` (9) | 3 stay, 6 re-anchored | internal `GlyphAtlas`/`GlyphAtlasGpu` |
| `MetricsTest` (2) + `ShapingTest` (6) | re-anchored | internal `TtfPayload` + `NativeFace` |
| `WebNativeFaceTest` (1) | re-anchored | `KGETtfFontService` (the one-buffer web pin) |
| `CoverageTextureSmoke` | re-anchored | internal payload + face + atlas |
| benchmark cells + `TtfCarrierUploadTest` (1) | migrated | `TtfFontAddon` + `TextAddon` (Decision 5) |

## Carried into the micro-plan

- The test contract items the unified path already satisfies are not re-authored;
  the micro-plan enumerates each legacy case's fate and books every migrated pin
  as a test, with fact 6's eight pins as the minimum carry.
- Every public parameter keeps an observable-effect assertion, and the loader's
  decorator proof names what the caller observes (Decision 3).
- The micro-plan states the expected per-target test count after the migration,
  so the gate's reported counts can be read against it (chunk `35`: a suite
  reporting fewer tests than it has still exits 0).
- `buildSrcCheck` and the metadata/kLIB compilation are part of the gate; the
  round deletes public declarations, so a stale reference in the metadata source
  set is a real failure mode.

## Gate

`tools/gradle build`, once, before the two review axes: every target's tests
with the browser suites reporting non-zero counts, ktlint, the metadata/kLIB
compilation and `buildSrcCheck`. No golden reference moves (Decision 7).

## Confirmed with the owner (2026-10-05)

1. **One retirement round absorbs U5** (Decision 1).
2. **The family/lease leak contract stays U6, and `FontLeakReportTest` is
   deleted with its subject**, the one-round gap recorded (Decision 4).
