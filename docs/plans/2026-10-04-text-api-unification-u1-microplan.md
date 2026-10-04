# Text API unification — micro-plan, round U1 (core vocabulary + bitmap family + `TextAddon`)

**Date:** 2026-10-04. Round **U1** of the unification work. Context: the
touch-point (`docs/plans/2026-10-04-text-api-unification-touchpoint.md` — the
design is settled there; this plan only turns it into TDD steps), the
unification findings (`2026-10-01-text-api-unification-findings.md` §2 for the
two surfaces side by side and §2.4 for what `main` contributed), and decisions
chunks `29`, `38`.

Rounds: **U1 core vocabulary + bitmap backend** → U2 bundled italic fixtures →
U3 TTF family/loading/naming/discovery/axes + measure → U4 TTF CPU draw → U5
TTF decal draw → U6 caching/ownership hardening.

## Scope

`kge-core` only, plus the `kge-benchmark` migration that removing the core
surface forces. No new dependency (`kotlinx.collections.immutable` is already a
`kge-core` `implementation`), no build change, **no `kge-text-ttf` production
change**.

Public additions: `KGEFont` and its nested `Family`/`Face`/`Axis`/`Axis.Tag`/
`Axis.Value`/`Size`, `Int.fontPx`, `Int.axisValue`/`Float.axisValue`,
`KGECoreFontService`, `TextAddon`; `Engine`
gains `TextAddon`. Public removals: `DrawStringService`, `DrawStringAddon` and
every `*Prop` operation name.

Removal is **staged across the checkpoint**. U1 removes the core surfaces;
`TtfTextService`/`TtfDrawStringAddon` stay until U3–U5 replace them, so the
benchmark's TTF legs keep compiling. Touch-point item 36 ("no old name in the
public API") is therefore a **close** criterion, not a U1 one, and the Spec
review is told so in advance.

## Test and golden strategy

The old core text specs are **replaced, not ported**: their names name removed
types, and the behavior they pinned is re-established by new specs written
against the new surface, with new golden references. Two rules govern that:

1. **Coverage parity is a deliverable.** Every behavior the deleted suite pinned
   is pinned again by a named new spec — the map below is what the Spec review
   checks. Coverage the new model adds is pinned too, and is listed separately.
2. **References are authored, never recorded.** A new golden is produced by
   copying or mechanically deriving an already-committed reference — never by
   running the new implementation and saving its output. `generateGoldenImages`
   re-encodes the committed PNGs into the codegen; `goldenActualToPng` exists to
   inspect a failure, not to author an expectation. This is the E2 rule
   (chunk 39) applied to a re-homing round.

### Old coverage → new spec

| deleted spec | what it pinned | new spec |
|---|---|---|
| `DrawStringMetricsTest` | mono/prop sizes; newline reset; tab advance; non-positive tab rejected | `CoreFontMetricsTest` — all eight cases, now per face and per base size |
| `DrawStringServiceTest` | the 128x48 sheet exists; the painted sheet golden | the `payload-mono-8` golden in `CoreFontGoldenTest` (the sheet's pixels through the public face), `CoreFontFamilyTest` for the one shared payload, and `CoreFontDecalTest` for the decal's 128x48 sprite |
| `DrawStringTest` | four draw goldens; typed vs raw; newline/tab advance; olc mode resolution; translucent blend; opaque painting; non-positive scale | `CoreFontDrawTest`, `CoreFontGoldenTest`, and the overload cases in `TextAddonTest` |
| `DrawStringResourcesTest` | the scope owns and releases the font; double close releases once; a draw without the font fails fast | `CoreFontFamilyTest` — lease/family close idempotence, fail-fast after either close, one `deleteTexture` at scope close |
| `DrawStringDecalTest` | seven decal cases: geometry, source cell and step, prop spacing, newline/tab, mode/structure/tint, empty text, tab size not validated, missing font fails fast | `CoreFontDecalTest` |
| `DrawStringAddonTest` | nine addon cases: CPU draws, typed/raw forwarding, null target, tab forwarding and override, mono/prop decal cells, decal tab override | `TextAddonTest`, with the tab forwarding and override rows in `EngineTextFontTest`; plus the two-font and closed-font cases below |
| `EngineTextResourcesTest` | the scope resolves the font during the run; it is released at teardown; the scope fails fast outside a run | `EngineTextFontTest`, which must also carry the scope fail-fast case **before** this spec is deleted — nothing else pins it (checked) |

### Coverage the new model adds

- `Size`: positive-only, `Int.fontPx`, `Comparable` (item 1).
- `Axis.Tag`: four printable ASCII, the five constants, custom tags (item 11).
- `Axis.Value`: exact `Int` conversion, nearest `Float` quantization, signed
  bounds, NaN/infinities, `Comparable`, shortest round-trip `toString`
  (item 12).
- The base-scale rule: `8.fontPx` + `scale 2` and `16.fontPx` + `scale 1` paint
  and measure identically (item 5).
- Face selection replaces the `*Prop` split: one family, two faces, one payload
  (item 3).
- Two configured fonts drawn alternately through one addon, with no state
  switched between calls (item 34).
- `Engine.textFont` is live during a run and `tabSizeInSpaces` starts at 4 and
  rejects non-positive assignment (items 32, 35).
- A `null` draw target no-ops **even with a closed font** (item 28).

## New golden references

Written under `kge-core/src/commonTest/golden/text/`; the five committed
references are deleted at the end of the round.

| new reference | case | authored how |
|---|---|---|
| `mono-8` | mono face, `8.fontPx`, `scale 1`, `"Hi"`, 16x8 | copy of the committed `mono.png` |
| `mono-8-scale-2` | mono face, `8.fontPx`, `scale 2`, 32x16 | copy of `mono-scale-2.png` |
| `prop-8` | proportional face, `8.fontPx`, `scale 1`, 11x8 | copy of `prop.png` |
| `prop-8-scale-2` | proportional face, `8.fontPx`, `scale 2`, 22x16 | copy of `prop-scale-2.png` |
| `mono-16` | mono face, `16.fontPx`, `scale 1`, 32x16 | copy of `mono-scale-2.png` — the base-scale rule written out |
| `prop-16` | proportional face, `16.fontPx`, `scale 1`, 22x16 | copy of `prop-scale-2.png` |
| `mono-16-scale-2` | mono face, `16.fontPx`, `scale 2`, 64x32 | 4x nearest replication of `mono.png`, produced by a throwaway script — the only reference that pins the product of the two scales |
| `payload-mono-8` | the whole 96-cell payload — characters 32..127 — drawn through the public mono face as six 16-character rows, 128x48 | copy of the committed `sheet.png` |

`payload-mono-8` is the interesting one: it converts an internal-payload golden
into a public-API one. The sheet is a grid of 8x8 cells — 16 per row, six rows,
96 cells, characters 32..127 in `(char - 32)` order — and the mono walk places
character *i* at column `i % 16` and row `i / 16`; drawing characters 32..127
with `\n` inserted every sixteen therefore reproduces the committed `sheet.png`
exactly at `8.fontPx`. The range is 32..**127**, not 32..126: 95 characters are
five full rows plus fifteen cells, so the last cell of the sheet would never be
painted, and the last cell does carry ink (23 pixels). `DrawString` is
monospaced and ignores `vFontSpacing` — it walks the full 8x8 cell for every
character — so painting character 127 this way is olc's own behavior, not a
divergence; only `DrawStringProp` consults the table, whose final entry is
`0x00`. Deleting `text/sheet.png` without losing its coverage is the point of
the case.

## Surfaces

`kge-core`, `dev.staticsanches.kge.text` — the vocabulary in a new
`KGEFont.kt`, restated here only where this round's steps depend on it (the
touch-point's domain model is authoritative):

```kotlin
interface KGEFont : KGEResource {
    val face: Face
    val size: Size
    val family: Family get() = face.family

    /** Applied design coordinates; canonical, in tag order. */
    val axisCoordinates: Map<Axis.Tag, Axis.Value>

    /** The pixel box of [text]; no draw `scale` takes part. `""` is `(0, 0)`. */
    fun measureText(text: String, tabSizeInSpaces: Int): Int2D

    /** Draws [text] from the raw ([x], [y]) corner; `scale <= 0` is a no-op. */
    fun drawText(
        target: Pixmap.Mutable, x: Int, y: Int, text: String,
        color: Pixel, scale: Int, tabSizeInSpaces: Int, mode: Pixel.Mode,
    )

    /** Queues [text]'s cells from [position]; decal sign/zero rules apply. */
    fun drawTextDecal(
        position: Float2D, text: String, color: Pixel, scale: Float2D,
        tabSizeInSpaces: Int, screenSize: Int2D, decalMode: Decal.Mode,
        decalStructure: Decal.Structure, decalInstanceCollector: (DecalInstance) -> Unit,
    )

    /** A family's related faces; it owns the payload and native state. */
    interface Family : KGEResource {
        val faces: List<Face>
        val defaultFace: Face
    }

    /** A selectable design; the family owns it, callers never close it. */
    interface Face {
        val family: Family

        /** Descriptors by tag, in `fvar` order; empty for the bitmap faces. */
        val axes: Map<Axis.Tag, Axis>

        /** Creates a configured font and adopts it into [scope]; a native face suspends during setup. */
        @KGESensitiveAPI
        suspend fun font(
            scope: ResourceScope,
            size: Size,
            axes: Map<Axis.Tag, Axis.Value> = emptyMap(),
        ): KGEFont
    }

    data class Axis(
        val tag: Tag, val name: String,
        val min: Value, val default: Value, val max: Value, val hidden: Boolean,
    ) {
        /** Exactly four printable ASCII characters. */
        @JvmInline
        value class Tag private constructor(val raw: String) {
            companion object {
                val Weight: Tag; val Width: Tag; val OpticalSize: Tag
                val Slant: Tag; val Italic: Tag
                fun ofOrNull(raw: String): Tag?
            }
        }

        /** A canonical 16.16 design coordinate. */
        @JvmInline
        value class Value private constructor(val raw: Int) : Comparable<Value> {
            val floatValue: Float
            companion object {
                fun of(raw: Int): Value
                fun ofOrNull(value: Float): Value?
            }
        }
    }

    /** The configured font's base size in whole pixels; strictly positive. */
    @JvmInline
    value class Size private constructor(val px: Int) : Comparable<Size> {
        companion object { fun ofOrNull(px: Int): Size? }
    }
}

val Int.fontPx: KGEFont.Size
val Int.axisValue: KGEFont.Axis.Value
val Float.axisValue: KGEFont.Axis.Value
```

- `Tag` gets **only** `ofOrNull` plus the five constants, exactly as the
  touch-point declares: the core never parses a tag and R3's `fvar` reader is
  module-internal, so a throwing `Tag.of` would have no consumer. Recorded here
  because a reviewer will ask for the symmetry with `Value.of`.
- `Int.axisValue` and `Float.axisValue` are both the throwing form of
  `Value.ofOrNull(...)`, so both name the same design coordinate; the raw 16.16
  integer is only `Value.of(raw)`. Both exist because the touch-point names them.

`kge-core`, `dev.staticsanches.kge.engine.addon` — `TextAddon`, replacing
`DrawStringAddon`:

```kotlin
@OptIn(KGESensitiveAPI::class)
interface TextAddon :
    HasDrawTarget,
    HasDrawModes,
    HasWindow,
    HasLayers,
    HasResourceScope {
    /** The principal font of subsequent calls; a call may name another one. */
    var textFont: KGEFont

    /** The spaces a tab stop spans; positive, olc's default is 4. */
    var tabSizeInSpaces: Int

    fun measureText(text: String, font: KGEFont = textFont): Int2D =
        font.measureText(text, tabSizeInSpaces)

    fun drawText(
        position: Int2D,
        text: String,
        color: Pixel = Colors.WHITE,
        scale: Int = 1,
        font: KGEFont = textFont,
    ) = drawText(position.x, position.y, text, color, scale, font)

    fun drawText(
        x: Int,
        y: Int,
        text: String,
        color: Pixel = Colors.WHITE,
        scale: Int = 1,
        font: KGEFont = textFont,
    ) {
        val target = drawTarget ?: return
        font.drawText(target, x, y, text, color, scale, tabSizeInSpaces, pixelMode)
    }

    fun drawTextDecal(
        position: Float2D,
        text: String,
        color: Pixel = Colors.WHITE,
        scale: Float2D = Float2D(1f, 1f),
        font: KGEFont = textFont,
    ) {
        font.drawTextDecal(
            position, text, color, scale, tabSizeInSpaces,
            window.screenSize, decalMode, decalStructure,
            layers.target.decalInstances::add,
        )
    }
}
```

- The `drawTarget ?: return` sits **before** any use of `font`, so a host with
  no draw target never touches even a closed font (item 28).
- `HasResourceScope` has no reader in this round. It is included because the
  touch-point's role list settles it and its consumer — `TtfFontAddon`'s
  loading ergonomics, adopting a loaded family into the host's scope — lands in
  U3. This is not the E1 case (chunk 38), where `TtfDrawStringAddon` had no
  scope consumer and correctly did without the role.
- `textFont`/`tabSizeInSpaces` are abstract `var`s because a Kotlin interface
  cannot back one; the positivity of `tabSizeInSpaces` (item 35) is enforced by
  the host's setter, and `Engine` is the host the test drives. Assignments are
  **not** thread-guarded: `drawTarget` guards, but `pixelMode`/`decalMode` set
  the opposite precedent and the touch-point asks for nothing here.

`kge-core`, `KGECoreFontService` — the public construction seam:

```kotlin
interface KGECoreFontService : KGEOverridable {
    /** Builds the built-in family into [scope], which owns and closes it. */
    @KGESensitiveAPI
    fun createResources(scope: ResourceScope): KGEFont.Family

    companion object :
        KGEOverridable.Proxy<KGECoreFontService>(KGECoreFontService::class, Default),
        KGECoreFontService { /* one forwarder per member */ }
}
```

## Files

| file | change |
|---|---|
| `kge-core/…/text/KGEFont.kt` | new: the vocabulary + `Int.fontPx`/`Int.axisValue`/`Float.axisValue` |
| `kge-core/…/text/KGECoreFontService.kt` | new: the seam, the family, the two faces, the configured font; the sheet painter and `vFontSpacing` move here from `DrawStringService.kt` |
| `kge-core/…/text/DrawStringService.kt` | **deleted** |
| `kge-core/…/engine/addon/TextAddon.kt` | new |
| `kge-core/…/engine/addon/DrawStringAddon.kt` | **deleted** |
| `kge-core/…/engine/Engine.kt` | `: TextAddon`, the principal font, `tabSizeInSpaces = 4` |
| `kge-core/src/commonTest/…/text/CoreFontModelTest.kt` | new |
| `kge-core/src/commonTest/…/text/CoreFontFamilyTest.kt` | new |
| `kge-core/src/commonTest/…/text/CoreFontMetricsTest.kt` | new |
| `kge-core/src/commonTest/…/text/CoreFontDrawTest.kt` | new |
| `kge-core/src/commonTest/…/text/CoreFontDecalTest.kt` | new |
| `kge-core/src/commonTest/…/text/golden/CoreFontGoldenTest.kt` | new: the eight-reference matrix |
| `kge-core/src/commonTest/…/engine/addon/TextAddonTest.kt` | new |
| `kge-core/src/commonTest/…/engine/EngineTextFontTest.kt` | new |
| `kge-core/src/commonTest/…/text/DrawString{Test,MetricsTest,ResourcesTest,ServiceTest,DecalTest}.kt`, `…/engine/addon/DrawStringAddonTest.kt`, `…/engine/EngineTextResourcesTest.kt` | **deleted** |
| `kge-core/src/commonTest/golden/text/*.png` | seven copies added, one derived reference (`mono-16-scale-2`), five old files deleted |
| `kge-benchmark/…/Scene.kt`, `FpsBenchmarkEngine.kt`, `MergedDrawStringService.kt`, their tests | migrated |

## The core family

`KGECoreFontFamily` (`private`), reached only through `KGECoreFontService`:

- two faces, `monospaced` and `proportional`, both sharing **one** sheet and
  **one** decal, which the family owns; `defaultFace === monospaced`;
- `axes = emptyMap()` on both, and `Face.font` with a non-empty map is
  rejected rather than ignored;
- `Face.font(scope, size)` requires `size.px > 0 && size.px % 8 == 0`, because
  the built-in cell is a fixed 8 px and the requested size is otherwise not
  representable without inventing resampling semantics;
- the family and every lease are registered under their own **fresh, private**
  `ResourceScope.Key`, so nothing reuses an identity key;
- `close` is idempotent on both; every operation fails fast after the lease or
  the family is closed; the family does not retain lease objects.

**Size → base scale.** A configured core font's base scale is `size.px / 8`; the
per-call `scale` multiplies it, so `effectiveScale = size.px / 8 * scale` and
`8.fontPx` with `scale = 1` is exactly today's behavior. `measureText` scales by
the base only (no `scale` parameter, olc/`C7` parity, chunk 38 decision 6), so
at `8.fontPx` `mono.measureText("AB", 4)` is `Int2D(16, 8)` and at `16.fontPx`
it is `Int2D(32, 16)`.

## The benchmark

`Scene.kt`'s `TextSceneTarget : TextAddon` (it no longer implements
`DrawStringAddon`), and `renderTextScene` calls `drawTextDecal(...)` with no
`font` argument, so the merged path can intercept it.

`MergedDrawStringService : DrawStringService` becomes
`MergedTextAddon(inner: TextSceneTarget) : TextSceneTarget` — a **host**
decorator, not a resource: it forwards every role (`textFont`,
`tabSizeInSpaces`, `resourceScope`, `window`, `layers`, `drawTarget`,
`pixelMode`, `decalMode`, `decalStructure`, `suspendTextureTransfer`,
`setDrawTarget`) to `inner` and overrides `drawTextDecal` to emit one
triangle-list instance per run. The decal is still learned by probing `inner`
with a single space through a `STRIP` collector, because the font's decal is not
publicly reachable; `mergedRunVertices` and `MergedRunGeometryTest` are
untouched.

Rationale for decorating the addon rather than the font: the merged lever was a
`KGEOverridable` override of a *service*; the new seam is the font object, and a
`KGEFont` decorator would be a non-owning `KGEResource` — the ownership model
the touch-point rejects for `Face`. Decorating the host keeps the lever, needs
no core widening and no new resource semantics.

## Steps (TDD: red → green per feature)

1. **`Size`** (red → green): `Size.ofOrNull` accepts positive values and rejects
   zero/negative; `8.fontPx.px == 8`; `(0).fontPx` and `(-1).fontPx` throw;
   `Comparable` orders by `px` (item 1). `CoreFontModelTest`.
2. **`Axis.Tag`** (red → green): the five constants carry their registered
   tags; `ofOrNull` accepts exactly four printable ASCII (`"ABCD"` preserved
   verbatim, including case) and rejects `"wg"`, `"wght "`, `"wg\th"`, `"wgät"`
   and a control character (item 11). `CoreFontModelTest`.
3. **`Axis.Value`** (red → green): `Value.of(65536).floatValue == 1f` and
   `Value.of(-65536).floatValue == -1f`; `Value.of(raw)` is the only raw entry
   point; `1.axisValue == 1f.axisValue == Value.of(65536)` (both extensions are
   design coordinates — finding 2); nearest quantization
   (`0.5f.axisValue == Value.of(32768)`, `0.00001f.axisValue == Value.of(1)`);
   signed bounds (`32768f`, `-32769f` and `32768.axisValue` rejected, `32767.99f`
   accepted); NaN and both infinities are `null`; `Comparable` orders by `raw`;
   `toString` round-trips
   (`Value.ofOrNull(Value.of(raw).toString().toFloat()) == Value.of(raw)` over a
   raw table including `1`, `32768`, `65536`, `-65536`, `0`, `Int.MIN_VALUE`,
   `-98304`, `-32768`, `-3`, `-1`) plus the pinned trivial spellings `"0"`,
   `"1"`, `"0.5"`, `"-1"` (item 12). The shortest-spelling rule is the property;
   only those four are pinned as strings, and `Int.MAX_VALUE` is never pinned as
   one (finding 3). `CoreFontModelTest`.
4. **The core family and the configured font** (red → green):
   `KGECoreFontService.createResources(scope)` returns a family with exactly two
   faces, `defaultFace === monospaced` and both `axes` empty; one
   `createTexture` for the family and one `deleteTexture` at scope close; a
   non-empty axis map rejected; every non-multiple-of-8 size rejected; close
   idempotence and fail-fast after lease and family close; and **two families
   and two leases coexisting in one scope**, which is the observable form of the
   fresh-private-key rule — a shared identity key fails the second registration
   (items 3, 4, 21, 23). `CoreFontFamilyTest`. "The family does not retain lease
   objects" has no observable surface on the core side and is recorded in the
   decisions entry rather than claimed as pinned.
5. **Measurement** (red → green): the eight `DrawStringMetricsTest` cases per
   face; `measureText("") == Int2D(0, 0)` (item 25); the base size multiplies
   width and height and the per-call `scale` does not exist on this seam
   (item 5). Item 26 is pinned concretely on the **mono** face, where the walk is
   defined for every character: a carriage return is an ordinary cell, so
   `"A\rB"` measures `Int2D(24, 8)` — the same as `"A B"` — and `"A\r\nB"`
   measures `Int2D(16, 16)`, so neither is the `\n`-normalized result. The
   proportional face indexes its 96-entry table by `char - 32`, so a carriage
   return falls below it and fails; that is the pre-existing `C7` behaviour the
   touch-point keeps as "unsupported/input-specific text", recorded in the
   decisions entry rather than pinned as desired. `CoreFontMetricsTest`, plus one
   drawing case in `CoreFontDrawTest`: drawing `"A\rB"` paints exactly what
   `"A B"` paints, because the out-of-bounds sheet read is transparent and the
   space cell carries no ink.
6. **CPU drawing** (red → green): the four old draw cases, the typed/raw
   equivalence, the newline/tab advances at two base sizes, the olc mode
   resolution (opaque to `Mask`, translucent to `Alpha`, `Custom` kept and
   receiving the same object), the translucent composite over a pre-filled
   target, the opaque painting, and `scale <= 0` as a no-op that does not
   validate the tab size (items 27, 28, 29). `CoreFontDrawTest`.
7. **The golden matrix** (red → green): the eight references above through the
   public API, including the two equivalences the copies encode (`16.fontPx` at
   `scale 1` equals `8.fontPx` at `scale 2`; `payload-mono-8` equals the old
   sheet) and `mono-16-scale-2` for the composed scale.
   `CoreFontGoldenTest`.
8. **Decal drawing** (red → green): the seven old decal cases — geometry,
   source cell and step, prop spacing, newline/tab, mode/structure/tint, empty
   text, non-positive tab size not validated on this path, and a missing font
   failing fast (item 30). `CoreFontDecalTest`.
9. **`TextAddon` and its host** (red → green): a host over a `LayerStack` —
   `measureText` uses the host's `tabSizeInSpaces`; typed and raw `drawText`
   agree; `drawTextDecal` queues on the target layer with `window.screenSize`,
   the host's `decalMode`/`decalStructure` and the selected font; a `null`
   `drawTarget` no-ops **with a closed font**; naming a second configured font
   changes only that call while `textFont` still answers the default; two
   different fonts are drawn alternately with no state switched between calls
   (items 27, 28, 30, 33, 34). `TextAddonTest`.
10. **The engine** (red → green): inside a run `textFont` is live and
    `tabSizeInSpaces == 4`; assigning `textFont` changes subsequent default
    calls; `tabSizeInSpaces` rejects `0` and `-1` and its value reaches
    measure/CPU/decal; `textFont` read before `start()` and after the loop fails
    fast; the principal font is released with the scope (items 32, 35).
    `EngineTextFontTest`.
11. **Removal** (refactor, no red of its own): delete the seven old specs, the
    old five goldens, `DrawStringService.kt` and `DrawStringAddon.kt`. Nothing
    else may change: the eight new references and every new spec must already be
    green (items 31 core side, 36 core side).
12. **The benchmark** (refactor + one measurement check): the merged cell still
    queues one `LIST` instance per run and `MergedRunGeometryTest` is unchanged;
    `TextSceneTest`'s recording host implements `TextAddon`.
13. **Gate, review, decisions entry, commit**: `tools/gradle build` (it already
    runs `buildSrcCheck`); the eight references byte-identical to their authored
    inputs; the reported counts read per the `#35`/`#38` rules; the entry staged
    before the final review round; the marker; one squashed commit.

## Findings recorded during the round

Recorded as they were found. Items 1–3 are settled or routed; item 4 corrects the
golden matrix above and was caught before the case landed.

1. **`Axis.Value.toString` used `Long` — settled: removed.** The first
   implementation derived the exact decimal digits through `Long` arithmetic —
   51 emulated `Long` operations per call on Kotlin/JS. The `Int`-only
   derivation splits the two's-complement halves, `high = raw shr 16` and
   `low = raw and 0xFFFF`, and then recovers the **magnitude** parts with the
   borrow a negative value needs: `whole = -high - (low == 0 ? 0 : 1)` and
   `remainder = (65536 - low) and 0xFFFF`. Feeding the halves straight into
   `numeral` would not be equivalent — it renders `integer + 0.digits`, so raw
   `-1` would spell `-1.99998…` instead of `-0.00002` — which is why the borrow
   belongs to the derivation. `-raw` is never formed (`-high` alone is safe,
   `|high| <= 32768`), the digit loop's remainder stays below 65536, so
   `remainder * 10 <= 655350` and the constant division becomes a shift and a
   mask. `numeral` stays a local function inside `toString` with an unchanged
   body, and the companion does not change. The owner directed the change; the
   round-trip table gains `Int.MIN_VALUE`, `-98304`, `-32768`, `-3` and `-1`,
   because four pinned spellings cannot distinguish the two derivations.
2. **`Int.axisValue` and `Float.axisValue` were in different units — settled:
   both are design coordinates.** `1.axisValue` must equal `1f.axisValue`. The
   raw 16.16 integer is reached **only** through `Value.of(raw: Int)`, so a
   whole-number coordinate cannot be a 65536x slip. Both extensions throw
   `IllegalArgumentException` for a value outside the signed 16.16 range.
3. **The shortest spelling is not target-independent — routed to the decisions
   entry.** Kotlin/JS `String.toFloat()` is `toDouble().unsafeCast<Float>()` and
   does **not** demote to binary32, while the JVM and WasmJS do, so the
   acceptance test in `toString` accepts a different candidate set there:
   `Value.of(Int.MAX_VALUE)` spells `32767.9999847412109375` on JVM/WasmJS and
   `32767.99998` on `js`. The touch-point's property — the shortest spelling
   that round-trips — holds per target, and no consumer observes the difference
   today; the tests must not pin `Int.MAX_VALUE` as a string.

The formatter's `numeral` helper stays a **local function inside `toString`** —
not a member — which is the strongest form of "only `toString` uses it", and the
`Int`-only rewrite keeps it there.

4. **The payload case was mis-specified in this plan — corrected before the
   implementation landed.** The plan first read `payload-mono-8` as characters
   32..126. That cannot reproduce `sheet.png`: 95 characters are five full rows
   plus fifteen cells, so the sheet's last cell — (column 15, row 5), character
   127 — is never painted, and it carries 23 ink pixels. The case draws 32..127,
   six full rows, and the reference stays a plain copy. The range is not a
   `DrawString` divergence: `DrawString` is monospaced and walks the full 8x8
   cell without consulting `vFontSpacing` (olc `:4111`), so character 127 paints
   and advances like any other cell; only `DrawStringProp` reads the table,
   whose final entry `0x00` is what makes the character look unreachable.

5. **This plan's description of the `Int`-only `toString` was wrong when it was
   written — corrected in item 1.** It read as if the two's-complement halves
   could feed `numeral` directly; `numeral` renders a magnitude, so a negative
   non-integer needs the borrow, and a literal implementation would have failed
   the very round-trip rows the item adds. The implementer caught the gap before
   writing the code and implemented the equivalent form, so no code was
   reworked — only this record.

6. **Contract item 26 was left unpinned by the first pass — caught in the
   coverage audit.** `CoreFontMetricsTest` arrived with the eight legacy cases
   plus the empty string and the base size, and nothing anywhere asserted that
   only `\n` breaks a line. Step 5 above now states the mono pin concretely and a
   drawing case joins it; the proportional face's out-of-table failure stays a
   recorded divergence, not a pinned expectation.

7. **The `tabSizeInSpaces` declaration clash forced one edit outside this
   round's file list.** `Engine` now carries `TextAddon`'s mutable
   `tabSizeInSpaces` while `TtfDrawStringAddon` declares the same name as a
   `val`, so any class that extends `Engine` **and** implements the TTF addon
   must resolve the ambiguity explicitly with
   `override var tabSizeInSpaces ... super<Engine>.tabSizeInSpaces`. The
   benchmark's `FpsBenchmarkEngine` needed it, and so did `TtfCarrierUploadTest`'s
   private `CarrierProbeEngine` — seven lines in a file the slice was told to
   leave alone. The edit is compile-forced and behaviour-preserving, and it
   disappears when U3–U5 replace `TtfDrawStringAddon` with
   `TtfFontAddon : TextAddon`.

## Out of scope (round U1)

Anything TTF: `TtfTextService`/`TtfDrawStringAddon` and their tests, payload
families, `fvar`/`name` discovery, axis coordinates other than the empty map,
native cloning and the configuration cache, and the leak-report contract for
leases (it has no native payload on the core side; items 18–24 are pinned on the
TTF side in U6, except the core-side sharing, idempotent close, fail-fast and
fresh-key rules, which steps 4 and 9 pin). Also out: `TtfCarrierUploadTest`, the
upload-policy lever, and any `TextAddon` decal batching.

## Gate

`tools/gradle build` green: every target's tests (both browser suites reporting
their counts, never merely exiting 0), ktlint, the metadata/kLIB compilation and
`buildSrcCheck`. The authored references are the contract: if a new golden
disagrees with the reference it was copied from, the implementation is wrong,
not the reference.

**Known trap.** Without `--rerun-tasks` the web suites can fail six *old* specs
through a stale webpack bundle: three `DrawStringAddonTest` decal cases,
`DrawStringDecalTest`'s mono geometry, `KGEOverridableExtensionTest`'s "module
teardown clears an override" and `WebDecalSmokeTest`'s solid decal, each with a
leaked `DrawPartialDecalService`/`TranslatorService` override. The slice-B
dispatch reproduced the identical six with its own specs moved out of the tree,
so it is an artifact of incremental web builds rather than a regression; re-run
the web targets with `--rerun-tasks`, the chunk 35 remedy, before treating it as
one.
