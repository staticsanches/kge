# Unifying the text-drawing service API — findings

**Date:** 2026-10-01. **Status:** analysis only; this document **decides nothing
and changes nothing**. It is material for a future touch-point. No source,
build, test, plan, decisions entry, `AGENTS.md` or roadmap file was modified to
produce it.

Question: could the text-drawing **service API** be unified so that `kge-core`
keeps the olc bitmap font (`C7`) and the opt-in `kge-text-ttf` module provides a
real TTF/OTF implementation **against an API defined in the core**? The
2026-09-16 `R6` revision recorded "a compatible API across the core bitmap text
and the module" as a follow-up analysis, not a commitment
(`docs/plans/2026-09-16-r6-text-touchpoint.md`:152-155;
`docs/plans/2026-08-31-kge-restructure-roadmap.md`:240-243). This is that
analysis.

Convention used below: **[F]** marks a fact read from the sources (file/line or
olc source); **[J]** marks judgement. Citations are `path:line` for the repo and
`olcPixelGameEngine.h:line` for `~/workspace/olcPixelGameEngine/olcPixelGameEngine.h`.

## 1. Sources read

- olc v2.30: `olc_ConstructFontSheet` (`:4941`), the font members
  (`fontRenderable` `:1622`, `vFontSpacing` `:1635`), `olc::Renderable`
  (`:1183-1200`), the declarations (`:1463-1469`, `:1493-1494`) and the bodies of
  `DrawString` (`:4111`), `DrawStringProp` (`:4181`), `DrawStringDecal` (`:3999`),
  `DrawStringPropDecal` (`:4022`), `GetTextSize` (`:4091`), `GetTextSizeProp`
  (`:4159`), `nTabSizeInSpaces = 4` (`:947`).
- `main`: `kge-core/.../rasterizer/service/DrawStringService.kt` and
  `kge-core/.../engine/addon/DrawStringAddon.kt` (`git show main:<path>`).
- Current surfaces: `kge-core/.../text/DrawStringService.kt`,
  `kge-core/.../engine/addon/DrawStringAddon.kt`,
  `kge-text-ttf/.../text/ttf/{TtfTextService,TtfDrawStringAddon,TextDraw,TextLayout,Font}.kt`,
  `kge-core/.../engine/Engine.kt`, `kge-core/.../resource/ResourceScope.kt`,
  `kge-core/.../overridable/KGEOverridable.kt`,
  `kge-text-ttf/build.gradle.kts`, `kge-benchmark/.../benchmark/Scene.kt`.
- Design records (settled; not re-opened): `docs/plans/2026-09-16-r6-text-touchpoint.md`
  (its 2026-09-16 and 2026-09-19 revisions), `docs/plans/2026-10-01-r6-round-e2-touchpoint.md`,
  `docs/plans/2026-09-29-r6-round-e-touchpoint.md`,
  `docs/decisions/phase-1/29-c7-bitmap-text.md`, `34-kge-text-ttf-round-c.md`,
  `36-kge-text-ttf-round-d.md`, `38-kge-text-ttf-round-e1.md`,
  `docs/plans/2026-08-31-kge-restructure-roadmap.md` (principles `:50-120`, `R6`
  `:233-250`, `C7` `:251-256`).

## 2. The two surfaces side by side [F]

### 2.1 Services

| | core `DrawStringService` (`kge-core`, `dev.staticsanches.kge.text`) | module `TtfTextService` (`kge-text-ttf`, `dev.staticsanches.kge.text.ttf`) |
|---|---|---|
| declaration | `DrawStringService.kt:29-166`, `: KGEOverridable` | `TtfTextService.kt:11-97`, `: KGEOverridable` |
| resources | `createResources(scope: ResourceScope)` (`:36`, impl `:282-290`) | `createResources(scope: ResourceScope, font: Font)` (`:13-16`, impl `:100-105`) |
| metric | `getTextSize(text, tabSizeInSpaces): Int2D` (`:43-46`) | `getTextSize(font, text, sizePx, tabSizeInSpaces): Int2D` (`:19-24`) |
| metric, prop | `getTextSizeProp(text, tabSizeInSpaces): Int2D` (`:54-57`) | none (decision, not an omission: chunk `38` decision 6) |
| CPU draw | `drawString(scope, target, x, y, text, color, scale: Int, tabSizeInSpaces, mode)` (`:72-82`) + `Int2D` overload (`:85-94`) | `drawString(font, target, x, y, text, sizePx, color, scale: Int, tabSizeInSpaces, mode)` (`:30-41`) + `Int2D` overload (`:44-54`) |
| CPU draw, prop | `drawStringProp(...)` (`:102-124`) | none |
| decal | `drawStringDecal(scope, position: Float2D, text, color, scale: Float2D, tabSizeInSpaces, screenSize: Int2D, decalMode: Decal.Mode, decalStructure: Decal.Structure, collector)` (`:135-146`) and `drawStringPropDecal` (`:152-163`) | none yet — E3's (`2026-09-29-r6-round-e-touchpoint.md`:361-380) |
| glyph source | sheet cell `((c-32)%16, (c-32)/16)` of the scope-resolved `Sprite`; ink is `sheet.get(...).r > 0` (`:519-546`) | shaped glyph id at `sizePx` → `GlyphAtlas` entry → `BlitService.blitRegion` (`TextDraw.kt:100-120`) |
| glyph metrics | `monoSpacing = Int2D(0, 8)` or `fontSpacing[c-32]` (`:575-589`) | the face's shaped advances/offsets + `TextMetrics` (`TextLayout.kt:9-27`) |
| tab | fixed step `8 * tabSizeInSpaces * scale` (`:515`, `:452`) | next stop `(floor(penX/step)+1)*step`, `step = tabSizeInSpaces * spaceAdvance`, pen relative to line origin (`TextDraw.kt:59-61`) |
| line advance | `8 * scale` (`:512`, `:449`) | `ceil(ascender - descender + lineGap)` (`TextDraw.kt:26-29`) |
| `scale` | `Int`, multiplies the painted block and the advance | `Int`, multiplies the blit and the advance (`TextDraw.kt:96,110`) |
| `sizePx` | none — the cell is a fixed 8 | required, no default (`:32,47`; chunk `38` decision 6) |
| non-`Custom` blend | CPU: `Alpha` when `color.a != 255`, else `Mask` (`:497-504`) | coverage folded into `tint.a`, then `srcOver` (`TextDraw.kt:123-140`) |
| `Custom` | preserved, untouched (`:497-499`) | preserved and receives the coverage-weighted pixel (`TextDraw.kt:138`) |
| `getTextSize` and `scale` | no `scale` argument | no `scale` argument (olc/C7 parity, chunk `38` decision 6) — **this is a shared property, not a divergence** |

### 2.2 Addons

| | core `DrawStringAddon` | module `TtfDrawStringAddon` |
|---|---|---|
| roles | `HasDrawTarget, HasDrawModes, HasWindow, HasLayers, HasResourceScope` (`:21-26`) | `HasDrawTarget, HasDrawModes` (`:13-15`) |
| `tabSizeInSpaces` | `4` (`:28`) | `4` (`:17`) |
| metric | `getTextSize(text)`, `getTextSizeProp(text)` (`:31-34`) | `getTextSize(font, text, sizePx)` (`:20-24`) |
| draw | `drawString(position/x,y, text, color = WHITE, scale = 1)` (`:40-57`), `drawStringProp(...)` (`:63-80`) | `drawString(font, position/x,y, text, sizePx, color = WHITE, scale = 1)` (`:27-48`) |
| decal draw | `drawStringDecal(position, text, color = WHITE, scale = Float2D(1,1))`, `drawStringPropDecal(...)` (`:86-129`); viewport `window.screenSize`, queue `layers.target.decalInstances` | none (E3's) |

### 2.3 Ownership and lifetime [F]

| resource | owner | lifetime | reached how |
|---|---|---|---|
| core font (`private class BitmapFont(sheet: Sprite, decal: Decal) : KGEResource`, `:619-624`) | the run's `ResourceScope` under a file-private `FontKey` (`:616`) | created at `Engine.start()` (`Engine.kt:166`), closed with the scope (`ResourceScope.kt:36-47`) | `scope.get(FontKey)` inside the service, never handed out (`:353,368,384,411`) |
| module `Font` (`Font.kt:17-19`, `: KGEResource`) | caller-loaded, adopted into a scope by `createResources(scope, font)` under a **fresh key per adoption** (`TtfTextService.kt:104,128-129`) | closed when the scope closes, or by the caller; use-after-close fails fast (`Font.kt:59-71`) | passed explicitly to every draw |

`ResourceScope.register` is key-identity based and rejects a duplicate key
(`ResourceScope.kt:24-33`); chunk `36` records why the TTF key is fresh per
adoption. `KGEOverridable.Proxy` holds **one** live implementation per service
type in an `AtomicReference` with last-wins `override` (`KGEOverridable.kt`,
`Proxy.current`/`override`).

### 2.4 What `main` contributed [F]

`main`'s `DrawStringService` put the sheet into every draw call as a parameter
(`fontSheet: Sprite` / `fontSheet: Decal`) plus `invertedScreenSize`; the font
belonged to `WindowDependentAddon`. The Kotlin-level solutions worth mining: the
two `drawString` shapes share one private inline walk parameterized by
`spacingByChar: (Char) -> Int2D` (the mono/prop split is a lambda, not duplicated
code), and the decal walk likewise. `C7` kept the lambda seam
(`DrawStringService.kt:430-476,482-552`) and moved ownership from the addon into
the `ResourceScope`. Chunk `29` records that `main` additionally preserved
`Pixel.Mode.Alpha` and that `C7` did not copy it.

### 2.5 What olc v2.30 does and does not have [F]

- One built-in font only: `olc::Renderable fontRenderable` (`:1622`) and
  `std::vector<olc::vi2d> vFontSpacing` (`:1635`), both **private engine
  members**, built once at engine construction by `olc_ConstructFontSheet`
  (`:4677,4941`). `olc::Renderable` is exactly a sprite+decal pair
  (`:1183-1200`) — the same shape as the core's private `BitmapFont`.
- No font object, no font handle, no supplied font, no glyph metrics accessor,
  no atlas, no shaping, **no TTF/OTF path at all**. The `vFontSpacing` table
  (`:4984-4992`) is a compile-time constant for the one sheet.
- No `sizePx`: the only lever is `uint32_t scale` (`:1463-1469`); the cell is
  `8*scale` and the line is `8*scale` (`:4122-4135`).
- No tab parameter: `GetTextSize(const std::string& s)` (`:4091`) has no
  `nTabSizeInSpaces` argument; the engine member (`:947`) is used implicitly.
  KGE carries both the explicit argument (service) and the olc default (addon).
- The decal variants hardcode `fontRenderable` (`:3999-4041`), exactly as the
  core hardcodes the scope-resolved sheet.
- olc also has rotated string decals (`:4044-4089`), a console/text-entry surface
  (`:4346-4350`) and a global `SetPixelMode` state (`:4118-4120,4156`); KGE
  deliberately does not port the first two (E touch-point "out of scope") and
  passes `mode` explicitly instead of mutating engine state.

**Consequence for the question [J]:** olc defines no joint bitmap/TTF API and no
font abstraction, so "olc parity" cannot decide the unification either way. The
only olc-shaped requirement is the *drawing call's* shape (names, defaults,
`\n`/`\t`, `Custom`-only preservation), which both KGE surfaces already meet.

## 3. Where one signature cannot serve both today [F]

1. **Font identity is carried differently.** Core resolves a private sheet from
   the `ResourceScope` and takes no font argument (`DrawStringService.kt:72-82`);
   the module takes `font: Font` explicitly and the scope is ownership only
   (`TtfTextService.kt:30-41`; chunk `36` decision 6, chunk `38` decision 7).
2. **`sizePx` exists on one side only.** Required and defaultless in the module
   (`:32,47-48`), meaningless in the core (fixed 8 px cell).
3. **The mono/prop pair has no module counterpart**, by recorded decision
   (chunk `38` decision 6). Four names in the core, two in the module.
4. **`createResources` arity differs**: `(scope)` vs `(scope, font)`.
5. **Line advance and tab semantics differ** in kind, not only in value: fixed
   `8*scale` and a fixed tab step (`:512,515`) against the face's line height and
   a tab **stop** (`TextDraw.kt:26-29,59-61`). Both are recorded divergences
   (chunk `38` decisions 2-3).
6. **Decal variants exist on one side only** and the TTF side is unshipped (E3).
7. **Non-`Custom` blending is a different rule** (opaque→`Mask`/translucent→
   `Alpha` vs coverage `srcOver`), though both preserve only `Custom`.
8. Minor: the core's CPU path validates `tabSizeInSpaces`, the core's decal path
   does not (`:452,495` and no check in `:430-476`; chunk `29`), while the TTF
   walk validates for both measure and draw (`TextDraw.kt:24`).

## 4. What "unified" could mean

All sketches below place every new type in `kge-core`; the dependency graph
forces it (`kge-text-ttf/build.gradle.kts`: `api(project(":kge-core"))`, never the
reverse). The module can only *implement* or *adapt to* a core declaration.

### (a) A core-declared handle + service shape [J]

```kotlin
// kge-core, dev.staticsanches.kge.text — public, non-owning handle
interface TextFont {
    fun getTextSize(text: String, tabSizeInSpaces: Int): Int2D
    fun drawString(
        target: Pixmap.Mutable, x: Int, y: Int, text: String,
        color: Pixel, scale: Int, tabSizeInSpaces: Int, mode: Pixel.Mode,
    )
}

// kge-core, dev.staticsanches.kge.text — the shape, not a new plug
interface TextService {
    fun getTextSize(font: TextFont, text: String, tabSizeInSpaces: Int): Int2D
    fun drawString(font: TextFont, target: Pixmap.Mutable, x: Int, y: Int, text: String, /* … */)
}
```

- Core: a `private class BitmapFont : TextFont` behind a public accessor (e.g.
  `DrawStringService.font(scope): TextFont`) or a public handle registered in the
  scope. The module: `Font.at(sizePx): TextFont` returning a module-`private`
  `SizedFont`, or `Font : TextFont` with `sizePx` hoisted — see below.
- **`sizePx` breaks the shape.** If `sizePx` stays a method parameter, the core
  implementation ignores it, and API discipline ("every public parameter has an
  observable effect, pinned by a test", `AGENTS.md`) is violated. The only clean
  fix is a **handle-per-size** (`Font.at(sizePx)`), i.e. the handle *is* "a font
  resolved at a size"; the core's handle is the sheet at 8.
- **Mono/prop must leave `TextService`.** A single `drawString`/`getTextSize`
  cannot carry the prop variants; they become a core extension interface
  (`PropTextFont`/`DrawStringPropService`) or are dropped from the shared shape.
- **`TextService` must not be a `KGEOverridable.Proxy`** if both implementations
  are to stay live: the registry holds one implementation per type
  (`KGEOverridable.kt`), so a single unified proxy would make the two fonts
  mutually exclusive process-wide. Kept as a *shape*, `DrawStringService` and
  `TtfTextService` remain independently overridable.
- What breaks: chunk `29`'s recorded "no public `BitmapFont`, no `HasBitmapFont`"
  is reversed in substance — a public handle (and an accessor or a public key)
  appears in the core. That is exactly the widening principle 7 says needs a
  **named consumer**, and there is none today (§5).
- Reversibility: high — the shape is additive except for the accessor, and E3's
  decal path can be added as a second, separate shape later.

### (a′) The strongest form: handle-per-size, shared shape, two plugs [J]

This is (a) with the `sizePx` and prop questions answered explicitly:

- `TextFont` (core, public) = metrics + mono CPU draw + (later) decal draw.
- Core's `BitmapFont` implements it privately; the public accessor is the only
  widening.
- The module's adapter is `Font.at(sizePx): TextFont` — a `private class` in the
  module over `shape`/`glyph`/`walkText`, which already take `sizePx` per call
  (`Font.kt:27-54`, `TextDraw.kt:16,100`), so no module change beyond the
  adapter.
- The two `KGEOverridable` proxies stay; an app that wants both fonts holds two
  handles through one shape.
- Prop stays a core-side extension; the module never has to publish a no-op.
- Cost: one public interface + one accessor in the core; one private adapter in
  the module; a lifetime contract test on both sides (fail-fast after the owner
  closes — the core's `scope.get` and the module's `Font.checkNotReleased`
  already provide it, `DrawStringService.kt:353`, `Font.kt:67-71`).

### (b) A thin adapter in the module over the core's service, core untouched [J]

Not expressible as stated. `DrawStringService.drawString` has no `font`/`sizePx`
slot (`:72-82`), so a TTF-backed implementation cannot know which face at which
size to draw; `getTextSizeProp` and the decal variants have no TTF meaning. The
only shape that compiles is stateful —
`private class TtfAsBitmapDrawStringService(private val font: Font, private val sizePx: Int) : DrawStringService` —
which (i) contradicts chunk `29`'s stateless rationale and the "draws take the
`Font` directly" decision (chunk `36` decision 6), (ii) still inherits the
core's fixed tab step and line box, so the same string would measure/render
differently depending on which service the app called, and (iii) makes the
extension contract depend on construction state. **Refuted as stated**; the
stateful variant is a facade whose semantic cost exceeds its benefit.

### (c) Status quo: two sibling APIs plus a documented mapping [J]

No type change anywhere. An app that needs font-agnostic code writes its own
one-method interface over whichever addon it holds. The recordings in this
document (tables in §2) are the mapping.

### (d) Unify at the addon/engine level instead [J]

A core-declared `TextAddon : HasDrawTarget, HasDrawModes` with the olc-shaped
methods cannot be satisfied by the module's addon without a `font`/`sizePx`
slot; the only fixes are (i) making the addons carry `font`/`sizePx` state —
rejected by the module's stateless design — or (ii) adding a `HasTextFont` role.
(ii) is (a′) hoisted one level: the engine host already satisfies every role
(`Engine.kt:33-44`), so a role-carried handle works, but it collapses the two
addons into one and gives a host **one** active font, losing the mixed use that
works today. **Judged worse than (a′), not better.**

### Comparison

| option | core change | module change | `sizePx` | prop | decal, later | E3 safe? | coexist two fonts? |
|---|---|---|---|---|---|---|---|
| (a)/(a′) | public `TextFont` + accessor (reverses chunk `29` in substance) | `Font.at(sizePx)` adapter | on the handle | core extension | second shape after E3 | yes, additive | yes |
| (b) | none | stateful facade | constructor state | inherited, wrong | inherited, wrong | yes | no (one service) |
| (c) | none | none | unchanged | unchanged | unchanged | yes | yes |
| (d) | public `TextFont` + role | new addon interface + role | addon state | addon-only | addon | yes | no (one active font) |

## 5. The interactions that decide the question [J]

**`sizePx`.** A unified method cannot carry `sizePx` without either an
unobservable parameter on the core side or a core-side validation that has no
consumer, both contrary to the API-discipline rule. The honest residence is the
handle, which forces the public handle. This is the single largest structural
cost of unification.

**Handle lifetime and ownership.** `Font` is a `KGEResource` (module) and the
core sheet is scope-owned; a unified `TextFont` should be a **non-owning view**
(nothing in either surface needs a second owner), with fail-fast after the owner
closes. Making `TextFont : KGEResource` would create double ownership for the
same module `Font` (its scope and the handle) and is not recommended.

**Mono/prop.** The pair is olc's two-sheet artifact and the module correctly has
one metric set (chunk `38` decision 6). Any unified surface either publishes a
prop member the module cannot honour observably, or splits prop out. This is a
reason the shared shape is smaller than either current surface.

**Tab-stop / line-box.** Already recorded divergences with different *kinds* of
rule (fixed cell vs face metrics). A unified interface can only document them as
implementation-defined, which weakens the contract rather than strengthening it;
sharing a signature does not share the semantics.

**Decal variants.** The core has four variants; the module has none, and E3 owns
the TTF decal contract (`HasWindow`/`HasLayers`, `Decal.Mode`/`Structure`,
viewport, coverage texture; `2026-09-29-r6-round-e-touchpoint.md`:361-380).
Unifying before E3 fixes the decal shape twice or constrains E3 — the
"provisional API a later concept must break" the roadmap forbids
(`2026-08-31-kge-restructure-roadmap.md` principle list; `AGENTS.md` "ship
concepts whole").

**Engine-level coexistence.** Today `DrawStringAddon` and `TtfDrawStringAddon`
are disjoint in parameter lists (`Font` + `sizePx` vs not), so one host can
implement both and draw both fonts — recorded at the E touch-point
(`2026-09-29-r6-round-e-touchpoint.md`:155-158). A unified no-handle interface
destroys that; a handle-parameterized one (`a′`) preserves it. This is the
strongest argument *for* doing it the `a′` way if it is done at all — and the
strongest argument against a careless unification.

**Kotlin/KMP cost.** `Pixel` is a value class (`Pixel.kt:20`) but `Int2D` and
`Float2D` are `data class` (`Int2D.kt:11`, `Float2D.kt:11`), so the common
premise that all three box is not supported by the source; the unification adds
no new boxing because every option (a′)-(d) passes the same types the surfaces
already pass. The real allocation costs that the review axes found are inside
the walks (chunk `29`: a per-character `Int2D`; chunk `38`: a per-glyph
`Float2D` sink), and they are orthogonal to the API shape. `expect`/`actual`
width is untouched: the unified types would be `commonMain`-only, and the TTF
platform seam (`NativeFace`) stays module-internal (chunk `34`). Per-call
shaping vs the cached sheet is likewise orthogonal — the sheet is a grid lookup,
the TTF path shapes per draw until a cache exists (roadmap `R6` open item), and
neither is changed by sharing a signature.

## 6. Recommendation [J]

**Do not unify now. Keep the two sibling surfaces (option c), and record option
(a′) as the design to adopt *if and when* a consumer that must write
font-agnostic text code exists — and, in any case, not before E3 has fixed the
TTF decal contract.**

Rationale:

1. **No consumer.** The only shipped text consumer names the core addon directly
   (`kge-benchmark/.../Scene.kt:31-33`, `TextSceneTarget : ClearAddon,
   DrawStringAddon`), and it is a benchmark, not an application. Principle 7 makes
   widening the change that needs a named consumer; a public core handle has
   none.
2. **The stated purpose is already met.** The follow-up was to "exercise the
   plug". Both services are `KGEOverridable` proxies, and the module's
   extension-contract proof exists (chunk `34`, T2 revision and
   `TtfTextServiceTest`). A unified *type* is not what exercises `KGEOverridable`.
3. **The unification is not free and not small.** It reverses chunk `29`'s
   deliberately private font in substance, forces `sizePx` onto a handle to stay
   observable, splits prop off the shared shape, and risks collapsing the two
   independent plugs or the two-font host.
4. **Timing.** The decal half of both surfaces is E3's and unshipped; a
   CPU-only unified surface now is the provisional API the roadmap's
   "ship concepts whole" rule forbids.
5. **The two fonts are not substitutes.** A fixed 8x8 sheet and an arbitrary
   face at an arbitrary size differ in cell, metric, tab and line rule; a shared
   signature would advertise a substitutability that does not exist, while the
   mapping recorded here is what a consumer actually needs.

Cost of this recommendation: an application that wants font-agnostic drawing
writes a local interface today. That is cheap and reversible; if the core later
publishes (a′), the local interface becomes an adapter. The cost of the
alternative — unifying early — is a public core handle with no consumer, a
possible collision with E3, and the loss of the mixed-font host unless the
careful `a′` shape is chosen.

If the owner chooses to unify anyway, **(a′) is the only shape that satisfies
the recorded disciplines**: handle-per-size, `TextFont` non-owning, `TextService`
a shape rather than a proxy, prop a core extension, and the decal variants added
as a second shape only after E3.

## 7. Decisions a future touch-point would have to take (not taken here)

1. Whether to unify at all, and the named consumer that justifies the core
   widening (principle 7).
2. The handle's public shape: a public `TextFont` interface with a `private`
   implementation behind a public accessor, versus making the core's font public
   outright (which reverses chunk `29`).
3. Where `sizePx` lives (handle-per-size versus method parameter with an
   observable core-side effect).
4. The mono/prop disposition: dropped from the shared shape, a core extension
   interface, or a documented fixed-cell metric on the shared shape.
5. Whether the shared shape is a `KGEOverridable` proxy; if so, how two live
   implementations coexist given the one-slot registry.
6. The handle's lifetime contract: non-owning, fail-fast after the owner closes;
   explicitly **not** a `KGEResource` (double ownership).
7. The decal variants' unified shape — decided **after** E3 fixes the TTF decal
   contract (viewport, `HasWindow`/`HasLayers`, structure, coverage texture).
8. Whether the tab/line-box contract under one interface is
   "implementation-defined per font" or a single rule (and how it is pinned).
9. How `createResources` is expressed under one seam.
10. Whether `DrawStringAddon` and `TtfDrawStringAddon` stay separable so one host
    can draw both fonts (today guaranteed by disjoint signatures).

## 8. Risks

**Unifying too early** — [J] reverses a recorded core decision without new
evidence; constrains or is broken by E3; forces an unobservable `sizePx` or a
public handle with no consumer; may collapse the two independent plugs
(`KGEOverridable`'s one slot) or the mixed-font host; ships a provisional API
the roadmap forbids.

**Never unifying** — [J] a font-agnostic consumer writes its own interface and
if the core later declares one the shapes can drift; a third text module (a
bitmap-from-file, a SDF path) repeats the TTF pattern and the divergence
multiplies; the tab/line-box/`Custom` divergences stay implicit rather than
contractual. Mitigations available now and cheap: this document is the mapping,
and the trigger for revisiting is the first real font-agnostic consumer or the
close of E3, whichever comes first.

**Leaving it unrecorded** — the same divergence is re-derived a third time when
E3's decal work is designed; §2 and §5 exist to prevent that.

## 9. Uncertainty, and what was not verified

- No measurement was run: this is a static analysis, no Gradle invocation, so
  every cost claim above is structural, not measured.
- The premise that `Int2D`/`Float2D` box as value classes is **not** supported
  by the source: both are `data class` (`Int2D.kt:11`, `Float2D.kt:11`); only
  `Pixel`, `Modifier`, `ButtonState` and `CircleOctantMask` are value classes in
  `commonMain`. The boxing discussion in §5 is written against what the files
  say.
- The claim that a full unification would make the two implementations mutually
  exclusive rests on reading `KGEOverridable.Proxy` (single `AtomicReference`);
  no experiment was run, and the conclusion only applies if the unified type is a
  proxy, which (a′) avoids.
- Whether a public `TextFont` accessor on the core would be accepted as the
  extension contract (principle 1) rather than as an unjustified widening is a
  judgement for the touch-point; this document records the arguments but does not
  decide it.
- E3's decal design is unshipped; any statement about the unified decal shape is
  contingent on the E3 touch-point.
- olc line numbers are from the v2.30 header at the path named in `AGENTS.md`;
  they were read, not assumed.
