## 2026-09-16 — `C7` bitmap text (core), round A: touch-point + close

Round A of the text work (context: `docs/plans/2026-09-16-r6-text-touchpoint.md`,
Revision section). `C7` revives olc's bitmap text in `kge-core` with **zero new
dependencies**; the elaborate TTF text stays in the future opt-in
`kge-text-ttf` module (`R6`, rounds B–E). Micro-plan:
`docs/plans/2026-09-16-c7-bitmap-text-microplan.md`.

### Shape (decided at the touch-point, reworked during implementation)

- `DrawStringService : KGEOverridable` (`dev.staticsanches.kge.text`) is
  **stateless**: `createResources(scope)` builds the font (a 128x48 sheet
  `Sprite` + its `Decal`) and registers it in the engine `ResourceScope` under a
  private key; the draws take that scope (the `Renderer.drawDecal(scope, …)`
  shape) and resolve the font from it. The metrics (`getTextSize`/
  `getTextSizeProp`) take no font. The font is a **private** implementation
  type — no public `BitmapFont`, no `HasBitmapFont`; only `internal` accessors
  return the public `Sprite`/`Decal` for tests.
- **Rationale (supersedes the micro-plan's first shape):** the initial
  micro-plan exposed a public `BitmapFont` through a `HasBitmapFont` role with a
  service-owned resource and a `release()`. Rejected during implementation: the
  font is an implementation detail, and `Renderer` already establishes the
  stateless `createResources(scope)` + scope-per-draw pattern, so no service
  state and no `release()` are needed.
- `HasResourceScope` (`@KGESensitiveAPI val resourceScope: ResourceScope`,
  fail-fast outside a run) exposes the run's scope; `Engine.start()` calls
  `DrawStringService.createResources(scope)` right after
  `Renderer.createResources`.
- `DrawStringAddon : HasDrawTarget, HasDrawModes, HasWindow, HasLayers,
  HasResourceScope` is the ergonomic surface (olc defaults: `tabSizeInSpaces` 4,
  `color = WHITE`, `scale` 1/`(1, 1)`); the decal variants queue instances into
  `layers.target.decalInstances` with `viewport = window.screenSize` (olc
  `vViewSize`), mirroring `DrawDecalAddon`.

### Divergences (recorded)

- **CPU pixel mode:** olc's `DrawString`/`DrawStringProp` preserve only
  `Pixel.Mode.Custom`; an opaque color draws as `Mask`, a translucent one as
  `Alpha`. `main` additionally preserved `Alpha` — **not copied** (a `main`
  divergence from olc).
- **`scale <= 0` is a no-op** (plan-mandated). olc's `scale` is `uint32_t`, so
  `0` falls into the unscaled branch and still draws; recorded.
- **Decal path:** no pixel-mode resolution — the color is the tint and the
  mode/structure are the requested `decalMode`/`decalStructure` (olc
  `DrawPartialDecal` sets `di.mode = nDecalMode`, `di.structure =
  nDecalStructure`). The decal path has **no** `tabSizeInSpaces` check (olc/`main`
  parity), unlike the CPU path.
- **Mono tab advance is fixed:** `"A\tB"` (tab 4) is 48x8, not the micro-plan's
  40x8; the 40x8 was a typo, corrected against olc/`main` during the round.

### Goldens

The sheet and the draws are pinned by `commonTest` goldens (`text/sheet`,
`text/mono`, `text/mono-scale-2`, `text/prop`, `text/prop-scale-2`) authored
from an independent olc-derived throwaway port, never from the engine output
(harness rule, log #28). The 16 payload literals were cross-checked
byte-identical against olc and `main`.

### Gate and review

`./gradlew build --rerun-tasks` green (jvm + browser js/wasmJs + ktlint +
assemble/metadata). Two-axis review (Standards + Spec) round 1: both axes PASS,
no Critical/Important; four Minor findings — the metrics walk allocated
per-character `Int2D` (a `main` regression), the `Int2D` draw overloads had no
test, the text GL test fixture duplicated the engine's `installGl`, and the
`internal` font accessors carried an unread receiver — all fixed, then the gate
re-run green and the review re-run on the final tree.
