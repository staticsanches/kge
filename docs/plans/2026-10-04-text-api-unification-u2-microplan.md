# Text API unification — micro-plan, round U2 (bundled italic fixtures)

**Date:** 2026-10-04. Round **U2** of the unification work, the fixtures the TTF
family needs before it can be built. Context: the touch-point
(`2026-10-04-text-api-unification-touchpoint.md`, §"TTF/OTF family" — one family
loaded atomically from several payloads; roman and italic atomic per family),
the unification findings (`2026-10-01-text-api-unification-findings.md`), the
axis findings (`2026-10-04-variable-font-axes-findings.md` §10 — the italic
artifacts were never fetched and the touch-point leaves them to the micro-plan),
the bundle round (`2026-09-17-font-bundle-findings.md`) and decisions chunk 31
(the `embedResources` codegen).

Rounds: U1 core vocabulary + bitmap backend → **U2 bundled italic fixtures** →
U3 TTF family/loading/naming/discovery/axes + measure → U4 TTF CPU draw → U5 TTF
decal draw → U6 caching/ownership hardening.

## Scope

`kge-font-roboto` (the committed payloads, `PROVENANCE.md`, the manifest in
`build.gradle.kts`) and `buildSrc` (the embedder's payload list and its tests).
No `kge-core` or `kge-text-ttf` change, no dependency change, no golden change.

This round deliberately does **not** rename the existing accessor member. Its
96 call sites live in `kge-text-ttf`'s test source sets (86), the benchmark (2)
and the bundle spec (8); U3–U5 rewrite those call sites when the family API
lands, so the rename rides along there instead of putting 96 mechanical edits in
a data round. The generator takes member names from the manifest, so this is a
manifest choice, not a codec limitation.

## The payloads (pinned 2026-10-04)

Fetched from `google/fonts@main` and measured in `bash`; the committed romans
re-hash byte-identical to upstream in the same run, so each roman/italic pair
comes from one revision. The previous pins are unchanged.

| file | bytes | sha256 | FNV-1a 64 | base64 chunks (last) |
|---|---|---|---|---|
| `fonts/roboto/Roboto[wdth,wght].ttf` | 488,584 | `d7598e12c5dbef095ff8272cfc55da0250bd07fbdecbac8a530b9b277872a134` | (pinned) | 20 (28,856) |
| `fonts/roboto/Roboto-Italic[wdth,wght].ttf` | 530,944 | `9725a847af6b460ffca162ae66d20dad48b01876137947180b42d7dcd7887182` | `4654176676351108715` | 22 (19,800) |
| `fonts/roboto-mono/RobotoMono[wght].ttf` | 183,700 | `66a80e79d17e4c7cabd162e2916578a4cc08fd19eef6e2a643305eae9c567b2b` | (pinned) | 8 (15,560) |
| `fonts/roboto-mono/RobotoMono-Italic[wght].ttf` | 196,792 | `49ac343bb7b070071e53f0a8a501d68d140ed98ebd0a43b6b8fb96cf22f09ff7` | `3367967902583743594` | 9 (248) |

Both new payloads are sfnt `00010000`, like their romans. The italic faces carry
the **same** axis sets as their romans (`wdth`+`wght` for Roboto, `wght` for
Roboto Mono), because italic ships as a separate file rather than as an axis, so
U3's discovery work is unchanged by this round.

Upstream sources to record:

- https://raw.githubusercontent.com/google/fonts/main/ofl/roboto/Roboto-Italic%5Bwdth%2Cwght%5D.ttf
- https://raw.githubusercontent.com/google/fonts/main/ofl/robotomono/RobotoMono-Italic%5Bwght%5D.ttf

## Test strategy

The shipped data grows, so the round ships **new tests for the new data** rather
than widening assertions inside the existing spec:

- `BundledItalicFontsTest` (new, `kge-font-roboto/commonTest`) owns the two new
  payloads: byte length, sfnt magic, FNV-1a 64, and the chunk count and
  remainder. `BundledFontsTest` keeps the romans untouched apart from the
  generator-visible member names, which do not change.
- The generated-accessor contract is pinned in `buildSrc`'s own suite, which
  must change anyway (the spec type changes): one member per declared payload in
  **manifest order**, per-payload provenance in the header comment, and the
  rejections below.
- No golden moves: this round changes no rendering.

## Surfaces

`buildSrc`, `EmbeddedResources.kt` — the family carries a list of payloads
instead of one font:

```kotlin
data class EmbeddedFontSpec(
    val member: String,   // the generated accessor member name
    val path: String,     // relative to resourceDir
) : Serializable

data class EmbeddedFamilySpec(
    val accessorName: String,
    val family: String,
    val version: String,
    val licenseId: String,
    val source: String,
    val fonts: List<EmbeddedFontSpec>,
    val license: String,
) : Serializable

data class EmbeddedFont(   // one resolved payload
    val member: String, val path: String,
    val bytes: Int, val sha256: String, val base64: String,
)

data class EmbeddedFontFamily(
    /* … provenance as today … */
    val fonts: List<EmbeddedFont>,   // was a single font/fontPath/fontBytes/…
)
```

`renderFamily` emits one `val <member>: List<String>` per payload, in manifest
order, and one header-comment line per payload. New fail-fast rules: an empty
`fonts` list, a blank member, and a duplicate member **within** a family are
rejected; the existing duplicate-accessor rule across families stays.

`kge-font-roboto/build.gradle.kts` — the manifest lists both payloads per
family; the member names keep the roman's existing name:

```kotlin
fonts = listOf(
    EmbeddedFontSpec(member = "variableFont", path = "roboto/Roboto[wdth,wght].ttf"),
    EmbeddedFontSpec(member = "italicFont", path = "roboto/Roboto-Italic[wdth,wght].ttf"),
),
```

The generated object therefore gains exactly one member per family:

```kotlin
object Roboto {
    const val FAMILY: String = "Roboto"
    const val VERSION: String = "3.015"
    const val LICENSE_ID: String = "OFL-1.1"
    const val SOURCE: String = "https://github.com/google/fonts/tree/main/ofl/roboto"
    val variableFont: List<String>   // the roman payload: the family's default face
    val italicFont: List<String>     // the italic payload
    val licenseText: String
}
```

The `variableFont` KDoc states the order contract the loader will rely on in U3:
the roman is the payload a family's `defaultFace` must come from.

### Resolved at dispatch (2026-10-04, orchestrator)

- **The payload pins were re-verified in `bash` before dispatch.** Both italic
  files, re-fetched from the URLs above, reproduce the table exactly — bytes,
  sha256, FNV-1a 64, chunk count and last-chunk length — and both are sfnt
  `00010000`. Both committed romans re-hash byte-identical to their upstream
  copies, so each roman/italic pair still comes from one revision.
- **The `variableFont` KDoc is generated, not hand-written.**
  `renderFamily` emits a one-line KDoc above the *first* payload member (manifest
  order) recording that a family's `defaultFace` comes from it. Manifest order
  stays significant: members are emitted in it, never sorted. It is the only
  place the order-sensitive contract reaches a consumer, and the touch-point
  requires that contract in the public KDoc.

## Files

| file | change |
|---|---|
| `kge-font-roboto/fonts/roboto/Roboto-Italic[wdth,wght].ttf` | new, committed verbatim |
| `kge-font-roboto/fonts/roboto-mono/RobotoMono-Italic[wght].ttf` | new, committed verbatim |
| `kge-font-roboto/PROVENANCE.md` | two rows, the two upstream URLs, and the note that the romans re-hash unchanged |
| `kge-font-roboto/build.gradle.kts` | the two `fonts` lists |
| `kge-font-roboto/src/commonTest/…/BundledItalicFontsTest.kt` | new |
| `buildSrc/…/EmbeddedResources.kt` | the payload list in the spec, the model and the renderer |
| `buildSrc/src/test/…/EmbeddedResourcesTest.kt` | adapted helper + the new cases |

## Steps (TDD: red → green per feature)

1. **The payloads** (data; no red): commit both files verbatim; confirm
   `shasum -a 256` against the table above before staging.
2. **PROVENANCE** (data): the two rows with bytes/sha256/version, both upstream
   URLs, and the re-verification note.
3. **The embedder** (red → green): `EmbeddedFontSpec`/`EmbeddedFont`/
   `EmbeddedFamilySpec.fonts`; the renderer emits one member per payload in
   manifest order with its own header line and its own byte size, sha256 and
   chunk sequence; the new rejections (empty payload list, blank member,
   duplicate member within a family) with the offender named. Red: the current
   suite declares a single `font` per family and does not compile against the
   new spec. `EmbeddedResourcesTest`.
4. **The module manifest** (no independent red): both families list roman then
   italic; `generateEmbeddedFonts` regenerates `EmbeddedFonts.kt` and the
   existing eight `variableFont` call sites inside the module keep compiling.
5. **The bundle identity pins** (red → green): `BundledItalicFontsTest` — for
   each new payload the byte length, the sfnt magic and the FNV-1a 64 from the
   table, plus the chunk count and the last chunk's length; and one case
   asserting the two payloads of a family decode to different bytes (a family
   with two faces, not two copies of one). Red: `italicFont` does not exist.
6. **Gate, review, decisions entry, commit**: `tools/gradle build` (it already
   runs `buildSrcCheck`); the reported counts read per the `#35`/`#38` rules,
   including `buildSrc`'s; the entry staged before the final review round; the
   marker; one squashed commit.

## Pinned expectations

The FNV-1a 64 constants are computed in `bash` from the committed files with a
64-bit-wrapping reference implementation, the procedure chunk 31 established;
the in-test helper (`fnv1a64`, already in `BundledFontsTest`) is reused. The
sha256s live in `PROVENANCE.md` and in the generated header comment; the test
does not recompute sha256 because the common stdlib has none, and the repo's
idiom for in-test payload identity is FNV-1a 64.

## Out of scope

The `variableFont` → `romanFont` rename (deferred to U3, where the family API
rewrites the call sites); every `kge-text-ttf` production and test change; the
name-table assertions that the italic payloads really report an italic
subfamily, and the axis descriptors of either payload — those are U3's, which
owns the `fvar`/`name` reader; and any new golden reference.

## Gate

`tools/gradle build` green: every target's tests (both browser suites reporting
their counts, never merely exiting 0), ktlint, the metadata/kLIB compilation and
`buildSrcCheck`. `verifyJvmLicenseJar` is unaffected — no license file changes —
and no golden moves: U2 ships data and its identity tests.
