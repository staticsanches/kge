## 2026-09-15 — Engine C10c (addons/roles/layers): touch-point + close

The third and last `C10` session (`C10a` loop/window/time → `C10b` input →
**`C10c` addons/roles/layers**); it closes with its own gate and log entry.
Design material: `docs/plans/2026-09-15-c10c-engine-touchpoint.md` and
`docs/plans/2026-09-15-c10c-addons-microplan.md` (written just-in-time). It
realizes `C10` decision 4 (ISP roles over narrow interfaces, no central state
contract): the roles, the addon mixins, layers/content drawing, window
manipulation and `setScreenSize`. Text is `R6`.

### Roles and holders

- **Narrow roles, engine as composition root.** `HasWindow` (`WindowInfo`:
  `screenSize`/`pixelSize`/`invertedScreenSize`/`windowSize`/`framebufferSize`/
  `config`), `HasTime` (`frame: FrameInfo`), `HasInput` (`input: InputState`),
  `HasLayers` (`layers: LayerStack`), `HasDrawTarget`, `HasDrawModes`,
  `HasDriver`. `Engine` implements them; each addon requires only the roles it
  uses. Holders are public read-only (`WindowInfo.screenSize` mutates only
  through `Engine.setScreenSize`).
- **`HasDriver`** exposes the platform driver (`@KGESensitiveAPI`) so the
  JVM-only window addon reaches the window; the driver is available only while
  the engine runs (`requireEngineThread` semantics), and `engineThreadId` is
  cleared at teardown.

### Layers and the render step

- **`Layer`** owns a screen-sized `Sprite` draw target and its `Decal`, with
  `show`/`update`/`offset`/`scale`/`tint` (`{0,0}`/`{1,1}`/`WHITE`) and
  `customRender: ((Layer) -> Unit)?` (olc's `funcHook`, takes the layer rather
  than a capture-only closure); the per-frame `decalInstances` queue is
  `internal`. **`LayerStack`** is the `KGEResource`: it allocates each layer
  through `letClosingIfFailed`, is registered in the engine-owned `ResourceScope`
  (closing before the driver), fails fast on the `size`/`get`/`target` reads
  after `close`, and keeps `resizeAll` and the `targetIndex` setter `internal`
  (the `targetIndex` scalar read stays unguarded).
- **Render step** (`Engine.renderFrame`, olc `olc_CoreUpdate` `:4874-4919`):
  `updateViewport` → `clearBuffer(BLACK, depth)` → force layer 0 `show`/`update`
  → **reset `decalMode` to `NORMAL`** → `prepareDrawing` → layers in **reverse**
  (when `customRender` is set call it; else `applyTexture` → upload the decal
  when `update` and not `suspendTextureTransfer` → `drawLayerQuad` → flush the
  queued `DecalInstance`s) → `present` → `awaitNextFrame`. `present` moved after
  the layer loop (olc `DisplayFrame` after the layer loop).

### Addons

- **Everything except text**: `ClearAddon`, `DrawAddon`, `DrawCircleAddon`,
  `FillCircleAddon`, `DrawLineAddon`, `DrawRectAddon`, `FillRectAddon`,
  `DrawTriangleAddon`, `FillTriangleAddon`, `DrawSpriteAddon`,
  `DrawDecalAddon`, `DrawPartialDecalAddon`, `LayersAddon`. Each is a set of
  defaults over the roles, forwarding `target = drawTarget ?: return` and
  `pixelMode`, with `main`'s parameter defaults (`Colors.WHITE`,
  `CircleOctantMask.ALL`, `LinePattern.Filled`). The decal addons build through
  `DrawDecalService`/`DrawPartialDecalService` and queue on `layers.target`
  (olc's `vecDecalInstance`); the render step draws them.
- **`WindowManipulationAddon` is JVM-only** (`jvmMain`, as `main`): the common
  `Driver`/roles gain no title/show/hide ops and the web target defines no such
  addon. The GLFW calls live on the JVM driver behind an internal `GlfwWindow`
  capability the addon casts to with an explicit error (the addon is
  unit-testable; `main` parity is behavioral). `windowShouldClose` reads/writes
  the same GLFW flag the engine's `isClosing`/`cancelClose` observe.
- **`setScreenSize(width, height)`** (olc `SetScreenSize`): rejects a
  non-positive size, requires the engine thread, resizes every layer target
  (`resizeAll` forces `update`), then updates `screenSize`, resets the draw
  target to layer 0 and invalidates the cached fit. There is no real-window mode
  (screen size tracks the window) — out of scope.

### Accepted divergences and limitations

- **`drawPartialSprite` region**: `main`'s inclusive diagonals map to
  `blitRegion`'s `origin`+`size`; a region outside the sprite fails fast
  (`blitRegion`'s C5 contract) rather than `main`/olc's per-pixel transparent
  out-of-bounds fill — an out-of-range region is a caller error.
- **`ClearAddon` keeps all three `main` overloads** (`clear(pixel)`,
  `clear(pixelByXY)`, `clear(pixels)`), owner-requested; olc defines only
  `Clear(Pixel)` (`:1516`). `clear(pixels)` overwrites the surface in row-major
  order until the iterable is exhausted, leaving the rest (`main` semantics).
- **Out-of-range `setDrawTarget(index)` fails fast** before mutating; olc
  silently ignores it (`:2563`). KGE's fail-fast is deliberate.
- **`customRender` does not flush `decalInstances`** (olc's `funcHook` branch
  skips the flush, `:4909-4913`); queued decals accumulate while a custom render
  is set.
- **`DrawRectAddon`'s raw-`Int` overload** calls the rect service; `main`'s
  accidentally called `drawLine` (a diagonal) — corrected to match its `Int2D`
  overload and olc's ring.
- **Smokes pin the real-context execution** (layer upload consumed, primitive in
  the CPU target, live/released context) and do not read the default framebuffer
  after `present` (double-buffered swap makes it undefined); the deterministic
  pixel readback lives in the C9 renderer smoke.
- Layer mutation is engine-thread-confined (`setDrawTarget`/`setScreenSize`
  guard); direct `Layer` property mutation is the caller's responsibility.
- One engine per process; a second `start()` after teardown fails fast.

### Review and gate

Two-axis review (Standards + Spec, fresh sub-agents; reports
`.opencode/reviews/c10c-addons-{standards,spec}.md`): Spec PASS (4 minor),
Standards FAIL (2 Important — a public `LayerStack.targetIndex` setter that
could diverge from `Engine.drawTarget`, and mutate-before-validate in
`setDrawTarget`/`setScreenSize` — plus 6 minor). Fixed in one round: the setter
and `resizeAll` narrowed to `internal`, validate-before-mutate, engine-thread
guards on the draw-target mutators, fail-fast accessors and post-run state, the
checked `GlfwWindow` cast, KDoc updates, closed test stacks, and the missing
parity tests (out-of-range `setDrawTarget`, `customRender` no-flush). Scoped
re-review clean. Gate: `./gradlew build --rerun-tasks` green (JVM + js browser +
wasmJs browser + ktlint + assemble/metadata).

### Correction (2026-09-15) — composite resource helper (owner Hunk review)

The owner's Hunk review questioned `LayerStack`'s hand-rolled
`mutableListOf<Layer>()` + `closed` boolean ("wouldn't a `ResourceWrapper` be
better?"). Ruling: `ResourceWrapper` is a **single-handle** wrapper (one owned
buffer/texture behind a fail-fast accessor, C2 log #08), while `LayerStack` is a
**composite**. The composite mechanism `main` used — `KGEInternalResource` + a
bound-resources list + close-all-with-suppressed — was explicitly deferred by C2
to its consumer, i.e. this engine round, and was not ported until now. Ported:
`KGEInternalResource` (the engine-managed marker with a `@KGESensitiveAPI
close`), an internal `CompositeResource<T : KGEResource>` (ordered elements,
idempotent close in reverse add order), and a shared `closeAll` close-error
collector used by both `CompositeResource` and `ResourceScope` (deduplicating
the suppressed-failure loop; `ResourceScope`'s LIFO order and behavior are
unchanged). `Layer` is now a `KGEInternalResource`.

**Second review point (owner): the empty list is the closed state, and the
engine always has a layer.** `CompositeResource` carries no `closed` flag: it is
created with its first element, so a live composite is never empty, and
`close()` clears the list — emptiness *is* closed (accessors fail fast when
empty; `size` is the live count). `LayerStack` is therefore constructed with
layer 0 and the engine builds it inside `start()` with the context current
(GPU allocation needs one), registering it in the `ResourceScope`;
`Engine.layers` is available only while the run is active (fails fast before
`start()`/after teardown, like `dispatcher`/`driver`), so a layer is always
available to the running app. `LayerStack.size` still fails fast once closed.
Behavior change from the first pass: layer 0 exists from stack construction, and
the stack is built in `start()` instead of at engine construction.

### Carry-forward

- Elaborate text (`R6`): shaping, rasterization, atlas, blit; no `DrawString`
  addon until then.
- `vecGPUTasks`/3D geometry; `multiDrawArrays` batching (benchmark-gated); the
  `mainResource` public API; real-window mode; multi-engine.
