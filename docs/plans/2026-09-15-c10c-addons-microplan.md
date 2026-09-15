# Engine addons/layers micro-plan (C10c, interactive execution)

**Date:** 2026-09-15. Base touch-point:
`docs/plans/2026-09-15-c10c-engine-touchpoint.md` (decisions closed 2026-09-15)
over the `C10` touch-point (decision 4) and the `C10a`/`C10b` closes
(`docs/decisions/phase-1/24-engine-c10a.md`, `25-input-c10b.md`) plus the
benchmark carry-forward (`26-fps-benchmark.md`). Third and last `C10` session:
the ISP roles, the addon mixins, layers/content drawing and window manipulation.
Text is `R6`; 3D/GPU-tasks and real-window mode are out of scope.

## Working mode

- **No subagents.** One step = test (red) → implement (green on jvm+js+wasmJs) →
  mark. Doubts resolved in-session with the owner.
- **Oracle.** `RecordingDriver` (lifecycle) + `RecordingGLService` (the C10a
  render-order oracle) + the pure `LayerStack`/`Layer` unit tests. Addons are
  pinned by real `Sprite` targets (pixel assertions) and by the queued
  `DecalInstance`s; the real backends are smoke-tested (JVM hidden GLFW, web
  canvas), as in `C10a`.
- **Confinement.** Layers, draw targets and the render step run on the engine
  thread (`requireEngineThread`); the addons are called from `onUserUpdate`.

## Touch-point decisions closed (owner, 2026-09-15)

1. **Narrow roles + one cohesive holder each.** `HasWindow` (`WindowInfo`),
   `HasTime` (`frame: FrameInfo`), `HasInput` (`input: InputState`),
   `HasLayers`, `HasDrawTarget`, `HasDrawModes`, plus `HasDriver` for the
   platform driver (added here; the touch-point listed the state roles). The
   engine implements them; each addon requires only the roles it uses.
2. **Addons: everything except text.**
3. **Layers: full olc parity, including `customRender` (olc `funcHook`).**
4. **Draw target: layer + arbitrary `Sprite` + `suspendTextureTransfer`.**
5. **Window manipulation + `setScreenSize`, all of it.** **Revised
   2026-09-15 (owner, micro-plan):** window manipulation is a **JVM-only addon**
   (`jvmMain`), as `main` had it; the common `Driver`/roles gain no
   `setTitle`/`show`/`hide`/close ops and the web target defines no such addon.
   The addon reaches the window through `HasDriver` and an internal JVM-only
   `GlfwWindow` capability. `setScreenSize` stays a common engine API.

## Contract

- **`WindowInfo`** (public, read-only): `screenSize`, `pixelSize`,
  `invertedScreenSize`, `windowSize` (logical), `framebufferSize` (physical),
  `config`. `screenSize` mutates only through `Engine.setScreenSize`;
  `windowSize`/`framebufferSize` refresh each frame from the driver.
- **`HasTime`** — `val frame: FrameInfo`.
- **`HasInput`** — `val input: InputState`.
- **`HasDrawModes`** — `var pixelMode: Pixel.Mode`, `var decalMode:
  Decal.Mode`, `var decalStructure: Decal.Structure`, `var
  suspendTextureTransfer: Boolean`.
- **`HasDrawTarget`** — `var drawTarget: Sprite?` and `fun setDrawTarget(index:
  Int, dirty: Boolean = true)`; setting `drawTarget = null` selects layer 0 and
  resets `targetIndex` to 0 (olc `SetDrawTarget(nullptr)`), while
  `setDrawTarget(index, dirty)` selects that layer and marks it dirty (olc
  `SetDrawTarget(layer, bDirty)`). A non-null `drawTarget` set directly leaves
  `targetIndex` unchanged (olc `SetDrawTarget(Sprite*)`). An out-of-range index
  fails fast before any mutation — a recorded divergence (olc silently ignores
  it, `:2563`).
- **`HasLayers`** — `val layers: LayerStack`, available only while the engine
  runs (fails fast before `start()`/after teardown); `createLayer()`, target
  selection and per-layer mutation go through the stack/`Layer`.
- **`HasDriver`** — `@KGESensitiveAPI val driver: Driver` (the platform driver,
  available while the engine runs), the common seam the JVM-only window addon
  reaches the window through. The common `Driver` keeps every current op — no
  title/show/hide/close methods; the JVM `GlfwWindow` capability (title/show/
  hide/close) is declared and implemented only in `jvmMain`.
- **`Layer`** (public): `val target: Sprite`, `val decal: Decal`, `var show`,
  `var update`, `var offset: Float2D`, `var scale: Float2D`, `var tint: Pixel`,
  `var customRender: ((Layer) -> Unit)?`, and the internal per-frame
  `decalInstances` queue; `offset`/`scale` default to olc's `{0,0}`/`{1,1}`.
  When `customRender` is set the layer's queued `decalInstances` are **not**
  flushed (olc's `funcHook` branch, `:4909-4913`).
- **`LayerStack`** (public `KGEInternalResource`, owning its layers through the internal
  `CompositeResource` — C2's deferred `KGEInternalResource` composite): created
  with layer 0, so a live stack is never empty and emptiness is the closed state
  (owner rule); `size`, `operator get(index)`, `targetIndex`/`target`,
  `createLayer(): Int`, `resizeAll(width, height)` (`internal`), `close()`.
  Registered in the engine's `ResourceScope` and built inside `start()` with the
  context current; each layer allocates a `Sprite` + `Decal` of the screen size.
  `createLayer` and `resizeAll` use `letClosingIfFailed`.
- **Addons** — the `main` inventory minus text, forwarding to `Rasterizer` /
  the decal services with `target = drawTarget ?: return` and `pixelMode`:
  `ClearAddon`, `DrawAddon`, `DrawCircleAddon`, `FillCircleAddon`,
  `DrawLineAddon`, `DrawRectAddon`, `FillRectAddon`, `DrawTriangleAddon`,
  `FillTriangleAddon`, `DrawSpriteAddon`, `DrawDecalAddon`,
  `DrawPartialDecalAddon`, `LayersAddon`, and the JVM-only
  `WindowManipulationAddon`.

## Kotlin/design notes

- `LayerStack` is the resource: the scope closes it LIFO before the driver, and
  it closes each layer's `Sprite` + `Decal` as one unit. `resizeAll` replaces the
  internals without changing the stack's identity.
- `drawTarget`/layer targets are `Sprite` (a `Decal` needs one); `Rasterizer`
  accepts any `Pixmap.Mutable`, so `Sprite` satisfies it.
- `customRender` is `((Layer) -> Unit)?`, not olc's capture-only
  `std::function<void()>`: passing the layer avoids the forwarding-all shape
  `C10` rejected.
- No `Long`/`Duration` enters the layer hot path (log #24 constraint not
  triggered).

## Render step (olc `olc_CoreUpdate`, `:4874-4919`)

```
fit = cached fit (recomputed on framebuffer/screen change)
Renderer.updateViewport(fit.position, fit.size)
Renderer.clearBuffer(BLACK, depth = true)
layers[0].show = true; layers[0].update = true
decalMode = Decal.Mode.NORMAL          // olc :4881 / main coreUpdate, per frame
Renderer.prepareDrawing(scope)
for layer in layers reversed:
    if layer.show:
        if layer.customRender != null: layer.customRender(layer)
        else:
            Renderer.applyTexture(layer.decal.texture)
            if !suspendTextureTransfer && layer.update:
                layer.decal.update()      // Decal.Update (CPU -> GPU)
                layer.update = false
            Renderer.drawLayerQuad(scope, layer.offset, layer.scale, layer.tint)
            for instance in layer.decalInstances: Renderer.drawDecal(scope, instance)
            layer.decalInstances.clear()
driver.present()
driver.awaitNextFrame()
```

`present` moves after the layer loop (it is the last thing before
`awaitNextFrame`; today it is before them).

## Resume tracker

- [x] **0.** Window/time/input roles + `WindowInfo`; `Engine` implements them
- [x] **1.** `Layer` + `LayerStack` (created with layer 0, allocation, target
  selection, resize, close in the scope)
- [x] **2.** Draw target/modes + render step with layers (reverse order,
  show/update, decal flush, `customRender`, `suspendTextureTransfer`,
  per-frame `decalMode` reset)
- [x] **3.** Raster addons (clear, draw, fill/draw rect/circle/triangle, line,
  sprite/partial sprite)
- [x] **4.** Decal addons (`drawDecal`, `drawPartialDecal`)
- [x] **5.** `HasDriver` + JVM `GlfwWindow` capability +
  `WindowManipulationAddon` (JVM-only)
- [x] **6.** `LayersAddon` + `Engine.setScreenSize` (resize layers, reset
  target, invalidate fit)
- [x] **7.** Real-backend smoke (JVM hidden GLFW + web canvas)
- [x] **8.** Gate + two-axis review + decisions-log close

## Steps

### 0. Window/time/input roles + `WindowInfo`

- **Tests:** `ScriptedEngine` exposes `window` (config-derived `screenSize`,
  `pixelSize`, `invertedScreenSize`, plus `windowSize`/`framebufferSize`
  refreshed from the driver each frame), `frame` and `input`; each is the one
  instance the loop updates. `window.screenSize`/`pixelSize` reflect the config.
- **Files:** `engine/Has{Window,Time,Input}.kt`, `engine/WindowInfo.kt`; move the
  `screenSize`/`pixelSize` fields off `Engine` into `WindowInfo`.
- **Decided:** roles live in `dev.staticsanches.kge.engine`; holders are public
  read-only (`internal set`).

### 1. `Layer` + `LayerStack` + `HasLayers`

- **Tests (unit):** `createLayer` returns the next index and allocates a
  screen-sized `Sprite`+`Decal`; `target`/`targetIndex` select; `resizeAll`
  replaces every layer's internals and forces `update`; `close` closes every
  layer's `Sprite` and `Decal` (leak-reporter/lifecycle assertion).
- **Tests (engine):** `start()` builds the stack (with layer 0) inside the
  scope; `engine.layers` is available only while running; the scope closes the
  stack before the driver.
- **Files:** `engine/layer/Layer.kt`, `engine/layer/LayerStack.kt`,
  `engine/HasLayers.kt`.
- **Decided:** `Layer` is a `KGEInternalResource` (engine-managed, sensitive
  close); the stack owns and closes the layers as one composite resource
  (`CompositeResource`). `decalInstances` is internal.

### 2. Draw target/modes + render step with layers

- **Tests (recording GL):** the engine exposes `drawTarget` (initialized to
  layer 0's sprite; `drawTarget = null` resets to layer 0 and `targetIndex` 0;
  `setDrawTarget(i)` selects layer `i` and marks it dirty) and the draw modes;
  order is
  updateViewport → clearBuffer → prepareDrawing → layer loop (last layer first)
  → present → awaitNextFrame;
  layer 0 is forced `show`/`update`; a hidden layer is skipped; a dirty layer
  uploads (`texImage2D`) then draws its quad and clears `update`; an
  up-to-date layer draws its quad without uploading; `suspendTextureTransfer`
  skips the upload; queued `DecalInstance`s are drawn after the quad, in order,
  and the queue is emptied; `customRender` replaces the quad+upload for its
  layer; a layer with `show = false` draws nothing; `decalMode` is reset to
  `NORMAL` on every frame before `prepareDrawing`, so a user-set mode never
  leaks across frames (olc `:4881`, `main` `coreUpdate`).
- **Files:** `engine/Has{DrawTarget,DrawModes}.kt`, `Engine.kt`.
- **Decided:** the `present` move above; the fit is recomputed when the
  framebuffer *or* the screen size changes.

### 3. Raster addons

- **Tests:** with a real `Sprite` target, each addon paints the expected pixels
  and defaults (`Colors.WHITE`, `CircleOctantMask.ALL`, `LinePattern.Filled`);
  `pixelMode` reaches the rasterizer (e.g. an `Alpha` blend); a `null`
  `drawTarget` is a no-op; both the `Int2D` and the raw-`Int` overloads exist.
- **Note (mapping):** `drawSprite` forwards the whole sprite to `BlitService`
  (`blit`); `drawPartialSprite` maps `main`'s **inclusive** diagonals to
  `blitRegion`'s `origin` + `size` (`size = |end - start| + 1`, min corner as
  origin). A region outside the sprite fails fast (`blitRegion`'s C5 contract)
  instead of `main`/olc's per-pixel transparent out-of-bounds fill — a recorded
  divergence (an out-of-range partial region is a caller error; `main`'s fill
  was a side effect of its unchecked per-pixel read). `flip` is `Pixmap.Flip`.
- **Files:** `engine/addon/{Clear,Draw,DrawCircle,FillCircle,DrawLine,DrawRect,
  FillRect,DrawTriangle,FillTriangle,DrawSprite}Addon.kt`.
- **Decided (owner, 2026-09-15):** `ClearAddon` ports all three `main` overloads.
  `clear(pixel)` forwards to `Pixmap.Mutable.clear`; `clear(pixelByXY)` fills
  every cell from the function; `clear(pixels: Iterable<Pixel>)` overwrites the
  cells in row-major order with the iterable's pixels until it is exhausted,
  leaving the rest untouched (`main`'s buffer-prefix semantics). olc defines only
  `Clear(Pixel)` (`:1516`), so the latter two are a recorded `main` extension
  kept on owner request.

### 4. Decal addons

- **Tests:** `drawDecal`/`drawPartialDecal` append exactly one `DecalInstance`
  to the target layer with the current `decalMode`/`decalStructure` and the
  `screenSize` viewport (the scale default is `Float2D(1f, 1f)` — `Float2D` has
  no `oneByOne` constant); `drawPartialDecal` forwards `sourcePosition`/`sourceSize`;
  the instance carries the decal's texture (drawn by the render step, not the
  addon).
- **Files:** `engine/addon/DrawDecalAddon.kt`,
  `engine/addon/DrawPartialDecalAddon.kt`.
- **Decided:** the addon builds via `DrawDecalService`/`DrawPartialDecalService`
  and queues on `layers.target.decalInstances` (olc's `vecDecalInstance`).

### 5. Window manipulation (JVM-only addon)

- **Tests (jvmTest):** a fake `Driver` implementing the JVM `GlfwWindow`
  capability records `changeWindowTitle`/`showWindow`/`hideWindow`;
  `windowShouldClose` reads and writes the driver's close request; delegating
  through the `HasDriver` seam (no GLFW statics in the addon).
- **Tests (real backend, smoke):** the JVM hidden GLFW window accepts a title
  change and a close request (skipped on macOS as in `C10a`).
- **Files:** `engine/HasDriver.kt` (common), `jvmMain` `GlfwWindow` + `GlfwDriver`
  implementation, `jvmMain/.../addon/WindowManipulationAddon.kt`, `jvmTest` test.
- **Decided:** the addon is JVM-only (`jvmMain`), as `main`'s; the web target has
  no `WindowManipulationAddon` and the common `Driver` keeps its surface (no
  title/show/hide ops). The GLFW calls live on the JVM driver behind an internal
  JVM `GlfwWindow` capability the addon casts to (not GLFW statics in the addon),
  so the addon is unit-testable; `main` parity is behavioral.
  `windowShouldClose` reads/writes the same GLFW flag the engine's
  `isClosing`/`cancelClose` observe.

### 6. `LayersAddon` + `setScreenSize`

- **Tests (`LayersAddon`):** `createLayer()` returns the next index and adds a
  screen-sized layer (delegates to `LayerStack.createLayer`).
- **Tests (`setScreenSize`):** a non-positive size throws; a valid one updates
  `window.screenSize`, resizes every layer target and forces `update`, resets
  `drawTarget` to layer 0 and `targetIndex` to 0 (olc `SetDrawTarget(nullptr)`
  inside `SetScreenSize`), and the next frame's viewport re-fits to the new
  screen size (the cached fit is invalidated).
- **Files:** `engine/addon/LayersAddon.kt`, `Engine.kt`.
- **Decided:** `setScreenSize` requires the engine thread and runs while the
  loop is active; window resize still only affects `framebufferSize`/viewport.

### 7. Real-backend smoke

- **Tests:** JVM hidden GLFW (skipped on macOS as in `C10a`) and web canvas
  create a layer, draw a raster primitive, composite a frame and read the
  framebuffer back; assert the composited layer is visible (or, conservatively,
  that the frame executes the layer path).
- **Decided:** smoke only; no cross-backend pixel-exactness.

### 8. Gate + review + close

- **Gate:** `./gradlew build --rerun-tasks` green (ktlint via `check`).
- **Review:** two-axis (Standards + Spec) over the staged diff, three sources of
  truth (olc parity, no unjustified `main` regression, Kotlin realization);
  resource audit of every layer allocation/close path (including construction
  failures); every public parameter has an observable effect; narrowest
  visibility. Decisions-log close entry.

## Files

New: `engine/Has*.kt`, `engine/WindowInfo.kt`, `jvmMain/.../engine/GlfwWindow.kt`,
`engine/layer/{Layer,LayerStack}.kt`,
`engine/addon/*Addon.kt`, `jvmMain/.../addon/WindowManipulationAddon.kt`,
`commonTest` layer/role/addon tests, real-backend smoke in `jvmTest`/`webTest`.
Edited: `Engine.kt`, `DriverServiceJvm.kt`, `EngineSmokeTest.kt`.

Out of scope: text/atlas (`R6`); `vecGPUTasks`/3D; real-window mode;
multi-engine.

## Open items (owner)

- None. `ClearAddon` keeps all three overloads and the window addon is JVM-only
  (owner, 2026-09-15).
