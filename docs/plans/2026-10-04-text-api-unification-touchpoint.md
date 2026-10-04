# Text API unification — font family, face, and configured font: touch-point

**Date:** 2026-10-04. **Status:** design decided; implementation has not
started. This document records the complete touch-point for replacing the
separate bitmap and TTF drawing surfaces with one font-centered API. The
micro-plan is written just-in-time on top of this document.

## Purpose

KGE should provide a cheap built-in bitmap font by default while allowing an
application to load and use TTF/OTF fonts in the same drawing flow. An `Engine`
has one mutable principal font for ordinary calls, and a caller can select a
different configured font explicitly for any individual call. Several fonts may
therefore remain live and be used simultaneously.

This is a real consumer that the earlier unification findings did not have. It
supersedes the literal option `(a′)` from
`2026-10-01-text-api-unification-findings.md`. The useful part of `(a′)` — one
core-declared abstraction implemented by both font systems — remains, but the
chosen abstraction is not a passive handle plus a second common dispatch
service. It is the three-level model below.

This round unifies text drawing. It does not add text entry, a console, bidi,
script itemization, rotated text, or a general typography/layout system.

## Sources and standing constraints

This touch-point builds on, rather than reopens:

- olcPixelGameEngine v2.30 for the built-in bitmap drawing behavior and call
  shape;
- `main` as evidence for the earlier Kotlin bitmap-font implementation;
- the current `kge-core` bitmap surface and `kge-text-ttf` HarfBuzz + FreeType
  implementation;
- `2026-09-16-r6-text-touchpoint.md` and the subsequent R6 decision chunks;
- `2026-10-01-text-api-unification-findings.md` for the two existing surfaces,
  their ownership, and their semantic differences;
- `2026-10-04-variable-font-axes-findings.md` for the axis capability spike.

The existing R6 boundaries remain: Latin/LTR ordered runs, HarfBuzz shaping,
FreeType rasterization, a common atlas/blit layer, and no text-entry or console
surface. Bitmap and TTF implementations may preserve different internal line,
tab, blend, and glyph-spacing rules where their font models genuinely differ,
but the public operations and ownership model are shared.

## Domain model

The vocabulary is declared once, in `kge-core`, and **nested** under `KGEFont`:
the configured font is the anchor and every concept it needs is named inside it.
Both backends implement these types; the concrete families stay private to their
modules. The concepts are interfaces, except where value semantics is the point:
`Axis` is a data class, and `Axis.Tag`, `Axis.Value` and `Size` are value
classes.

Every collection in this model is exposed as the Kotlin read-only interface
(`List`, `Map`) and backed by a `kotlinx.collections.immutable` persistent
collection. The dependency stays `implementation`, so it never enters a module's
ABI: a `PersistentMap` is a `Map`. Read-only is a view and not a guarantee — the
persistent value underneath is what makes a published face or font stable, and
this round adds the dependency to `kge-text-ttf`, which does not declare it yet.

```kotlin
interface KGEFont : KGEResource {
    val face: Face
    val size: Size
    val family: Family get() = face.family

    /** Applied design coordinates; canonical, in tag order. */
    val axisCoordinates: Map<Axis.Tag, Axis.Value>

    fun measureText(text: String, tabSizeInSpaces: Int): Int2D
    fun drawText(/* … */): Unit
    fun drawTextDecal(/* … */): Unit

    /** A family's related faces; it owns the payload and native state. */
    interface Family : KGEResource {
        /** The payload's preferred family name; the built-in family names itself. */
        val name: String
        val faces: List<Face>
        val defaultFace: Face
    }

    /** A selectable design; the family owns it, callers never close it. */
    interface Face {
        val family: Family

        /** This face's name within its family, unique there. */
        val name: String

        /** Whether the face advances every glyph by one width. */
        val monospaced: Boolean

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

    /** An OpenType variation axis. */
    data class Axis(
        val tag: Tag,
        val name: String,
        val min: Value,
        val default: Value,
        val max: Value,
        val hidden: Boolean,
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
```

### `KGEFont`

`KGEFont : KGEResource` is a configured, behavior-bearing font ready to measure
and draw. Configuration includes a face, a `Size`, and — for a face that exposes
axes — a canonical coordinate map.

It directly exposes the common operations:

- `measureText`;
- CPU `drawText`;
- GPU/decal `drawTextDecal`.

This is the single public drawing seam. There is no additional common
`TextService` that receives a configured font and dispatches a second time.
That extra layer would duplicate the polymorphism already provided by
`KGEFont` and make ownership harder to see.

`family` is derived from `face.family`, so the three configuration facts are
readable from one object without a second source of truth.

Each configured font is an independent lease over its family's cached
configuration. Closing one lease does not close another lease for the same
configuration. A configured font cannot outlive a closed family.

### `KGEFont.Family`

`KGEFont.Family : KGEResource` is an owned collection of related faces. It owns
the payload, decoded assets, native faces and configuration caches from which
configured fonts are made, and exposes its `faces` and a `defaultFace`.

The family is **not** a factory. A configured font is created by the face it
configures, which is the only object that knows the design being instantiated.
Closing a family invalidates every configured font created from it and releases
the family's entries.

### `KGEFont.Face`

`KGEFont.Face` is a public interface describing a selectable face within a
family. It is not a `KGEResource`: its family owns it, and callers do not close
it independently.

A face represents a source design such as the monospaced core face, the
proportional core face, Roboto Regular, or Roboto Italic. It is not a size or a
set of variable-axis coordinates.

It exposes its `family`, its axis descriptors in `fvar` order, and the
`font(scope, size, axes)` factory that adopts a configured font into a caller
scope. A face of a font without variation axes answers with an empty descriptor
map, and a non-empty `axes` argument to its factory is rejected rather than
silently ignored.

### `KGEFont.Size`

`KGEFont.Size` is a positive-`Int` value class. `Int.fontPx` is the ergonomic
constructor. Zero and negative sizes are rejected at construction rather than
being carried into a renderer.

The size belongs to the configured-font identity. The per-call `scale` remains
a separate drawing multiplier; it does not create another font or mutate its
base size.

### `KGEFont.Axis`

`KGEFont.Axis` is a data class describing one OpenType variation axis: `tag`,
non-null `name`, `min`, `default`, `max` and `hidden`. `Face.axes` is a
tag-keyed map in `fvar` order, so `axes[tag]` is the lookup and there is no
separate `axis(tag)` operation.

The descriptor keeps its own `tag` so that a value obtained from `axes.values`
still identifies its axis.

### `KGEFont.Axis.Tag`

Contains exactly four printable ASCII characters. It provides constants for the
registered `wght`, `wdth`, `opsz`, `slnt` and `ital` tags but also accepts valid
custom foundry tags. No standard-axis convenience extension properties are
added in this round.

### `KGEFont.Axis.Value`

Stores the canonical signed OpenType/FreeType 16.16 value. It provides
`Int.axisValue`, `Float.axisValue` and `floatValue`. Float input is quantized to
the nearest representable 16.16 value and rejects NaN, infinity, and values
outside the representable range. `toString` emits the shortest decimal spelling
that round-trips to the same canonical value.

## Core bitmap family

`KGECoreFontFamily : KGEFont.Family` is the built-in font's public face, and the
concrete family stays private behind it. It is the type an application names
when it wants a specific core design, so it is the one place where widening is
bought by a consumer.

It exposes two named faces:

- `monospaced`;
- `proportional`.

Both faces share one sprite/decal payload. `defaultFace === monospaced`; the
default preserves the ordinary olc-style built-in text behavior. The old
`drawStringProp` split becomes face selection rather than a second family of
operations, and selection is by name: reaching the proportional face is one
accessor, not an identity filter.

A core face's `font(scope, size)` accepts only positive multiples of `8.fontPx`
and rejects a non-empty axis map, because the built-in font has no axes. The
fixed 8-pixel cells make the requested base size representable without inventing
resampling semantics. Per-call `scale` multiplies the configured base scale.

`KGECoreFontService : KGEOverridable` is the public construction seam: it adopts
the core family into a caller scope and returns it as `KGECoreFontFamily`, so an
application reaches either face by name. It does not become the public drawing
API; configured `KGEFont` instances do the drawing.

## TTF/OTF family

The TTF module publishes no face or configured-font subtype: both are the common
types, and its concrete family stays internal to the module. What it supplies is:

- `KGETtfFontService : KGEOverridable`;
- `TtfFontAddon : TextAddon`.

### Atomic multifile loading

`TtfFontAddon` carries the loading ergonomics over `KGETtfFontService`, and both
return the family as `KGEFont.Family`. One family may be loaded atomically from
several independent font payloads:

- bytes: `vararg ByteArray`;
- Base64: `loadBase64(..., vararg List<String>)`.

Each TTF or OTF payload contributes exactly one face. TTC and OTC collections
are rejected rather than silently selecting face index zero. If any payload is
invalid, duplicated, unsupported, or fails to initialize, the entire load
fails and every resource allocated by that attempt is released.

The first payload determines `defaultFace`. This order-sensitive contract must
be stated in the public KDoc.

Family names are resolved by exact preferred-family-name match. English naming
records are preferred, followed by a deterministic fallback when English is
absent. Duplicate subfamily names in one loaded family are rejected so that
face selection is never ambiguous.

A single family represents related faces, not an arbitrary font catalog.
Roboto and Roboto Mono remain separate families. Their roman and italic
variable payloads are to be loaded atomically within their respective
families.

The exact italic artifacts, checksums, versions, and provenance are not fixed by
this touch-point. They must be pinned and verified in the implementation
micro-plan; the existing repository currently contains only the roman variable
payloads documented by the axis findings.

## Variable-font axes

The completed JVM, JS, and WasmJS spike classifies variable-axis support as
**SUPPORT NOW**. Production still owes the tested seam, but no further
feasibility spike blocks the API.

### Axis discovery

The vocabulary is §3's; this section is what the TTF family does with it. A
face's descriptors come from a pure-Kotlin `fvar` + `name` reader over the
payload the load already holds, internal to `kge-text-ttf`: the module owns the
parsing, and neither backend is asked to enumerate natively. The JVM's
`FT_Get_MM_Var` and the web's `getAxisInfos` stay cross-checks, not the source.
Names resolve through the `name` table by the axis's name ID rather than
FreeType's literal-only fallback.

`Face.axes` is the resulting tag-keyed `Map<KGEFont.Axis.Tag, KGEFont.Axis>` in
`fvar` order, so lookup is `axes[tag]` and there is no separate `axis(tag)`
operation.

### Configuring axes

Axis coordinates live on `KGEFont`, never on the family or face. `Face.font`
accepts `Map<KGEFont.Axis.Tag, KGEFont.Axis.Value>`. It validates the tags and
ranges against the selected face, fills every omitted coordinate with that
axis's default, and canonicalizes the result in tag order. A face without axes
accepts only an empty map.

The §3 collection convention applies, and the dependency this round adds to
`kge-text-ttf` does not authorize a refactor of unrelated existing collections.

The configuration/cache key is:

- face identity;
- `KGEFont.Size`;
- the complete canonical axis map, including defaults.

The same coordinate set must be applied explicitly to both HarfBuzz and
FreeType. Their variation stores are independent in the shipped stack, so
updating only shaping or only rasterization is a correctness defect.

## Ownership, caching, and scope

A family caches native configuration state by the full key above. Each active
configuration has a thread-safe reference count.

- Creating the same key concurrently is single-flight on every target: callers
  share one successfully constructed native configuration, or observe the same
  failed construction without a leaked partial entry.
- One native clone exists per active configuration.
- Equal configured-font leases share that clone and its CPU/GPU atlas state.
- Closing a lease decrements the reference count.
- Closing the final lease immediately releases native state and CPU/GPU atlases.
- Closing the family invalidates all extant leases and releases every entry.
- The family does not strongly retain lease objects.
- Garbage collection may report a leaked lease but never performs normal
  ownership or silently substitutes for `close`.

Configuring a font is `suspend`. `Face.font` creates a configuration's native
state, and on the web that creation awaits the process-wide FreeType module
(`freeTypeModule()`, itself a `Mutex` single-flight), so the suspension propagates
from the module accessor through the face seam to the factory rather than forking a
parallel synchronous construction path.

The per-family configuration cache is a second, independent single-flight, guarded
by a `kotlinx.coroutines.sync.Mutex` in `commonMain`: look the key up under the
lock, construct only on a miss, and publish the construction's success or failure
before releasing, so a failed attempt leaves no partial entry. A `Mutex` — not a
`ConcurrentHashMap.computeIfAbsent`, which `commonMain` does not have and which
would push the whole cache across a platform boundary, and not a bare atomic, which
cannot be held across a suspending construction.

The lock is **not reentrant**, and the guarded region spans the construction,
because holding it across the miss is what makes the single flight. Nothing inside
it may re-enter the family: no public family or lease operation, no
`ResourceScope.register`, no leak-report callback, and no resource close beyond the
objects the attempt itself just allocated. Lease registration happens outside the
region, and a registration that fails decrements the count in a fresh acquisition.
A re-entrant acquisition deadlocks the caller instead of failing, which is why the
boundary is stated as what the region must not contain.

**One payload copy per family.** The family decodes the payload once and owns it;
a configuration adds only native state — one shaping font and one raster face —
and must not copy the payload again. On the web that copy is the font's size, paid
twice through the blob and the face, which the axis findings measured for the seam
as it stands, so a configuration that re-copies it is a defect. Sharing is a
measurement, not an assumption: the benchmark harness prices it, and the plan
does not assert it.

`close` is idempotent. Every operation that consumes the closed resource fails
fast: the handles that reach the payload (a family's `faces`, `defaultFace` and,
for the core family, `monospaced`/`proportional`, a face's `family` and
`font(...)`, a configured font's `face` and `family`) and the operations that
read it (`measureText`, the draws). Inert values stay
readable — a family's and a face's `name`, a face's `monospaced` and `axes`, a
configured font's `size` and `axisCoordinates` — because the guard belongs to
what the close released, and a descriptor or a size was never released.

Every family and every configured-font lease is registered under its own fresh,
private `ResourceScope.Key`. Explicit APIs that accept a `ResourceScope` are
`@KGESensitiveAPI`. This retains explicit adoption without making a globally
reused key conflate independent resources.

The existing `ResourceScope` is single-threaded; the configuration cache's
thread-safety requirement does not make scopes concurrently mutable.

## Drawing contract

### Measurement

`KGEFont.measureText` measures using the configured face, base size, and
axis coordinates. It has no draw `scale` parameter. `measureText("")` returns
`Int2D(0, 0)`.

Only `\n` is a line break. The API does not normalize `\r` or `\r\n`; a carriage
return remains ordinary unsupported/input-specific text according to the
implementation's established behavior.

### CPU drawing

The font-level CPU seam takes raw `x` and `y` coordinates. Its `scale` is an
`Int`. `scale <= 0` is a no-op.

`Pixel.Mode.Custom` is passed through and preserved. Other blend, tab, line,
and spacing details may remain implementation-specific where bitmap and shaped
text cannot honestly share one rule, but measuring and drawing with a given
font must be internally consistent.

The addon checks whether a draw target exists before touching the selected
font. With no draw target, CPU drawing is a no-op even if the font has otherwise
been closed or would reject the call.

### Decal drawing

The decal seam uses `scale: Float2D`. It preserves the existing olc-shaped
propagation of zero, negative, NaN, and infinite components rather than
silently normalizing or rejecting them at this layer.

The configured font is responsible for the implementation-specific atlas and
for emitting through the existing layer/decal machinery. The addon supplies the
window, target layer, draw modes, and collector context.

## `TextAddon`

`TextAddon` is the common engine-facing ergonomic surface. It has the roles
needed by both implementations:

- draw target;
- draw modes;
- window;
- layers;
- resource scope.

It requires:

- mutable `textFont: KGEFont` — the principal font;
- mutable positive `tabSizeInSpaces` — initialized by `Engine` to `4`.

Its operation names are:

- `measureText`;
- `drawText`;
- `drawTextDecal`.

CPU drawing has raw-coordinate and `Int2D` overloads. Each operation places
`font: KGEFont = textFont` last, so the principal font is effortless while
an alternative configured font is explicit and local to one call. This supports
several simultaneous fonts without mutating global state around each draw.

`TtfFontAddon : TextAddon` adds TTF-family loading/configuration ergonomics; it
does not create a second text-drawing vocabulary.

## Replacement and compatibility policy

This is a restructuring checkpoint, not a compatibility-preserving release.
The following old public surfaces are removed rather than deprecated:

- `DrawStringService`;
- `DrawStringAddon`;
- `TtfTextService`;
- `TtfDrawStringAddon`;
- all `*Prop` drawing and measurement operation names.

Their behavior is represented by the new model: the core proportional face
replaces `*Prop`, and the configured TTF font replaces explicit `(font,
sizePx)` parameters on every call.

The rename from `getTextSize`/`drawString*` to
`measureText`/`drawText`/`drawTextDecal` is intentional. It gives bitmap and
outline fonts one truthful vocabulary rather than preserving names tied to the
old singleton bitmap implementation.

## Rejected alternatives

### Keep the two APIs

Rejected because the consumer needs one mutable principal font, per-call
alternatives, and simultaneous bitmap/TTF use. Keeping separate addons would
make a font switch also require switching drawing vocabularies and engine
roles.

### Implement literal option `(a′)`

Rejected in its passive-handle-plus-common-service form. It was the least-bad
unification option before a consumer existed, but a configured font can own its
behavior directly. A second common service would add shallow double dispatch
without hiding additional complexity.

### Put size and axes on each draw call

Rejected because they are configuration identity, determine native state and
atlas contents, and must participate in lifecycle and caching. Repeating them
on every draw would make ownership implicit and cache correctness fragile.

### Make faces resources

Rejected because a face is owned by exactly one family and has no independent
lifetime. Independent close would permit invalid partial-family states.

### Put axes only on a TTF-specific subtype

Rejected in favour of the common type. The vocabulary is declared once, in
`kge-core`, and both implementations answer it: a bitmap face exposes an empty
descriptor map and an empty coordinate map, which is honest rather than
invented, and it buys one factory signature instead of two drawing vocabularies.
Keeping the accessor on a TTF subtype would leave the vocabulary unified but the
access to it split. This reverses the earlier ruling that the common face must
not carry axes because the bitmap font cannot honour them; the empty answer is
what makes the ruling unnecessary, and the unified signature is what it buys.

### One face per family

Rejected because roman/italic and other related faces need atomic ownership and
selection under one family. Conversely, unrelated Roboto and Roboto Mono
families are not collapsed into a catalog.

### Silently choose face zero from TTC/OTC

Rejected because it hides information and revives the indexed-face ambiguity
previously removed from the module. Collections are unsupported until they have
an explicit model.

### Preserve `*Prop` operations

Rejected because proportionality is a property of the chosen face. Parallel
method families would encode the old bitmap implementation detail into the
common API.

### Let garbage collection own leases

Rejected because GPU/native resources need deterministic release and the
project's resource contract is explicit. Leak detection is diagnostic only.

## Divergences and retained implementation differences

- olc has one private built-in bitmap font and no public font abstraction,
  TTF/OTF loading, configured sizes, or variable axes. The family/face/font
  model is an intentional KGE extension required by the real consumer.
- The common surface exposes variation axes although the built-in bitmap font
  has none: a bitmap face answers with an empty map and rejects a non-empty
  coordinate argument. This diverges from the recommendation in
  `2026-10-04-variable-font-axes-findings.md` §9 to defer the axis surface
  until a named consumer exists; the owner decided the unified vocabulary is
  itself the consumer, and enumeration stays additive for third-party fonts.
- olc exposes monospaced and proportional operations as separate names. KGE
  selects a core face instead.
- The core font has fixed 8-pixel source cells; accepting only positive
  multiples of eight is a KGE validity rule.
- TTF tab stops, line metrics, shaping, antialiasing, and alpha composition are
  intrinsically different from the bitmap walk. The shared contract requires
  internal consistency, not false pixel identity between unrelated fonts.
- The CPU `scale <= 0` no-op and decal floating-point propagation retain the
  established path-specific behavior rather than forcing one validation rule
  onto two different rendering mechanisms.

## Micro-plan test contract

The implementation micro-plan must turn the following into executable tests,
with every public parameter receiving an observable-effect assertion.

### Model and validation

1. `KGEFont.Size` accepts positive values, rejects zero/negative values, and
   `Int.fontPx` constructs the same canonical size.
2. A face belongs to its family and cannot be independently closed.
3. The core family exposes exactly the monospaced and proportional faces,
   defaults by identity to monospaced, and both use one shared payload.
4. A core face's factory accepts positive multiples of 8, rejects every other
   size, and rejects a non-empty axis map.
5. Core face selection changes measured/drawn spacing where the built-in glyph
   data differs; per-call scale multiplies the configured base scale.

### Loading and family identity

6. Byte and chunked-Base64 multifile loads preserve payload order and choose
   the first face as `defaultFace`.
7. A roman+italic load succeeds atomically; failure in any payload closes all
   allocations and publishes no partial family.
8. Exact preferred-family matching, English-name preference, deterministic
   fallback, duplicate subfamily rejection, and mismatched-family rejection are
   independently pinned.
9. A TTF/OTF payload creates one face; TTC/OTC payloads fail explicitly rather
   than selecting index zero.
10. Roboto and Roboto Mono remain distinct families. The selected italic files,
    source, version, license, size, and checksum are verified in the micro-plan.

### Axes

11. Tags enforce four printable ASCII characters; registered constants and a
    custom tag preserve exact identity.
12. Values cover exact integer conversion, nearest float quantization, signed
    bounds, rejection of NaN/infinity/out-of-range values, and shortest
    round-trip decimal output.
13. Roboto face discovery pins tag, name, min/default/max, hidden state, order,
    and tag lookup for its known axes.
14. Partial coordinates fill defaults; differently ordered input maps produce
    one sorted canonical map and the same cache key.
15. Unknown tags and out-of-range values fail before native/atlas state is
    published.
16. A non-default `wght` coordinate changes both shaped advance and raster ink
    on JVM, JS, and WasmJS. Both engines receive exactly the canonical map.
17. Raw FreeType web exports fail loudly when unavailable. Dependency upgrades
    rerun the axis spike because the raw Emscripten names and hand-written
    layouts are version-sensitive.

### Caching and lifecycle

18. Equal keys share one native clone and atlas state while returning distinct
    leases; different face, size, or canonical axes maps do not share.
19. Concurrent JVM creation of one key is single-flight on success and failure.
20. Closing one lease leaves equal live leases usable; closing the last lease
    immediately frees native and CPU/GPU atlas state.
21. Lease and family close are idempotent; after a close the handles and
    operations that consume the released state fail fast while inert values
    still answer (item 42), and closing a family invalidates all leases.
22. The family does not strongly retain lease objects; abandoned live leases
    are reported as leaks without GC-driven cleanup.
23. Every family and lease uses a fresh private scope key; scope close releases
    each exactly once. Explicit scope-taking APIs carry `@KGESensitiveAPI`.
24. All allocate-then-fail paths close resources, including parsing, native face
    creation, configuration cloning, atlas creation, and lease construction.

### Measurement and drawing

25. `measureText("")` is `(0, 0)` for every font implementation.
26. Only `\n` creates a new line; `\r` and `\r\n` are not normalized.
27. CPU raw and `Int2D` overloads render identically; each coordinate, text,
    color, scale, tab size, mode, and font parameter has a visible assertion.
28. CPU `scale <= 0` performs no draw. With no draw target, the addon returns
    before consulting even a closed font.
29. `Pixel.Mode.Custom` is preserved and receives the implementation's intended
    pixel; non-custom behavior is consistent with measurement and each font's
    documented renderer.
30. Decal position, color, both scale components, tab size, draw mode,
    structure, selected layer/collector, screen size, and selected font are
    observable. Zero, negative, NaN, and infinite scale components propagate as
    specified.
31. Measurement, CPU drawing, and decal drawing agree on line advance, tab
    behavior, and face/configuration within each implementation.

### Addon and replacement surface

32. `Engine` starts with a live core principal `textFont` and
    `tabSizeInSpaces == 4`.
33. Assigning `textFont` changes subsequent default calls; passing the final
    `font` argument selects an alternative for only that call.
34. Multiple configured fonts can be measured and drawn alternately without
    mutable global font switching.
35. `tabSizeInSpaces` rejects non-positive assignments and affects subsequent
    measure/CPU/decal calls.
36. The public API contains no old `DrawStringService`, `DrawStringAddon`,
    `TtfTextService`, `TtfDrawStringAddon`, or `*Prop` operations.
37. Public KDoc states the first-payload default-face rule and the non-obvious
    lifetime contracts in at most two lines per block.

### Vocabulary revision (2026-10-04 close review)

38. Every family and face reports a `name`, and a face's name is unique within
    its family.
39. A face reports whether it is `monospaced`; the core's monospaced face
    answers true and its proportional face false.
40. `KGECoreFontFamily` exposes both core faces by name: `monospaced` is
    `defaultFace`, and `proportional` is the other member of `faces`.
41. The built-in family reports `KGE bitmap font`, and its faces report
    `Monospaced` and `Proportional`, in that order.
42. Inert values answer after the close: a family's and a face's `name`, a
    face's `monospaced` and `axes`, and a configured font's `size` and
    `axisCoordinates`. The handles and operations keep failing fast: `faces`,
    `defaultFace`, a core family's `monospaced` and `proportional`, `family`,
    `font(...)`, a configured font's `face` and `family`, and measuring. Reading
    a value consumes nothing; the guard belongs to what the close released.
43. `KGECoreFontService.createResources` returns `KGECoreFontFamily`, and the
    service stays overridable: a foreign implementation of that interface
    answers the seam without the private concrete family.

Tests must exercise common behavior through public APIs. Platform-private
implementations remain private; `expect`/`actual` is limited to one narrow
internal entry point, with concrete implementations file-private behind it.

## Definition of done

The unification concept is complete only when:

- the model and all operations above exist in `kge-core`, with TTF
  specializations in `kge-text-ttf`;
- the built-in core font is the Engine default and TTF fonts can replace it or
  be selected per call without another drawing API;
- multifile families, axes, caching, resource ownership, and failure cleanup
  satisfy the test contract on JVM, JS, and WasmJS;
- Roboto and Roboto Mono each include verified roman+italic variable fixtures
  loaded atomically, with their bundle metadata retained in `kge-font-roboto`
  rather than attached to `KGEFont.Family` — the family's own `name` is intrinsic
  to the payload and is part of the vocabulary;
- the old public drawing surfaces are removed, not deprecated;
- the full repository gate passes with expected test counts;
- independent Standards and Spec reviews pass under the repository round flow;
- the decisions entry records implemented behavior and all accepted
  divergences before the reviewed tree is committed.

No production or test code is changed by this touch-point itself.

## Revision 2026-10-04 — the close review of U1

The owner's review of the committed U1 found the vocabulary incomplete in three
ways, each confirmed against this document:

- **No name.** `KGEFont.Family` and `KGEFont.Face` carried none, although the
  model above describes faces as named designs (Roboto Regular, Roboto Italic)
  and U3 resolves a load by exact preferred-family-name match while rejecting
  duplicate subfamily names.
- **No proportionality.** Rejecting the `*Prop` family of operations is
  justified here by "proportionality is a property of the chosen face", yet no
  member of `Face` answered it; telling the two core faces apart meant comparing
  identity.
- **No named route to the core faces.** This document states that the core
  family exposes two faces and that the service returns it "so an application
  can still reach the `proportional` face", but the API offered only `faces` and
  `defaultFace`, so reaching it meant filtering by identity.

Decisions (owner, 2026-10-04):

- **`Family.name` and `Face.name`**, the latter unique within its family. A
  TTF family takes the preferred English name from the payload's `name` table
  and a face its subfamily name. The built-in family reuses its sheet's own name
  string, so the built-in font has one name, and its faces are `Monospaced` and
  `Proportional`.
- **`Face.monospaced`**, declared by the face rather than derived. A TTF face
  takes it from the payload's own metric (`post.isFixedPitch`), which is U3's to
  populate.
- **`KGECoreFontFamily` becomes a public interface** extending `KGEFont.Family`
  with `monospaced` and `proportional`, the concrete family staying private
  behind it. The override seam still returns an interface, and the named
  consumer of the widening is the application that selects a core design — the
  same consumer this document already promised the route to.

Rejected: a general `Family.face(name)` lookup, which is stringly-typed and
answers a different question than the agreement named; and leaving the concrete
family internal, which would leave the promised route non-existent.

**The open check guards consumption, not metadata.** Reading a name, a spacing,
descriptor axes, a size or axis coordinates consumes nothing, so those values
stay readable after a close, while the handles that reach the payload and the
operations that read it keep failing fast. This relaxes three accessors U1 had
pinned as failing fast (`KGEFont.size`, `KGEFont.axisCoordinates`,
`Face.axes`); their pins move with the rule, and the decisions entry records the
supersession. U3 follows the same rule for TTF faces, whose descriptors are
parsed Kotlin values over the retained payload.

This is an addendum round before U2, not part of U3. U2 generates the bundled
fixtures from a manifest and U3 owns loading and naming, so shipping the
vocabulary now lets U2 fill the new members and U3 populate them from the `name`
table, instead of reopening generated fixtures or a decided API later.
