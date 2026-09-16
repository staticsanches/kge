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
(mono + prop), the service seam, the engine-owned font resource and the addon.
No dependency changes; no TTF.

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
   with the built-in default, like `SpriteService`), stateless: every method
   takes the target/`fontSheet` and returns pixels or a `DecalInstance`. It is
   **not** folded into `Rasterizer` (the decal variant is not a raster op); it
   lives in a new `dev.staticsanches.kge.text` package. Naming follows olc's API
   surface (`drawString`/`getTextSize` + `Prop`/`Decal` variants); the Kotlin
   service/addon type names are a micro-plan detail, not inherited from `main`.
2. **Font resource.** `BitmapFont : KGEResource` owns the sheet `Sprite` (CPU)
   and its `Decal` (GPU), created by the service and owned by the **engine's
   `ResourceScope`** (built in `start()` with the context current, closed at
   teardown), exposed through a `HasBitmapFont` role — the engine is the
   composition root, so no process-sticky service state.
3. **Sheet.** 128x48, chars 32..126, 8x8 cells, 16 per row; white on
   transparent, built from the olc-derived `FONT_SHEET_DATA` (via `main`).
4. **Pixel mode.** olc parity: unless the active mode is `Custom`, an opaque
   color draws in `Mask`, a translucent one in `Alpha`.
5. **API parity.** `getTextSize`/`getTextSizeProp`, `drawString`/`drawStringProp`
   (both `Int2D` and raw `x, y`), `drawStringDecal`/`drawStringPropDecal`;
   `tabSizeInSpaces` is an engine/addon setting (default 4).

## Steps (TDD: red → green each)

1. **Sheet.** The default service builds the 128x48 sheet and the `Decal`
   (`BitmapFont`), re-implemented from olc `olc_ConstructFontSheet`
   (`:4943-4990`): the 16x64 char payload, each set bit painting `(k,k,k,k)`
   (opaque white) and each clear bit `(0,0,0,0)`. Tests: dimensions; the painted
   sheet matches the `text/sheet` golden, authored from an independent
   olc-derived port (not from the engine's output, per the harness rule);
   `BitmapFont.close()` closes both.
2. **Metrics.** `getTextSize`/`getTextSizeProp`. Tests: mono `"AB"` → 16x8,
   `"A\nB"` → 8x16, `"A\tB"` (tab 4) → 40x8; prop uses `fontSpacing[c-32]`
   (`size.y *= 8` at the end, as olc); `tabSizeInSpaces <= 0` fails fast.
3. **CPU draw.** `drawString`/`drawStringProp` into a `Pixmap.Mutable`. Tests:
   goldens for a short mono/prop string at scale 1 and 2; newline/tab advance;
   a translucent color blends in `Alpha`; an opaque one in `Mask`; `scale <= 0`
   is a no-op.
4. **Engine wiring.** `BitmapFont` + `HasBitmapFont`; `Engine.start()` creates it
   in scope and `layers`/`drawTarget` behave as before. Tests: present during the
   run, closed after teardown.
5. **Addon.** `DrawStringAddon : HasDrawTarget, HasDrawModes, HasBitmapFont` with
   the olc defaults and both overloads. Tests: draws into the layer target
   (host double, as `DecalAddonsTest`); `drawTarget == null` returns without
   drawing.
6. **Decal draw.** `drawStringDecal`/`drawStringPropDecal` build
   `DecalInstance`s from the sheet decal (partial-decal cells), queued by the
   caller. Tests: the queued instances' source rects/positions match olc; a
   translucent color maps to the right `Decal.Mode`.

## Out of scope (round A)

- Glyph atlas, TTF/HarfBuzz/FreeType, `kge-text-ttf` (rounds B–E).
- `TextEntryEnable`/console.
- `nTabSizeInSpaces` per-engine configuration beyond the default (add only if a
  consumer appears).
- Rotated string variants (`DrawRotatedString*`, olc `:4045`).

## Gate

`./gradlew build --rerun-tasks` green, then the two-axis review, then the
decisions-log entry (recording the `C7` revival and the round).
