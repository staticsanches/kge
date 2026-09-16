# `C7` bitmap text (core) — micro-plan, round A

**Date:** 2026-09-16. Round **A** of the text work: revive `C7` in `kge-core`,
porting olc's bitmap text with **zero new dependencies** and full olc parity.
Round plan: A bitmap (core) → B `kge-text-ttf` scaffold → C face + shaping +
layout → D raster + atlas/cache → E blit + addons + decal. Context:
`docs/plans/2026-09-16-r6-text-touchpoint.md` (Revision section).

## Oracle

olc v2.30 is the behavior authority: `DrawString`/`DrawStringProp`/
`GetTextSize`/`GetTextSizeProp`/`…Decal` (`~/workspace/olcPixelGameEngine/olcPixelGameEngine.h`
`:3999-4200`) and the sheet builder `olc_ConstructFontSheet` (`:1691`). The
bitmap text is **recreated from olc**, not brought over from `main`: `main`
implemented it and is a useful **working example**, but it is evidence, not a
mandate and not necessarily correct. So the sheet data, the spacing table, the
metrics and the draw are derived from olc and merely cross-checked against
`main`; every divergence of `main` from olc is a **finding to record**, not a
basis to copy, and `main`'s structure/naming must not be carried verbatim.
`Colors.WHITE`, `scale=1`, `tabSizeInSpaces=4` (olc `nTabSizeInSpaces`).

## Scope

The bitmap sheet, the metrics, the CPU draw (mono + prop), the decal draw
(mono + prop), the stateless service seam with a private font holder, and the
addon. No dependency changes; no TTF.

## Dependencies (added in later rounds, when the code uses them)

Round A adds **no** dependency, and the version catalog carries no entry until
it is consumed (the catalog stays clean). Versions are pinned by the touch-point
spike findings.

- **Round C (shaping):** JVM `org.lwjgl:lwjgl-harfbuzz` under the existing
  `lwjgl = "3.4.3"` BOM (catalog alias `lwjgl-harfbuzz`); web `harfbuzzjs`
  `1.6.1` (HarfBuzz 14.4.0) via `npm("harfbuzzjs", …)`, referenced as
  `libs.versions.harfbuzzjs.get()` — the catalog has no npm dependency type, so
  only the version is centralized.
- **Round D (rasterization):** JVM `org.lwjgl:lwjgl-freetype` under the same BOM
  (catalog alias `lwjgl-freetype`, FreeType 2.14.3); web
  `@zkl2333/freetype-wasm` `2.14.3`, referenced as
  `libs.versions.freetype.wasm.get()`.
- **Round E:** no new dependencies.

JVM native classifiers follow the `lwjglNatives` block in
`kge-core/build.gradle.kts`; the web npm Emscripten glue needs the webpack
`resolve.fallback` entries (`module`/`fs`/`path`) recorded in the touch-point
spike findings.

## Decisions (recorded)

1. **Service seam.** `DrawStringService : KGEOverridable` (companion `Proxy`
   with the built-in default, like `SpriteService`), **stateless**:
   `createResources(scope)` creates the private font holder and registers it in
   the scope; every draw takes that `ResourceScope` (the `Renderer.drawDecal(scope,
   …)` shape) and resolves the holder from it — the metrics need no resource
   (olc reads only the spacing table). The decal variants produce public
   `DecalInstance`s (`Decal` is public; only the holder is private). It is
   **not** folded into `Rasterizer` (the decal variant is not a raster op); it
   lives in a new `dev.staticsanches.kge.text` package. Naming follows olc's API
   surface (`drawString`/`getTextSize` + `Prop`/`Decal` variants); the Kotlin
   service/addon type names are a micro-plan detail, not inherited from `main`.
2. **Font resource.** A **private** holder inside the default implementation
   owns the sheet `Sprite` (CPU) and its `Decal` (GPU); `createResources(scope)`
   registers it under a private `ResourceScope.Key`, and the **engine's
   `ResourceScope`** owns and closes it at teardown (created in `start()` with
   the context current). No service state, no `release()`, no public font type.
   The service receives the scope as a draw parameter instead, so it stays
   stateless like `Renderer`. Tests reach the resource through `internal`
   accessors that return **public** types (`Sprite`/`Decal`) — the private holder
   stays private, and an `internal` accessor cannot expose it anyway (the
   exposure check), so the public API still names no font type.
3. **Sheet.** 128x48, chars 32..126, 8x8 cells, 16 per row; white on
   transparent, built from the olc-derived `FONT_SHEET_DATA` (via `main`).
4. **Pixel mode.** olc parity: unless the active mode is `Custom`, an opaque
   color draws in `Mask`, a translucent one in `Alpha`.
5. **API parity.** `getTextSize`/`getTextSizeProp`, `drawString`/`drawStringProp`
   (both `Int2D` and raw `x, y`), `drawStringDecal`/`drawStringPropDecal`;
   `tabSizeInSpaces` is an engine/addon setting (default 4).
6. **Engine exposure.** The font is an implementation detail: no public font
   type and no `HasBitmapFont`. The addon reaches the run's resources through a
   public `HasResourceScope` role (`@KGESensitiveAPI val resourceScope:
   ResourceScope`, fail-fast outside a run like `layers`/`driver`), implemented
   by `Engine`; the scope is already public, so nothing private leaks and
   extenders gain legitimate access to engine-owned resources.

## Steps (TDD: red → green each)

1. **Sheet.** The default service builds the 128x48 sheet and the `Decal`
   (`BitmapFont`), re-implemented from olc `olc_ConstructFontSheet`
   (`:4943-4990`): the 16x64 char payload, each set bit painting `(k,k,k,k)`
   (opaque white) and each clear bit `(0,0,0,0)`. Tests: dimensions; the painted
   sheet matches the `text/sheet` golden, authored from an independent
   olc-derived port (not from the engine's output, per the harness rule);
   `BitmapFont.close()` closes both.
2. **Metrics.** `getTextSize`/`getTextSizeProp`. Tests: mono `"AB"` → 16x8,
   `"A\nB"` → 8x16, `"A\tB"` (tab 4) → 48x8 (a fixed `nTabSizeInSpaces` advance,
   no tab stops — the plan's earlier 40x8 was a typo, corrected 2026-09-16 after
   the olc/`main` cross-check); prop uses `fontSpacing[c-32]` (`size.y *= 8` at
   the end, as olc); `tabSizeInSpaces <= 0` fails fast.
3. **CPU draw.** `drawString`/`drawStringProp` into a `Pixmap.Mutable`. Tests:
   goldens for a short mono/prop string at scale 1 and 2; newline/tab advance;
   a translucent color blends in `Alpha`; an opaque one in `Mask`; `scale <= 0`
   is a no-op.
4. **Service shape** (refactor of steps 1–3's surface). Make `DrawStringService`
   stateless and scope-parameterized: `createResources(scope)` builds the
   private holder and registers it under a private `ResourceScope.Key`; the draw
   methods take the `ResourceScope` and resolve the holder from it; drop the
   public `BitmapFont` and `createBitmapFont()`. Add `internal` accessors that
   return the sheet `Sprite`/`Decal` for the tests. Tests: `createResources`
   registers the holder and the scope closes it (the returned sheet fails fast
   after `scope.close()`); a draw against a scope without the holder fails fast;
   the steps 1–3 tests are updated to pass a scope.
5. **Engine wiring.** `HasResourceScope` implemented by `Engine` (fail-fast
   outside a run, like `layers`) and `DrawStringService.createResources(scope)`
   called in `start()` right after `Renderer.createResources(device, scope)`;
   `layers`/`drawTarget` behave as before. Tests: `resourceScope` is available
   during the run and fails fast after teardown; the font is closed with the
   scope.
6. **Decal draw.** `drawStringDecal`/`drawStringPropDecal` build
   `DecalInstance`s from the holder's decal (partial-decal cells), collecting
   them through the caller's collector. Tests: the collected instances' source
   rects/positions/UVs match olc (`DrawPartialDecal`, `:3489-3519`, which sets
   `mode = nDecalMode`, `structure = nDecalStructure` and `tint = color` — the
   decal path does **not** resolve the pixel mode, unlike step 3); the requested
   `decalMode`/`decalStructure` and the color tint land in the instances. Mono
   cells use `sourcePosition = (8*ox, 8*oy)`, `sourceSize = 8x8`; prop cells use
   `(8*ox + spacing.sourceColumn, 8*oy)`, `spacing.advance x 8`.
7. **Addon.** `DrawStringAddon : HasDrawTarget, HasDrawModes, HasWindow,
   HasLayers, HasResourceScope` with the olc defaults (`tabSizeInSpaces` 4 as an
   overridable `val`, `color = WHITE`, `scale` 1 / `Float2D(1, 1)`) and all the
   variants (CPU and decal, `Int2D` and raw): the CPU ones draw into `drawTarget
   ?: return`; the decal ones (position `Float2D`) enqueue into
   `layers.target.decalInstances` with `viewport = window.screenSize` (olc's
   `vViewSize`, matching `DrawDecalAddon`), the current `decalMode`/
   `decalStructure` and the service's collector. Tests: draws into the layer
   target (host double, as `DecalAddonsTest`); the decal variants enqueue
   instances into the target layer with the right viewport/modes; `drawTarget ==
   null` returns without drawing. The addon comes last because it is the
   ergonomic surface over both drawing paths.

## Out of scope (round A)

- Glyph atlas, TTF/HarfBuzz/FreeType, `kge-text-ttf` (rounds B–E).
- `TextEntryEnable`/console.
- `nTabSizeInSpaces` per-engine configuration beyond the default (add only if a
  consumer appears).
- Rotated string variants (`DrawRotatedString*`, olc `:4045`).

## Gate

`./gradlew build --rerun-tasks` green, then the two-axis review, then the
decisions-log entry (recording the `C7` revival and the round).

## Carry-forward (investigate after round A, not part of it)

- **Decal draw-call batching for shared sprites.** `DefaultRenderer.drawDecal`
  issues one `drawArrays` per `DecalInstance`, re-applying the texture,
  re-uploading that instance's vertices and resetting the blend each time. Text
  draws many small cells from the *same* font-sheet decal, so the question is
  whether instances sharing one sprite/decal texture should be batched again.
  `main` did exactly this: `BaseRenderer.drawDecals(List<DecalInstance>)` packed
  consecutive compatible instances (same mode/structure/`decal.uuid`) into one
  ARRAY_BUFFER upload and issued a single `GL.multiDrawArrays` over
  `firsts`/`counts` (the `QuadInfo` buffers), falling back to `drawArrays` for a
  lone instance — via `DecalInstance.VerticesInfo.putAll(ByteBuffer)`. The new
  renderer dropped `multiDrawArrays` (deferred as benchmark-gated, decisions
  log #22) and redesigned `VerticesInfo` as a pull contract (2026-09-13), so
  reviving batching must not re-couple geometry to the byte layout. The analysis
  must be a **benchmark comparison of the two solutions** (per-instance
  `drawArrays` vs `multiDrawArrays` batching), not a judgement call. Decide at
  the `R6` decal round or a dedicated renderer round; round A keeps the current
  per-instance path.
