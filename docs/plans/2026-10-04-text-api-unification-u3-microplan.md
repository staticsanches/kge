# Text API unification — micro-plan, round U3

**Date:** 2026-10-04. Round **U3** of the unification: `kge-text-ttf` answers the
`KGEFont` model — the payload family, atomic multifile loading, `name`-table
identity, `fvar` discovery, axis configuration, measurement and the two draws,
with the legacy `Font`/`TtfTextService`/`TtfDrawStringAddon` still working until
U4/U5 retire them. Context: the U3 touch-point
(`docs/plans/2026-10-04-text-api-unification-u3-touchpoint.md` — its eleven
decisions and two owner confirmations are settled there), the unification
touch-point and its test contract, the axis findings
(`2026-10-04-variable-font-axes-findings.md`), and decisions chunks `41`/`42`/`43`.

Rounds: U1 core vocabulary + bitmap backend → U2 bundled italic fixtures →
**U3 TTF family/loading/naming/discovery/axes + measure + draws** → U4/U5 legacy
retirement → U6 caching/ownership hardening.

## Scope

`kge-text-ttf` (production + tests), `kge-font-roboto` (one manifest member and
its call sites), and — added by the round-1 delta at the owner's direction — the
single web `ByteBuffer` accessor in `kge-core`. **No golden reference moves**:
the CPU/GPU raster is untouched, so a golden diff is a finding.

Public additions, exactly two names:
`KGETtfFontService : KGEOverridable` and `TtfFontAddon : TextAddon`. Everything
else — the family, its faces, the configured font, the reader, the payload — is
`internal` or file-`private`.

## Surfaces

```kotlin
// KGETtfFontService.kt — the seam; the family is adopted into [scope].
interface KGETtfFontService : KGEOverridable {
    @KGESensitiveAPI
    suspend fun createResources(scope: ResourceScope, vararg bytes: ByteArray): KGEFont.Family
    @KGESensitiveAPI
    suspend fun createResources(scope: ResourceScope, vararg base64: List<String>): KGEFont.Family
    companion object : KGEOverridable.Proxy<KGETtfFontService>(…), KGETtfFontService
}

// TtfFontAddon.kt — the loading ergonomics over HasResourceScope.
interface TtfFontAddon : TextAddon {
    suspend fun loadFont(vararg bytes: ByteArray): KGEFont.Family =
        KGETtfFontService.createResources(resourceScope, *bytes)
    suspend fun loadFontBase64(vararg base64: List<String>): KGEFont.Family =
        KGETtfFontService.createResources(resourceScope, *base64)
}
```

The seam is `internal` in one respect only: the payload handle and the native
face stay behind `expect`/`actual` (`TtfPayload`, `NativeFace`,
`createNativeFace`, `closeNativeFace`) — the module's existing narrow-seam
convention, widened by the payload alone.

`SfntReader` is `internal` (its consumer is the family, across files), its
helpers file-`private`. `TtfFontFamily`/`TtfFace`/`TtfFont` are file-`private`
behind the two public names, mirroring `CoreFontFamily`/`CoreFace`/`CoreFont`.

## The reader (`SfntReader.kt`, pure Kotlin over the load bytes)

One pass over the table directory, then `name`, `fvar`, `post`, `hhea`:

- **identity** — family name from ID 16, falling back to 1; subfamily name from
  17, falling back to 2; Windows/Unicode record preferred, English (language
  `0x0409`) first, then Windows any language, then Mac English, then the first
  record in table order. A missing ID 1/2 is a rejection.
- **`fvar`** — descriptors in record order (never sorted): tag, `axisNameID`
  resolved through `name` (falling back to the four-character tag), `min`,
  `default`, `max` as `Axis.Value.of(raw)`, and the hidden flag from bit 0.
- **`monospaced`** — `post.isFixedPitch` (offset 12 of the `post` header, read
  for format 2.0 and 3.0 alike) **or** `hhea.numberOfHMetrics == 1`.
- **collection rejection** — a `ttcf` magic is refused, never face index zero.
- a payload with no `fvar` is still a face with an empty descriptor map;
  usability stays the native seam's call.

### Pinned expectations (measured 2026-10-04 from the committed payloads)

| payload | family / subfamily | axes, in `fvar` order | `monospaced` |
|---|---|---|---|
| `Roboto[wdth,wght].ttf` | `Roboto` / `Regular` | `wght` `Weight` 100/400/900 hidden=false; `wdth` `Width` 75/100/100 hidden=false | false |
| `Roboto-Italic[wdth,wght].ttf` | `Roboto` / `Italic` | identical | false |
| `RobotoMono[wght].ttf` | `Roboto Mono` / `Regular` | `wght` `Weight` 100/400/**700** hidden=false | **true** |
| `RobotoMono-Italic[wght].ttf` | `Roboto Mono` / `Italic` | identical | **true** |

Raw 16.16 values: 100 `6_553_600`, 400 `26_214_400`, 700 `45_875_200`, 900
`58_982_400`, 75 `4_915_200`. The `name` tables carry a single Windows/Unicode
English record each (IDs 1, 2, 3, 4, 6; no 16/17), so the preference and fallback
paths need **hand-built** `name` tables in the test, not the fixtures.

## The family, the faces, the configured font

`TtfFontFamily(name, faces)` owns one `TtfPayload` per face and registers itself
under a fresh private `ResourceScope.Key`. `faces` is the payload order, and
`defaultFace === faces.first()`.

`TtfFace` holds its payload, its `name`, its `monospaced` and its `axes` (the
reader's map, built with `persistentMapOf` in `fvar` order — 0.5.2's
`persistentMapOf` is a `PersistentOrderedMap` and iterates in insertion order,
so the order is contract, not luck). Inert values (`name`, `monospaced`, `axes`)
answer after a close; `family` and `font(...)` fail fast.

`TtfFace.font(scope, size, axes)`:

- validates the requested tags against `this.axes` and each value against its
  axis range, filling every omitted axis with its default, and canonicalizes in
  **ascending tag order** (so Roboto's canonical map iterates `wdth`, `wght` —
  a different order from `Face.axes`' `fvar` order, and both are pinned);
- creates a `NativeFace` over the face's payload with the complete canonical
  coordinates (empty map ⇒ default instance ⇒ no engine call at all);
- builds a `TtfFont` and registers it under its own fresh private key;
- a non-empty request against a face with no axes is rejected.

`TtfFont` holds its `face`, `size`, canonical `axisCoordinates`, its `NativeFace`
and one lazily created `GlyphAtlas`/`GlyphAtlasGpu` for its single size. It is
one flat lease: closing it releases the atlas, the GPU atlas and the native face,
and never the payload (the family owns that). U3 creates native state per lease
with no cache — the cache, reference counting and single-flight are U6's.

## The native seam

```kotlin
internal class TtfPayload(bytes: ByteArray) : KGEResource   // the one core buffer, on every target
internal expect class NativeFace { /* shape, metrics, rasterize — unchanged */ }
internal expect suspend fun createNativeFace(payload: TtfPayload, coordinates: AxisCoordinates): NativeFace
internal expect fun closeNativeFace(face: NativeFace)
```

`TtfPayload` is **common**, not a platform specialization: the core `ByteBuffer`
already *is* the native handle on the JVM (an `actual typealias` to
`java.nio.ByteBuffer`) and already owns the web's `Uint8Array`, so the module
needs neither an `expect class` nor a per-configuration conversion. The kernel's
only change is the widening recorded below.

`AxisCoordinates` is an `internal` value defined beside the seam (its consumers
are the platform actuals) carrying the tags (big-endian 4-byte ints) and the raw
16.16 values **in `fvar` order**, plus its own emptiness. `createNativeFace`
applies it when non-empty:

- **JVM** — `hb_font_set_variations(font, hb_variation_t.Buffer)` from a
  `MemoryStack` buffer, then `FT_Set_Var_Design_Coordinates(ftFace, CLongBuffer)`
  (`stack.mallocCLong`, never `IntBuffer`). The face no longer closes the
  payload; the payload is no longer created inside the face.
- **web** — `HarfBuzzFont.setVariations([Variation(tag, value), …])` through two
  new `@JsModule` externals, then the raw
  `ft.module._FT_Set_Var_Design_Coordinates(face.ptr, n, ptr)` with
  `_malloc`/`HEAP32`/`_free` (the axis spike's proven pattern; `HEAP*` views are
  re-read from the module, never cached).

Both calls receive the same complete map, because neither engine reads the
other's store and `hb_font_set_variations` resets what it omits.

## Files

| file | change |
|---|---|
| `kge-text-ttf/build.gradle.kts` | `implementation(libs.kotlinx.collections.immutable)` |
| `…/ttf/SfntReader.kt` | new: the pure reader |
| `…/ttf/TtfPayload.kt` | new: the shared payload handle — one **common** class (the platform actuals were deleted in the round-1 delta) |
| `…/ttf/NativeFace.kt` | `AxisCoordinates`; `createNativeFace(payload, coordinates)` |
| `jvmMain/…/NativeFaceJvm.kt` | the two variation calls; the face stops owning the payload |
| `webMain/…/NativeFaceWeb.kt` | `setVariations` + the raw FreeType call; the payload owns the bytes |
| `webMain/…/HarfBuzzWebExternals.kt` | `Font.setVariations`, `Variation` |
| `webMain/…/FreeType.kt` | `FreeType.module` and `Face.ptr` (an external class's members live in its own file) |
| `webMain/…/FreeTypeRawWebExternals.kt` | new: the Emscripten module's shape and its variation export |
| `…/ttf/TtfFontFamily.kt` | new: family/face/configured font (file-private) |
| `…/ttf/KGETtfFontService.kt`, `…/ttf/TtfFontAddon.kt` | new: the two public names |
| `…/ttf/TextLayout.kt`, `…/ttf/TextDraw.kt` | re-anchored to `(NativeFace, sizePx, atlas)` |
| `…/ttf/Font.kt` | re-based on `TtfPayload` + the re-anchored internals (legacy) |
| `kge-font-roboto/build.gradle.kts` + the 96 sites | `variableFont` → `romanFont` |

## Steps (TDD: red → green per feature)

1. **The reader** (red → green): `SfntReaderTest` — the four payload pins above,
   the hand-built `name` preference/fallback tables, `post`/`hhea` combinations,
   `ttcf` rejection, malformed-directory rejection. Red: `SfntReader` does not
   exist. Pure `commonMain`; no platform code.
2. **The payload and the variation seam** (red → green): `NativeVariationTest` —
   one `TtfPayload` serves two `NativeFace`s; at 16 px `wght = 400` shapes `A`
   with advance `10.4375f` and coverage sum `9983`, `wght = 900` with `10.90625f`
   and `16978` (the spike's cross-platform numbers; the default is round D's
   hand-derived table); closing one face leaves the other usable; closing the
   payload after both. Red: the coordinate parameter does not exist. The room's
   existing suite (`ShapingTest`, `GlyphRasterTest`, `TextDrawTest`, the web and
   JVM native smokes) is the refactor's regression net.
3. **The family and the loader** (red → green): `TtfFontFamilyTest` — two-payload
   load gives one family, faces in payload order, `defaultFace === faces[0]`,
   names/monospaced/axes per the table; reversed payload order moves the default;
   mixed families, duplicate subfamilies and a garbage payload each reject and
   publish nothing (the leak detector observes no stranded buffer); scope
   registration and close-once; `faces`/`defaultFace` fail fast after close.
4. **The configured font** (red → green): `TtfFontMeasureTest` — canonical
   `axisCoordinates` (defaults filled, ascending tag order, `wdth` before
   `wght`); a differently ordered request yields the same map; unknown tag and
   out-of-range value reject before any native state; `measureText("")` is
   `(0, 0)`; the pinned 16 px boxes (`A` 11x19, ` ` 4x19, `AAAA` 42x19,
   `A\nB` 11x38, `A\tB` 26x19, `AAAA\tB` 58x19) reproduce through
   `KGEFont.measureText`; a `wght = 900` lease measures wider than its `400`
   twin; lease close leaves an equal lease usable, and the inert values answer
   while the handles and `measureText` fail fast.
5. **The two draws** (red → green): a new `TtfFontDrawTest` and
   `TtfFontDecalTest` drive the configured font through `KGEFont.drawText`/
   `drawTextDecal` — the eleven `text/*.png` goldens reproduce unchanged through
   the new API, and blend/`Custom`/tab/scale/no-draw-target behavior is re-pinned
   with the legacy specs' own expectations, never with regenerated ones. The
   legacy specs stay untouched this round; U4/U5 delete them as they delete the
   names they call.
6. **The addon** (red → green): `TtfFontAddonTest` — a headless `Engine` host
   loads both payloads into its own scope, `textFont = family.defaultFace.font(…)`
   changes subsequent default calls, a second configured font is named per call
   without touching the principal, and `measureText`/`drawText`/`drawTextDecal`
   answer through `TextAddon`.
7. **The rename** (mechanical, no new red): the manifest member `variableFont`
   becomes `romanFont`; the generated accessor, `kge-font-roboto`'s two specs,
   `kge-text-ttf`'s test source sets and the benchmark follow; `buildSrc`'s suite
   already takes member names from the spec and needs no change.
8. **Gate, review, decisions entry, commit**: `tools/gradle build` (it already
   runs `buildSrcCheck`); the reported counts read per the `#35`/`#38`/`#40`
   rules, both browser suites non-zero; the two review axes on the staged tree;
   the entry (chunk `44`) staged before the final review round; the marker; one
   squashed commit.

Steps 1 and 2 are independent of 3–6 and may be dispatched first; 3 depends on
1 and 2, 4–6 depend on 3, and 7 is independent. Implementation dispatches never
overlap — one Gradle workspace.

## Resolved at dispatch

### Step 2 (2026-10-04, orchestrator)

- **~~The web payload is a `Uint8Array` and allocates no engine buffer.~~**
  Superseded by the round-1 delta below: the payload is common over the core
  `ByteBuffer` on every target, so the web allocates one tracked buffer per
  payload exactly as the JVM does.
- **`Font.load` keeps an explicit try/catch** instead of `letClosingIfFailed`,
  because a `crossinline` lambda cannot host the suspend face construction.

### Steps 3–5 (2026-10-04, orchestrator)

`TtfFace : KGEFont.Face` declares `font(...)`, so the configured font cannot be
deferred past the family that owns it: the interface forces the whole production
surface into one dispatch. Step 3 therefore ships the family, the loader, the
re-anchored internals and the configured font — including both draws, each with
its own red assertion — and steps 4 and 5 are the *test* stages that deepen it:
the axes/measure contract and then the drawing contract with the eleven goldens.
The split stays test-first per feature inside each dispatch.

### Step 4 (2026-10-04, orchestrator)

Decision 10 was not honoured in step 3, because that dispatch forbade build-file
edits: `Face.axes` and the canonical `axisCoordinates` are insertion-ordered
`LinkedHashMap`s today, which pins both orders but not the persistent backing the
touch-point decided. Step 4 restores it — the dependency is declared and both
published maps become persistent — and pins the immutability observably
(`is MutableMap` is false) rather than by naming the implementation type.

### Round-1 review delta (2026-10-04, orchestrator)

The first review round returned Spec **FAIL** (one Critical, two Minors) and
Standards **PASS** (three Minors). The delta fixes: a lease was not invalidated
by its family's close (`TtfFont.checkOpen` now consults the owner, as the bitmap
sibling does — the Critical); a Unicode-platform English `name` record was
ranked below a non-English Mac record; `Family.faces` was a plain `ArrayList`
instead of a persistent list; and the JVM payload-allocation pin was restored
exactly. Two Standards Minors are dispositioned rather than coded:
`canonicalAxisCoordinates` stays `internal` with `commonTest` as its named
consumer (the axisless rejection has no public route with the shipped fixtures),
and `wrapNativeFace`/`toCodePoints` stay in the legacy `Font.kt` until U4/U5
move them beside the concept they serve.

**The payload became common in the same delta.** `TtfPayload` is now one
`commonMain` class over `ResourceWrapper<ByteBuffer>`, its two platform actuals
are deleted, and the seam is three `expect` names again. That needed exactly one
`kge-core` change — the web `ByteBuffer`'s backing typed array, `nativeBytes`,
widened from `internal` to `public` — because the JVM's equivalent is already
public and raw (`actual typealias ByteBuffer = java.nio.ByteBuffer`) while the
web hid it. This amends the Scope line below and touch-point Decisions 1 and 4.

## Out of scope (round U3)

The configuration cache, its `Mutex` single-flight, reference counting and the
lease leak contract (U6); deleting `Font`/`TtfTextService`/`TtfDrawStringAddon`
and migrating their remaining specs (U4/U5); named instances, `avar`, TTC/OTC
support, bidi/script itemization, axis convenience properties; any golden
regeneration; the benchmark's scene migration.

## Gate

`tools/gradle build` green: every target's tests, ktlint, the metadata/kLIB
compilation and `buildSrcCheck`. Both browser suites must report non-zero counts
(chunk `35`), and the counts are read, not assumed (chunk `40`'s record
correction). No golden moves.
