## 2026-10-04 — Text API unification, round U3: the TTF family, its loading and axes, and the unified draws

The round that turns `kge-text-ttf`'s private face machinery into the module's
answer to the `KGEFont` model U1 landed and U2 supplied fixtures for. Rounds: U1
core vocabulary + bitmap backend → U2 bundled italic fixtures → **U3 TTF
family/loading/naming/discovery/axes + measure + draws** → U4/U5 legacy
retirement → U6 caching/ownership hardening. Touch-point and micro-plan:
`docs/plans/2026-10-04-text-api-unification-u3-touchpoint.md` and
`docs/plans/2026-10-04-text-api-unification-u3-microplan.md`.

### Shipped

- **A pure-Kotlin `sfnt` reader** (`SfntReader.kt`, `commonMain`): the table
  directory, the `name` table (ID 16 → 1, 17 → 2, Windows/English preferred with
  a deterministic fallback), `fvar` descriptors in record order with the axis
  name resolved by `axisNameID` (the tag when absent), `post.isFixedPitch` and
  `hhea.numberOfHMetrics`. `ttcf` collections are rejected rather than opened at
  face index zero.
- **One decoded payload per face, shared by every configuration**
  (`TtfPayload`, one `internal` `commonMain` class over
  `ResourceWrapper<ByteBuffer>`): every target allocates one core buffer per
  payload, and a native face neither owns nor releases it. The web reaches its
  backing `Uint8Array` through that buffer, so no configuration converts or
  copies the payload again.
- **The variation seam** (`createNativeFace(payload, coordinates)` with
  `AxisCoordinates`): the complete canonical map goes into HarfBuzz
  (`hb_font_set_variations`, `setVariations`) **and** FreeType
  (`FT_Set_Var_Design_Coordinates` through the wrapper on the JVM, the raw
  `ft.module._FT_Set_Var_Design_Coordinates` on the web), in `fvar` order, since
  neither engine reads the other's store and a partial HarfBuzz set resets what
  it omits.
- **`KGETtfFontService`**, the public loading seam, and **`TtfFontAddon :
  TextAddon`** with `loadFont`/`loadFontBase64`: atomic multifile loads, exact
  family-name match, duplicate-subfamily rejection, payload order = face order,
  first payload = `defaultFace`, and every allocation of a failed attempt
  released.
- **The family, its faces and the configured font** (file-`private`): `Face.axes`
  in `fvar` order, `axisCoordinates` canonical in ascending tag order with
  defaults filled, and one `TtfFont` per `(face, size, coordinates)` holding the
  configured size, the canonical map, its native face and its one atlas.
- **The internals re-anchored**: `walkText`/`measureText`/`drawText`/
  `drawStringDecalText` now take the native face plus the per-size atlas
  accessors, so the legacy `Font` and the new configured font drive one
  implementation. The legacy public surface is untouched and survives to U4/U5.
- **`kotlinx-collections-immutable`** declared by the module; `Face.axes` and
  `axisCoordinates` are persistent maps behind the read-only interface, and the
  immutability is pinned observably (`is MutableMap` false).
- **The accessor rename** `variableFont` → `romanFont` (100 replacements across
  the manifest, the module's test source sets, the benchmark and the bundle
  specs; the resulting tree carries 109 `romanFont` references), deferred by U2
  to this round.

### Decisions

- **`Face.monospaced` is `post.isFixedPitch || hhea.numberOfHMetrics == 1`**
  (owner, 2026-10-04), revising the U1b route. Measured: all four bundled
  payloads declare `post.isFixedPitch = 0`, including both Roboto Mono faces,
  which are genuinely monospaced (one `hmtx` metric, panose 9); FreeType's own
  `FT_FACE_FLAG_FIXED_WIDTH` derives from the same field
  (`sfobjs.c:1125` in `VER-2-14-3`), so the flag alone reports a monospaced face
  as proportional.
- **The round ships the configured font's three operations at once.** `KGEFont`
  declares `measureText`, `drawText` and `drawTextDecal`, so a TTF font without
  them would have to throw; U4/U5 are the rounds that retire the legacy entry
  points and migrate their remaining specs, not the rounds that make the unified
  path exist.
- **The payload is common, and the core's web buffer exposes its storage.** The
  first implementation made `TtfPayload` an `expect class` — the JVM holding the
  engine buffer, the web holding the typed array. The round-1 review delta
  collapsed it, because the JVM half was ceremony: the core `ByteBuffer` *is* the
  native handle there (`actual typealias ByteBuffer = java.nio.ByteBuffer`) while
  the web hid its own behind `internal`. The one `kge-core` change is
  `nativeBytes` widened to `public`, with the text module as its named consumer
  (the GL backend already used it in-module). This is symmetry with the JVM,
  where a consumer could always reach the raw buffer, not a new hole.
- **`measureText("")` is `(0, 0)`**, matching the bitmap backend and the
  touch-point's item 25. The legacy TTF answer was `(0, lineHeight)`
  (`TextDrawTest.kt:30`); that pin moves with the legacy API in U4/U5.
- **The reader is the discovery source on all three targets**, with FreeType's
  `FT_Get_MM_Var` and the web's `getAxisInfos` left as cross-checks rather than
  the source — one parser, one name resolution, no extra seam.

### Divergences and retained differences

- **`post.isFixedPitch` disagrees with the shipped monospaced payload**, and so
  does FreeType's fixed-width flag; the round answers from the payload's own
  `hmtx` instead. Recorded in the touch-point's fact 3 and Decision 2.
- **Three existing tests were adapted in place**, none of their pins dropped:
  `FontResourceTest` asserts the one engine buffer a load allocates is cleaned,
  and that rasterizing adds exactly one chart; `WebNativeFaceTest` pins one
  tracked payload buffer, released with the font, plus a shaping assertion; and
  `GlyphRasterTest` changed setup only.
- **The `decal` spec compares geometry, not the carrier.** `Decal` has no
  `equals` and the configured font owns its own carrier, so the new spec asserts
  the derived destination, mode, structure, vertex count and tint against a
  hand-built instance and pins carrier reuse with identity; the UVs stay the E3
  carrier specs' contract.
- **`@file:Suppress("ktlint:standard:filename")`** on the two files whose
  plan-mandated names differ from their single non-`private` class
  (`SfntReader.kt`, `FreeTypeRawWebExternals.kt`), the precedent
  `KeyboardKeyWeb.kt` set.
- **The web raw FreeType call is a minified-build internal.** The
  `_FT_*` export names and the hand-written module shape carry no stability
  promise, so a dependency bump must re-run the axis spike (the axis findings'
  §10 residual, carried here).
- **The legacy `Font`/`TtfTextService`/`TtfDrawStringAddon` remain public and
  working**, with the unified path beside them. This is the confirmed U3/U4/U5
  split, not a forgotten removal; no new code calls them.

### Not in this round

The configuration cache, its `Mutex` single-flight, reference counting and the
lease leak contract (U6); deleting the legacy public surface and migrating its
remaining specs and goldens (U4/U5); named instances, `avar`, TTC/OTC support,
bidi/script itemization and axis convenience properties.

### Review

The first review round ran on the staged tree (Standards **PASS**, three
Minors; Spec **FAIL**, one Critical and three Minors) and produced a delta:

- **Critical, fixed**: a configured font was not invalidated by its family's
  close — `TtfFont.checkOpen` consulted only its own flag while
  `TtfFontFamily.close` released the payload its native face still pointed into
  (a JVM use-after-free, silent on the web). The lease's guard now consults the
  owner, as `CoreFont` does, and a lease held past `family.close()` fails fast on
  its handles and reading operations while `size`/`axisCoordinates` stay inert.
- **Fixed**: a Unicode-platform English `name` record was ranked below a
  non-English Mac record; `Family.faces` was a plain `ArrayList` rather than a
  persistent list; the JVM payload-allocation pin, weakened to
  `shouldBeLessThanOrEqualTo 1` by the earlier web asymmetry, is exact again; and
  this entry's own rename count — 109 replacements for a diff that replaces 100
  — was corrected.
- **Dispositioned, not coded**: `canonicalAxisCoordinates` stays `internal` with
  `commonTest` as its named cross-source-set consumer — the axisless rejection
  has no public route with the shipped fixtures; and `wrapNativeFace`/
  `toCodePoints` stay in the legacy `Font.kt`, to be moved beside the concept
  they serve when U4/U5 delete that file.

The second round passed both axes. Its three Minors were accuracy defects of
this entry — a stale `WebNativeFaceTest` premise, the per-spec counts in the
breakdown below, and the round-1 tally — and are corrected here, ahead of the
third round that reviews their correction.

### Verification

`tools/gradle build` green on the staged tree: every target's tests, ktlint, the
metadata/kLIB compilation and `buildSrcCheck`. Counts per module and target,
fresh from the gate's own result XMLs: `kge-core` 746 jvm / 781 js / 781 wasmJs
(the three jvm skips are the pre-existing GLFW-window smokes); `kge-text-ttf`
**201 / 203 / 203**, up from 107 / 110 / 110 by 94 jvm and 93 per browser target
— 26 reader, 4 variation, 17 family (including the immutability pins and the
round-1 lease-invalidation case), 13 measure, 8 CPU, 8 decal, 11 golden, 6
addon, and 1 JVM-only payload-allocation pin; `kge-font-roboto` 10 / 10 / 10;
`kge-test-support` 8 / 9 / 9; `kge-benchmark` 21 jvm / 22 wasmJs; `buildSrc` 27.
Both browser suites report non-zero counts, so no target is a phantom green
(#35).

Two defects were caught by the gate and fixed before it first went green, both
invisible to the per-target runs the implementation dispatches used:

- **`expect class TtfPayload : KGEResource` did not declare `close()`.** The
  jvm/js/wasmJs compilations passed because both `actual`s provide the body, but
  `:kge-text-ttf:compileCommonMainKotlinMetadata` failed on the missing abstract
  member. Declaring `override fun close()` in the `expect` body then required the
  `actual` modifier on both platform declarations. The round-1 delta removed the
  `expect` class entirely.
- **An unused import in `TtfFontAddonTest.kt`**, which `ktlintCheck` inside
  `build` rejects.

Both are the reason the gate is `build` and not `allTests` (chunk `38`'s rule).

The eleven `text/*.png` references reproduce byte-for-byte through the new
`KGEFont` path on all three targets, and no reference moved. The round closes
only when the two review axes pass on this exact staged tree, and the marker
names the report that carries its hash.
