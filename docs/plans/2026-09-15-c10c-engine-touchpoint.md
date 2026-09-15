# Engine (C10c) — addons, roles and layers: touch-point

**Date:** 2026-09-15. Pre-touch-point material for `C10c`, the third and last
`C10` session (`E2` addons), after `C10b` (input, closed 2026-09-13, decisions
log #25) and the FPS-benchmark round (#26). `C10a` (#24) split `C10` into
`C10a` (loop/window/time) → `C10b` (input) → `C10c` (addons); each session
closes with its own gate and decisions-log entry. Design decisions live in the
`C10` touch-point (`docs/plans/2026-09-13-c10-engine-touchpoint.md`, decision
4) and the carry-forwards of #24/#25/#26.

Status: **decided 2026-09-15** (owner). The open items below were closed in
discussion; the micro-plan is written just-in-time on top of them.

## Real consumer

A user application that subclasses `Engine`, draws through the C6/C9 stack
(`Rasterizer` over a per-layer `Sprite`, `Decal` for GPU sprites), composites
several layers, reads input (C10b) and manipulates the window. The engine loop
(`C10a`) currently renders only the clear + present step; content and layers do
not exist. The FPS benchmark (#26) subclasses `Engine` directly and does not
draw, so it is not the drawing consumer.

## Established (from the C10 touch-point + carry-forwards)

- **`E2` is ISP roles over narrow interfaces — no central state contract**
  (`C10` decision 4). Each capability is a narrow role owning one cohesive
  holder; an addon is a set of defaults over the role it needs; the engine is
  the composition root implementing the roles. The
  `WithKGEState`/`WindowDependentAddon` forwarding-all shape is rejected;
  "pass the role, not the engine" is the rule.
- **Callbacks ship in `C10a`** (loop-owned); `C10c` is ergonomics over the
  roles `C10a`/`C10b` expose (`C10` decision 1).
- **Window manipulation (carry-forward #26):** the mutable title
  (`main` `changeWindowTitle` → `glfwSetWindowTitle`), show/hide and the
  settable close flag (`windowShouldClose`) were not in `C10a` (its
  `WindowConfig` only holds the initial state); they land here with a `Driver`
  seam extension.
- **Text is `R6`:** the `main` bitmap font is not ported, so the text addons
  (`DrawStringAddon`: `drawString*`, `getTextSize*`, `fontSheet`,
  `tabSizeInSpaces`) are out of scope.

## Foundation in the kernel (C10c consumes)

- **`Rasterizer`** (C6/R1) — the five independently overridable sub-services
  over a `Pixmap.Mutable`; every primitive already takes `target: Pixmap.Mutable`
  and `pixelMode`. `Sprite` is a `Pixmap.Mutable` + `Pixmap.RawBacked`.
- **`Renderer`** (C9) is stateless over an engine-owned `ResourceScope`:
  `createResources(device, scope)`, `applyTexture`, `prepareDrawing`,
  `drawLayerQuad(scope, offset, scale, tint)`, `drawDecal(scope, instance)`,
  `clearBuffer`, `updateViewport`. `drawLayerQuad` and the "the engine owns
  layers" split were designed for this concept.
- **`Decal`** (C9) owns the `Sprite`'s GPU texture; `Decal.update()` re-uploads
  (CPU → GPU), `Decal.updateSprite()` reads back. **`DrawDecalService` /
  `DrawPartialDecalService`** build a `DecalInstance` from a position/scale/tint
  (no collector — the caller queues the instance).
- **`Engine`** (`C10a`/`C10b`): `frame: FrameInfo` (`elapsed`/`fps`/
  `frameCount`/`framebufferSize`), `input: InputState`, `config: WindowConfig`,
  the engine-owned `ResourceScope`, the loop and the private `renderFrame`
  (updateViewport → clear → prepareDrawing → present → awaitNextFrame).
- **`Driver`**: `pollEvents`, `windowSize`, `framebufferSize`, `isClosing`,
  `cancelClose`, `close`, `input`.
- **`ViewportFit` / `fitViewport`** — the pure letterbox; recomputed on a
  framebuffer-size change.

## Reference behavior

### olc v2.30 layers

- `LayerDesc` (`:1246`): `vOffset = {0,0}`, `vScale = {1,1}`, `bShow = false`,
  `bUpdate = false`, `pDrawTarget` (a `Renderable`: CPU `Sprite` + `Decal`),
  `vecDecalInstance`, `tint = WHITE`, `funcHook = nullptr`.
- `CreateLayer()` (`:2613`) allocates a `pDrawTarget` of `vScreenSize`;
  `olc_PrepareEngine` (`:4671`) creates layer 0 and forces `bUpdate`/`bShow`,
  then `SetDrawTarget(nullptr)` (draw target = layer 0).
- `SetDrawTarget(layer, bDirty)` (`:2563`) targets a layer and sets `bUpdate`;
  `SetDrawTarget(Sprite*)` (`:2549`) targets an arbitrary sprite (null resets to
  layer 0). `EnableLayer` (`:2573`) toggles `bShow`; `SetLayerOffset/Scale/Tint`
  write the layer; `SetLayerCustomRenderFunction` writes `funcHook`.
- `SetScreenSize(w, h)` (`:2504`) re-creates every layer's target at the new
  size, forces `bUpdate`, resets the draw target, flushes the backbuffer
  (non-real-window mode) and updates the viewport.
- `olc_CoreUpdate` (`:4874-4919`) render step: `UpdateViewport` → `ClearBuffer`
  → layer 0 forced `bUpdate`/`bShow` → `SetDecalMode(NORMAL)` →
  `PrepareDrawing` → for each layer in **reverse**: if `bShow`, either
  `funcHook()` or (`ApplyTexture` → if `!bSuspendTextureTransfer && bUpdate`,
  `Decal.Update()` and clear `bUpdate` → `DrawLayerQuad(vOffset, vScale, tint)`
  → flush `vecDecalInstance`) → `DisplayFrame`.
- `adv_FlushLayer` (`:4713`) is the imperative per-layer flush (`DrawLayer`).

### `main` addons / state

- `WithKGEState` (the god-interface rejected by `C10` decision 4): `mainResource`,
  `dimensionState`/`screenSize`/`invertedScreenSize`, `timeState`, `inputState`,
  `decalMode`/`pixelMode`/`decalStructure`/`suspendTextureTransfer`, `layers`/
  `targetLayerIndex`/`targetLayer`, `drawTarget`, `fontSheet`/`tabSizeInSpaces`.
- `WindowDependentAddon` (rejected by `C10` decision 4) forwarded all of it from
  a `Window`.
- `LayerDescriptor` (main, `renderer/LayerDescriptor.kt`): a `KGEInternalResource`
  owning a `Decal` draw target, with `offset`/`scale`/`show`/`update`/
  `decalInstances`/`tint`/`functionHook` and `resize(width, height)`.
- Addon inventory: `ClearAddon`, `DrawAddon` (`draw`), `DrawCircleAddon`,
  `FillCircleAddon`, `DrawLineAddon`, `DrawRectAddon`, `FillRectAddon`,
  `DrawTriangleAddon`, `FillTriangleAddon`, `DrawSpriteAddon` (`drawSprite`,
  `drawPartialSprite`), `DrawDecalAddon`, `DrawPartialDecalAddon`,
  `LayersAddon` (`createLayer`), `WindowManipulationAddon` (JVM:
  `changeWindowTitle`, `showWindow`, `hideWindow`, `windowShouldClose`).
  Every draw addon forwards `target = drawTarget ?: return` and `pixelMode`.
- `KotlinGameEngineBase` composes all the addons; `CallbacksAddon` is superseded
  by the `C10a` `Engine` callbacks and is not ported.

## Decisions closed at this touch-point (owner, 2026-09-15)

1. **Role shape: narrow roles + one cohesive holder each.** `HasWindow`
   (`WindowInfo`: `screenSize`, `invertedScreenSize`, `pixelSize`, `windowSize`,
   `framebufferSize`, `config`), `HasTime` (`frame: FrameInfo`), `HasInput`
   (`input: InputState`), `HasLayers` (the layer stack + `createLayer` +
   target selection), `HasDrawTarget` (`drawTarget`), `HasDrawModes`
   (`pixelMode`/`decalMode`/`decalStructure`/`suspendTextureTransfer`). The
   `Engine` implements them by exposing the holders; each addon requires only
   the role it uses. Exact member placement and package are micro-plan detail.
2. **Addon scope: everything except text.** `ClearAddon`, `DrawAddon`,
   `DrawCircleAddon`, `FillCircleAddon`, `DrawLineAddon`, `DrawRectAddon`,
   `FillRectAddon`, `DrawTriangleAddon`, `FillTriangleAddon`, `DrawSpriteAddon`,
   `DrawDecalAddon`, `DrawPartialDecalAddon`, `LayersAddon`,
   `WindowManipulationAddon` — the `Int2D` + raw-int overloads, carrying the
   required `mask` (circle), `pattern` (line/rect/triangle) and the existing
   parameter defaults. Text addons stay with `R6`.
3. **Layer model: full olc parity, including `funcHook`.** A public `Layer`
   owns a `Sprite` draw target and its `Decal`, with `offset`/`scale`/`show`/
   `update`/`tint`/`decalInstances`/`customRender` (the `funcHook`). The render
   step iterates the layers in reverse (olc `olc_CoreUpdate`): for a shown
   layer, `customRender(layer)` when set, else apply texture → update the decal
   when `update` and not `suspendTextureTransfer` → `drawLayerQuad` → flush the
   queued `DecalInstance`s. Layer 0 is always shown and updated. The engine
   creates layer 0 at startup.
4. **Draw-target surface: layer + arbitrary sprite + flag.** `setDrawTarget(layer,
   dirty = true)` and `setDrawTarget(sprite: Sprite?)` (null resets to layer 0),
   plus `suspendTextureTransfer` (a deferred GPU upload, `main`/olc parity).
   `drawTarget` is a `Sprite` because a `Decal` requires one.
5. **Window manipulation + dynamic resize: everything.** A `setScreenSize(width,
   height)` engine API (olc `SetScreenSize`): validate positive, re-create every
   layer target at the new size, force `update`, reset the draw target, recompute
   the viewport. **Revised 2026-09-15 (owner, micro-plan):** window manipulation
   is a **JVM-only addon**, as `main` had it (`WindowManipulationAddon` lived in
   `jvmMain` and was not in the common `KotlinGameEngineBase`); the common
   `Driver`/roles carry **no** `setTitle`/`show`/`hide`/close ops and the web
   target defines no such addon. The addon reaches the window through the
   `HasDriver` role and an internal JVM-only `GlfwWindow` capability.

## Derived model (micro-plan boundary)

- **`Layer`** (public): `val target: Sprite`, `val decal: Decal`, `var show`,
  `var update`, `var offset: Float2D`, `var scale: Float2D`, `var tint: Pixel`,
  `var customRender: ((Layer) -> Unit)?`, and the per-frame `decalInstances`
  queue. `Sprite`/`Decal` are created and closed as one unit.
- **Ownership:** the engine owns the layer list and registers each layer's
  `Sprite` + `Decal` in its `ResourceScope` (closed on destroy, before the
  driver); `setScreenSize` closes and re-allocates them.
- **Resize:** window resize continues to affect only `framebufferSize`/viewport
  (logical `screenSize` is config-derived); `setScreenSize` is the explicit
  logical-resolution change. `WindowConfig.screenWidth/Height` are the initial
  values, not a cap.
- **Window control seam:** the engine exposes the platform `Driver`
  (`@KGESensitiveAPI`) so the JVM-only `WindowManipulationAddon` reaches the
  window through a JVM-only `GlfwWindow` capability on the driver; the web target
  defines no such addon and the common `Driver` surface is unchanged.
- **Render step** stays in the engine's `renderFrame`, extended with the layer
  loop; `clearBuffer(BLACK, depth = true)` and the viewport fit are unchanged.

## Out of scope / deferred

- Elaborate text, atlas and blitting (`R6`); the `main` bitmap font.
- `vecGPUTasks` (olc/`DoGPUTask`) and 3D geometry — the 2D `DecalInstance` only.
- `mainResource` exposure as public API (the `Driver`/`Renderer` own it).
- `bRealWindowMode` (screen size tracks the window) — KGE has no such mode.
- Multi-engine and any global-handle policy.
