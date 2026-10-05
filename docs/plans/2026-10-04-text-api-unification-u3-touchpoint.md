# Text API unification — round U3 (TTF family, loading, naming, discovery, axes): touch-point

**Status: decided with the owner, 2026-10-04.** This round turns
`kge-text-ttf`'s private face machinery into the module's public answer to the
`KGEFont` model U1 landed and U2 supplied fixtures for. Design is taken from the
unification touch-point
(`docs/plans/2026-10-04-text-api-unification-touchpoint.md`); this document only
confirms the U3 slice against the sources of truth, pins the facts the round
needs, and records the decisions and divergences the entry will cite.

Rounds: U1 core vocabulary + bitmap backend → U2 bundled italic fixtures →
**U3 TTF family/loading/naming/discovery/axes + measure** → U4 TTF CPU draw →
U5 TTF decal draw → U6 caching/ownership hardening.

Material: the unification touch-point (§"TTF/OTF family", §"Variable-font axes",
§"Ownership, caching, and scope", the test contract); the unification findings
(`2026-10-01-text-api-unification-findings.md`); the axis spike
(`2026-10-04-variable-font-axes-findings.md`, `SUPPORT NOW`); decisions chunks
`41` (U1), `42` (U1b) and `43` (U2); the current module sources.

## Verified facts (this touch-point)

1. **The four bundled payloads, read in `bash` from the committed files.** Each
   is sfnt `00010000`, one face per file, name-table IDs 1 and 2:

   | payload | bytes | `name[1]` family | `name[2]` subfamily | `fvar` axes (tag min/def/max, nameID, name) |
   |---|---|---|---|---|
   | `roboto/Roboto[wdth,wght].ttf` | 488,584 | `Roboto` | `Regular` | `wght` 100/400/900 (256, `Weight`), `wdth` 75/100/100 (257, `Width`) |
   | `roboto/Roboto-Italic[wdth,wght].ttf` | 530,944 | `Roboto` | `Italic` | identical to the roman |
   | `roboto-mono/RobotoMono[wght].ttf` | 183,700 | `Roboto Mono` | `Regular` | `wght` 100/400/**700** (256, `Weight`) |
   | `roboto-mono/RobotoMono-Italic[wght].ttf` | 196,792 | `Roboto Mono` | `Italic` | identical to the roman |

   `axisSize` is 20 and `fvar` version is 1.0 in all four; every axis flag is 0
   (none hidden); Roboto carries 18 named styles and Roboto Mono 7. Roboto Mono's
   `wght` maximum is **700**, not Roboto's 900 — the two families differ and a
   pin that assumes one is a defect.

2. **Each bundled `name` table carries exactly one record per ID** — platform 3
   (Windows), encoding 1 (Unicode BMP), language `0x0409` — for IDs 1, 2, 3, 4
   and 6 only. No ID 16 (typographic family) and no ID 17 (typographic
   subfamily), and no second language. The English preference and the
   deterministic fallback the touch-point requires are therefore **not exercised
   by the shipped fixtures** and need synthetic `name`-table cases in the
   micro-plan.

3. **`post.isFixedPitch` is 0 in all four payloads**, including both Roboto Mono
   faces, which are genuinely monospaced: their `hhea.numberOfHMetrics` is 1
   (a single advance, 1229 roman / 1202 italic) and their `OS/2.panose[3]`
   (`bProportion`) is 9 (*Monospaced*), while both Roboto faces carry 1326
   metrics and panose 0. The U1b-recorded route — "a TTF face takes it from the
   payload's own metric (`post.isFixedPitch`)" — therefore answers `false` for a
   shipped monospaced face. FreeType cannot rescue it: `FT_FACE_FLAG_FIXED_WIDTH`
   is set **only** from `face->postscript.isFixedPitch`
   (`VER-2-14-3 src/sfnt/sfobjs.c:1125`), and the field is read straight from the
   `post` header (`src/sfnt/ttload.c:1341`). Both Roboto faces are `post` 3.0 and
   both Roboto Mono faces are `post` 2.0; the flag is 0 in every case.

4. **The current TTF measurement answers `(0, lineHeight)` for the empty
   string.** `walkText` counts one line even for `""` and `TextDrawTest.kt:30`
   pins `getTextSize(font, "", 16, 4) == Int2D(0, 19)`. Touch-point contract item
   25 requires `(0, 0)` for every implementation, and the bitmap backend already
   early-returns `Int2D(0, 0)`. U3 changes the TTF answer, so that pin moves with
   the old API.

5. **The variation seam does not exist yet.** `NativeFace` (`NativeFace.kt:11-24`)
   has no coordinate parameter anywhere; `createNativeFace` takes only the
   payload and `Font.load` builds a complete new face per call. The measured
   capability (`FT_Set_Var_Design_Coordinates` + `hb_font_set_variations` on the
   JVM, `setVariations` + the raw `ft.module._FT_Set_Var_Design_Coordinates` on
   the web) is proven by the axis spike, not yet wired.

6. **Shaping and rasterization hold independent coordinate stores**, and the
   `hb_ft_*` bridge is unavailable on both platforms (no exported symbol on the
   JVM natives, no such code in the `HB_TINY` wasm). The complete canonical
   coordinate map must be pushed to both engines explicitly;
   `hb_font_set_variations` *"overrides all existing variations"*, so a partial
   application silently resets an axis.

7. **The unified public surface already exists and is empty of TTF.**
   `KGEFont`/`Family`/`Face`/`Axis`/`Size` (`kge-core …/text/KGEFont.kt`),
   `TextAddon` with `HasResourceScope` (`kge-core …/engine/addon/TextAddon.kt`)
   and `Engine : TextAddon` are live; `kge-text-ttf` still publishes `Font`,
   `TtfTextService` and `TtfDrawStringAddon` and nothing else.

8. **The legacy module surface has three public types and two public
   consumers.** `Font` (a loaded face with a per-size atlas map),
   `TtfTextService` and `TtfDrawStringAddon`; `kge-benchmark`'s `Scene.kt:40`
   implements `TtfDrawStringAddon`, and two test hosts do.

9. **The current internals are already correct in form, only mis-anchored.**
   `TextLayout.walkText`/`measureText` and `TextDraw.drawText`/
   `drawStringDecalText` take a `Font` plus a per-call `sizePx`; the atlas map
   lives on `Font` (`Font.kt:20-21`); `GlyphAtlasGpu` is created per size on
   first use. The configured font of the unified model is the same object with a
   fixed size.

10. **`kotlinx-collections-immutable` 0.5.2 is already in the version catalog
    and used by `kge-core`**; `kge-text-ttf` does not declare it (U1 recorded
    this as U3's).

11. **The `variableFont` → `romanFont` rename touches 96 call sites** — 86 in
    `kge-text-ttf`'s test source sets, 2 in `kge-benchmark`, 8 in
    `kge-font-roboto`'s bundle spec (chunk `43`); a `grep` over the tree finds
    110 occurrences, the extra 14 being the generated accessor and the
    generator's own suite.

## Decisions

### Decision 1 — the family is the module-private counterpart of the bitmap family (orchestrator)

`TtfFontFamily`/`TtfFace`/`TtfFont` are file-`private` types in `commonMain`,
mirroring `CoreFontFamily`/`CoreFace`/`CoreFont` (`KGECoreFontService.kt:66-310`).
The module publishes exactly two new public names — `KGETtfFontService` and
`TtfFontAddon` — and both answer `KGEFont.Family`; no TTF-specific family, face
or configured-font subtype is published.

Rejected: a public TTF family subtype exposing axes or payload metadata (the
touch-point's "both are the common types, and its concrete family stays internal
to the module"); publishing the payload's bundle metadata on `Family` (the
touch-point keeps it in `kge-font-roboto`).

### Decision 2 — `monospaced` is measured from the payload's own advances (owner, 2026-10-04; revises the U1b decision recorded in the touch-point's 2026-10-04 revision)

`Face.monospaced` answers `post.isFixedPitch || hhea.numberOfHMetrics == 1`. The
contract's definition is behavioural — "whether the face advances every glyph by
one width" — and fact 3 shows the declared flag alone answers `false` for a
shipped monospaced face. `numberOfHMetrics == 1` is the same table's own truth
for the default instance (every glyph repeats the single stored advance), costs
one `hhea` read, and is independent of the unreliable `post` flag. The face
still *declares* it: no per-instance raster measurement is taken.

Rejected: `post.isFixedPitch` alone (fact 3: wrong for both Roboto Mono faces,
and FreeType's own flag has the same blind spot); `OS/2.panose[3] == 9` (a
heuristic that is zero in most third-party fonts, and it agrees with the
advances here only by luck of the fixtures); reading advances from the native
face (two backend implementations and a hover over "declared, not measured").

### Decision 3 — the reader is pure Kotlin in `commonMain` over the load bytes (orchestrator)

One internal `sfnt` reader parses `name`, `fvar`, `post`, `hhea` and the table
directory from the `ByteArray` the loader already holds, on all three targets,
before any native state exists. It resolves the family name (ID 16, falling back
to 1) and the subfamily name (17, falling back to 2), preferring Windows/Unicode
English and then a deterministic fallback (Windows any language, then Mac
English, then the first record by record order); it builds the axis descriptors
in `fvar` order with the axis name resolved by `axisNameID`; it rejects a
`ttcf`/`OTTO`-collection magic (`ttcf`) rather than selecting index zero.

An axis whose `axisNameID` has no record falls back to its four-character tag —
non-null by construction, deterministic, and honest about the payload.

Rejected: `FT_Get_MM_Var`/`getAxisInfos` as the source (two backends, two name
resolutions, and the web one is unwrapped — the axis findings §7.2); parsing
through HarfBuzz's `referenceTable` (one more seam for the same answer, and the
web view is a live heap subarray).

### Decision 4 — one payload copy per face; native state per configuration (orchestrator)

The family owns one `TtfFace` per payload; a `TtfFace` owns that payload's
buffer once, and every configured font creates its own native handles over it
(HarfBuzz blob/face/font and FreeType face). The seam gains one parameter — the
complete canonical coordinate map — and the JVM's `openNativeFace` no longer
closes the buffer it was handed.

Web keeps paying the wasm-heap copies the axis findings measured (one HarfBuzz
blob plus one FreeType face per configuration); the Kotlin side must not decode
or copy the payload a second time.

Rejected: a native clone per *size* (size is a per-call parameter of the native
seam and does not belong in the sharing key); closing the staging buffer after
the first configuration (later configurations need the same bytes).

### Decision 5 — one complete coordinate map is pushed into both engines at creation (orchestrator)

`createNativeFace(bytes, coordinates)` applies the whole canonical map — every
declared axis, defaults filled in — to HarfBuzz (`hb_font_set_variations` with
one `hb_variation_t` per axis, or `setVariations` on the web) **and** to
FreeType (`FT_Set_Var_Design_Coordinates`, through the wrapper on the JVM and
the raw `ft.module` export on the web). Non-default coordinates must move shaped
advances *and* raster ink, and the round pins both.

Rejected: applying only the non-default axes (fact 6 — the call resets what it
omits); relying on `hb_ft_*` (unavailable on both platforms); a `setVariations`
call with only the caller's map.

### Decision 6 — the configured font owns its atlas; caching stays U6 (orchestrator)

`TtfFont` holds the configured `size`, the canonical `axisCoordinates`, its
native face and a single `GlyphAtlas`/`GlyphAtlasGpu` for its one size. U3
creates native state per lease with no cache and no reference count; the
per-family configuration cache, its single-flight and the leak contract stay
U6, exactly as chunk `41` recorded.

### Decision 7 — the loader is a `suspend` service, with the addon on top (orchestrator)

```kotlin
interface KGETtfFontService : KGEOverridable {
    @KGESensitiveAPI
    suspend fun createResources(scope: ResourceScope, vararg bytes: ByteArray): KGEFont.Family
    @KGESensitiveAPI
    suspend fun createResources(scope: ResourceScope, vararg base64: List<String>): KGEFont.Family
    companion object : KGEOverridable.Proxy<…>, KGETtfFontService
}
```

The addon form carries the scope from `HasResourceScope`:

```kotlin
interface TtfFontAddon : TextAddon {
    suspend fun loadFont(vararg bytes: ByteArray): KGEFont.Family
    suspend fun loadFontBase64(vararg base64: List<String>): KGEFont.Family
}
```

Names follow `KGECoreFontService.createResources` for the seam and the
touch-point's "the addon carries the loading ergonomics"; the concrete method
names are the micro-plan's to pin. Loads are atomic: any invalid, duplicated,
mismatched-family or unsupported payload fails the whole call and releases every
allocation the attempt made, publishing no partial family.

### Decision 8 — `measureText("")` is `(0, 0)` (orchestrator)

The TTF configured font returns `Int2D(0, 0)` for the empty string before
touching metrics, matching the bitmap backend and contract item 25 (fact 4).

### Decision 9 — the bundled accessor is renamed `romanFont` (orchestrator)

The manifest member in `kge-font-roboto/build.gradle.kts` becomes `romanFont`,
the generated accessor follows, and the 96 recorded call sites move with it
(fact 11). The order-contract KDoc the generator emits above the first member is
unchanged.

### Decision 10 — U3 declares `kotlinx-collections-immutable` as `implementation` (orchestrator)

`kge-text-ttf` gains the catalog dependency U1 recorded as U3's, so a published
`Face.axes` and `KGEFont.axisCoordinates` are `Map` views over persistent
collections. `implementation`, never `api`: a `PersistentMap` is a `Map`.

### Decision 11 — the legacy surface is removed as the unified one replaces it (orchestrator)

`Font`, `TtfTextService` and `TtfDrawStringAddon` are deleted, not deprecated —
but only once nothing calls them. U3 lands the unified loading/measure/draw; U4
and U5 remove the legacy CPU and decal entry points and migrate their tests and
goldens, and U6 is unchanged. No new code may call a legacy name from U3 onward.

## The U3 slice

**Ships.** The family/face/configured-font implementation over the reader; the
`KGETtfFontService`/`TtfFontAddon` loader; axis discovery and validation;
`measureText`; the `romanFont` rename; the immutable-collections dependency; and
the TTF `KGEFont`'s `drawText`/`drawTextDecal` — which are **not** stubs.

**Why the draws cannot be deferred.** `KGEFont` declares all three operations,
so a configured TTF font without them would have to throw, and "never a
provisional API a later concept must break" forbids that. The drawing internals
already exist (`TextLayout.walkText`, `TextDraw.drawText`,
`drawStringDecalText`); U3 re-anchors them from `(Font, sizePx)` to
`(native face, sizePx)` and the configured font passes its own fixed size. U4
and U5 are therefore the rounds that **retire** the legacy surfaces and move
their specs and goldens, not the rounds that make the new path exist.

**Deferred.** The configuration cache, its `Mutex` single-flight, reference
counting and the lease leak contract (U6); deletion of `Font`/`TtfTextService`/
`TtfDrawStringAddon` (U4/U5); any golden regeneration — the existing references
were authored against the CPU raster, which this round does not change.

**Explicitly not in this round.** Named instances, `avar`, TTC/OTC support,
bidi/script itemization, and any new axis convenience property.

## Confirmed with the owner (2026-10-04)

1. **`Face.monospaced` is `post.isFixedPitch || hhea.numberOfHMetrics == 1`**
   (Decision 2). The measured defect of the recorded route decided it: all four
   bundled payloads declare `post.isFixedPitch = 0`, including the two Roboto
   Mono faces, whose single `hmtx` metric and panose 9 say otherwise, and
   FreeType's own fixed-width flag derives from the same field. Rejected: the
   flag alone (reproduces the defect), panose alone (a heuristic that is zero in
   most third-party fonts), and reading advances from the native face.
2. **U3 ships the configured font's `measureText`, `drawText` and
   `drawTextDecal` working**, re-anchoring the existing internals; U4 and U5
   retire `Font`/`TtfTextService`/`TtfDrawStringAddon` and migrate their specs
   and goldens. The round list's "U4 CPU draw"/"U5 decal draw" name the rounds
   that make the unified path the only path, not the rounds that make it exist.
   Rejected: a measure-only U3, which would force the draws to throw — the
   provisional API the repository forbids; and a U3 whose draws delegate inward
   to the legacy service, which pays the re-anchoring in U4/U5 for no gain.

## Carried into the micro-plan

The touch-point's test contract, restricted to what this round can pin:
items 1–17 (model and validation, loading and family identity, axes), 21–25
(close rules, allocate-then-fail, the empty measurement), 27–31 (the drawing
contract, re-anchored), 32–34 and 36–43 (addon and replacement surface, the
vocabulary revision) — with 18–20 and 22 as U6's. The micro-plan turns each into
an executable test, with every public parameter receiving an observable-effect
assertion; the synthetic `name`-table cases of fact 2 are the micro-plan's to
author.

## Gate

`tools/gradle build`, once, before the two review axes: every target's tests
with the browser suites reporting non-zero counts (chunk `35`), ktlint, the
metadata/kLIB compilation and `buildSrcCheck`. No golden moves are expected, so
a golden change in the diff is a finding, not a regeneration.
