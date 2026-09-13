# Engine loop/window/time micro-plan (C10a, interactive execution)

**Date:** 2026-09-13. Touch-point decisions in
`docs/plans/2026-09-13-c10-engine-touchpoint.md`. First of the three engine
sessions (`C10a` → `C10b` input → `C10c` addons). This one delivers the loop and
its lifecycle: the `Driver` seam, window + context, `TimeService`, frame
accounting, the user callbacks and the viewport wiring — everything needed to
open a window and run a timed frame loop on JVM and web. Input, the addon mixins
and layer/content drawing are `C10b`/`C10c`.

## Working mode

- **No subagents.** One step = test (red) → implement (green on jvm+js+wasmJs) →
  mark. Doubts resolved in-session with the owner.
- **Oracle.** A common `RecordingDriver` (implements `Driver`, records the
  frame lifecycle, ends after N frames) plus the C9 `RecordingGLService`; the
  real-GL smoke pins the platform window path. Time is faked through the
  overridable `TimeService`.
- **Confinement.** The loop runs on the calling (process main) thread; nothing
  in the loop hops off it. `Dispatchers.Default`/`IO` are never used for frame
  work.

## Contract

- **`TimeService : KGEOverridable`** — `fun elapsed(): Duration`, monotonic, one
  unit (the stdlib `Duration`). Default is a **common** implementation over
  `TimeSource.Monotonic` (no `expect`/`actual`: the stdlib is multiplatform and
  already monotonic on every target). Overridable process-wide for a virtual
  clock; facade `object Time : TimeService by TimeService`.
- **`FrameInfo`** — read-only per-frame snapshot: `elapsed: Duration`,
  `fps: Int`, `frameCount: Long`. The engine holds a mutable accumulator and
  publishes one snapshot per frame (focused state holder; no god-state).
- **`WindowConfig`** — `screenWidth`/`screenHeight` (logical points),
  `pixelWidth`/`pixelHeight` (art zoom, default 1), `title`, `resizable`,
  `vsync`, `fullScreen`, `cohesion` (opt-in). Non-positive sizes fail fast.
- **`Driver : GpuDevice`** — adds `pollEvents()`, `awaitNextFrame()`,
  `windowSize(): Int2D` (points), `framebufferSize(): Int2D` (physical px),
  `isClosing(): Boolean`, `close()`. `makeCurrent`/`present` come from
  `GpuDevice`. JVM backend over GLFW (window + GL 3.3 core + `glfw_async` on
  macOS); web backend over a canvas + WebGL2 (`present` no-op, `awaitNextFrame`
  = `requestAnimationFrame`).
- **`DriverService : KGEOverridable`** — `fun create(config: WindowConfig): Driver`.
  Engine-fixed platform behavior with a consumer override, the
  `MemoryAllocatorService` shape: default `internal expect val
  driverServiceDefault` (JVM GLFW, web canvas), resolved per call. Tests fake
  the window through it; a web app with its own canvas overrides it with the
  platform service bound to that holder (a public web service), so the engine
  has no constructor factory parameter.
- **`Engine`** — abstract, user-subclassed (olc model); open suspend callbacks
  `onUserCreate(): Boolean`, `onUserUpdate(elapsed: Duration): Boolean`,
  `onUserDestroy(): Boolean`; `val frame: FrameInfo`; `val dispatcher`
  (confined) + `requireEngineThread()`; `suspend fun start()`; `fun stop()`.
  One engine per process.

## Loop algorithm (parity with olc `EngineThread` + `olc_CoreUpdate`)

```
start():  driver = DriverService.create(config) ; driver.makeCurrent() ; scope = ResourceScope()
          Renderer.createResources(driver, scope)
          if (!onUserCreate()) -> abort
          active = true
          while (active):
              while (active && !stopRequested && !driver.isClosing()):
                  elapsed = accumulator.tick(Time.elapsed())
                  driver.pollEvents()
                  if (!onUserUpdate(elapsed)) active = false
                  renderFrame(scope, driver)
              if (!onUserDestroy()) active = true      // bAtomActive restart, no re-create
          finally: scope.close() ; driver.close() ; resetAll()
```

`renderFrame`: `driver.makeCurrent()` → `Renderer.updateViewport(letterbox)` →
`Renderer.clearBuffer(BLACK, depth = true)` → `Renderer.prepareDrawing(scope)` →
`driver.present()` → `driver.awaitNextFrame()`. Content drawing (layers/decals)
is `C10c`; this step is clear + present.

FPS accounting (olc exact): accumulate `elapsed` into a `>= 1.seconds` window,
publish `fps = frameCount` then subtract `1.seconds` (drift carry, not reset).

## Resume tracker

- [ ] **0.** Contract skeleton + test doubles (`RecordingDriver`, `FakeTimeService`) + driver-factory/web-holder decision
- [ ] **1.** `TimeService` (common default) + facade + override test
- [ ] **2.** Frame accumulator (pure: elapsed/fps/frameCount, `>= 1s` carry)
- [ ] **3.** `WindowConfig` + validation
- [ ] **4.** `Driver` seam + `RecordingDriver` + platform backends (JVM GLFW, web canvas)
- [ ] **5.** `Engine.start()` lifecycle: create/makeCurrent/scope/`createResources`/teardown order
- [ ] **6.** Callbacks + `bAtomActive` restart semantics + `stop()`
- [ ] **7.** Letterbox viewport (framebuffer px, fractional scale) + `updateViewport` wiring
- [ ] **8.** Frame render step (clear/prepare/present/await) + `FrameInfo` publication
- [ ] **9.** Real-GL smoke (JVM hidden window / web canvas) — run K frames; macOS skip
- [ ] **10.** Gate + two-axis review + decisions-log close

## Steps

### 0. Contract skeleton + test doubles

- **Tests:** `RecordingDriver` records `makeCurrent`/`pollEvents`/`present`/
  `awaitNextFrame` and reports a scripted `isClosing`; `FakeTimeService`
  advances by a fixed `Duration` per `elapsed()`.
- **Decided:** `DriverService : KGEOverridable` creates the driver (no
  constructor factory); the callbacks are open methods on an abstract `Engine`;
  layers/content are `C10c`.

### 1. `TimeService`

- **Tests:** the default is monotonic across calls; an override (virtual clock)
  changes `Time.elapsed()` process-wide and resets after the test (module kotest
  config already resets overrides).
- **Files:** `commonMain/.../time/TimeService.kt` (+ default).
- **Decided:** one unit (`Duration`); FPS is NOT computed here (loop owns it) —
  the `main` `fpsUpdater` coupling stays rejected.

### 2. Frame accumulator

- **Tests:** `elapsed` is the delta between ticks; `frameCount` accumulates;
  `fps` publishes when the window crosses `1.seconds` and carries the drift
  (`frameTimer -= 1.seconds`); the first frame does not publish a fake FPS (the
  `main` `frameTimer = 1.0` artifact is dropped).
- **Files:** pure type in `commonMain/.../time/` (internal).

### 3. `WindowConfig` + validation

- **Tests:** non-positive `screenWidth`/`screenHeight`/`pixelWidth`/
  `pixelHeight` fail fast (olc `Construct` `FAIL`).

### 4. `Driver` seam + backends

- **Tests:** `RecordingDriver` satisfies the interface; the JVM/web real
  backends are smoke-tested in step 9.
- **Files:** `commonMain/.../engine/{Driver,DriverService}.kt`; `jvmMain` GLFW
  backend + service default; `webMain` canvas/WebGL2 backend + service default.
- **Decided:** the driver owns window + context (they are the same GLFW object
  on JVM); `GpuDevice` (C9) is unchanged.

### 5. `Engine.start()` lifecycle

- **Tests:** startup order — driver created → `makeCurrent` →
  `Renderer.createResources` → `onUserCreate`; teardown order — `scope.close()`
  **before** `driver.close()` (context alive for GPU release), then
  `resetAll()`; a failing `onUserCreate` (false) skips the loop and still tears
  down; a construction failure closes what was allocated (`letClosingIfFailed`).
- **Decided:** the engine owns the `ResourceScope`; `resetAll` runs on destroy.

### 6. Callbacks + restart

- **Tests:** `onUserUpdate` returning false ends the inner loop; `onUserDestroy`
  false restarts the inner loop without re-running `onUserCreate` and without
  re-creating the window; `onUserDestroy` true exits; `stop()` terminates from
  outside.
- **Decided:** olc parity — `Boolean` returns + `bAtomActive` restart.

### 7. Letterbox viewport

- **Tests (pure):** `screenSize*pixelSize` aspect fitted into `framebufferSize`
  in physical pixels, centered, allowing fractional scale; the coherence flag
  snaps to an integer floor scale.
- **Decided:** framebuffer px for the GL viewport; mouse mapped back later
  (`C10b`) through the same view.

### 8. Frame render step + `FrameInfo`

- **Tests (recording driver + `RecordingGLService`):** per frame the order is
  `makeCurrent`, `updateViewport`, `clearBuffer`, `prepareDrawing`, `present`,
  `awaitNextFrame`; `frame` publishes `elapsed`/`fps`/`frameCount`; the loop runs
  the requested number of frames on the `RecordingDriver` and stops.
- **Decided:** content/layers out of scope — clear + present only.

### 9. Real-GL smoke

- **Tests:** open the real platform window (JVM hidden GLFW + GL 3.3, web
  WebGL2 canvas), run K frames of the loop with the real `Driver`, assert the
  loop advanced and tore down; macOS skips the JVM path (`glfw_async`).
- **Decided:** smoke only — no pixel-exactness across backends.

### 10. Gate + review + close

- **Gate:** `./gradlew build --rerun-tasks` green (ktlint via `check`). Leak
  audit of every allocate/close path and construction-failure branch
  (`letClosingIfFailed`); no parameter without an observable effect. Two-axis
  review (Standards + Spec, three sources of truth). Decisions-log close entry.

## Resolved micro-plan decisions (owner, 2026-09-13)

- **Driver creation: `DriverService : KGEOverridable`** — the
  `MemoryAllocatorService` shape (engine-fixed platform behavior, consumer
  override, `expect`/`actual` default), not a constructor factory. Uniform
  override for tests and the web holder; no provisional API. A web app with its
  own canvas overrides it with the public platform service bound to that holder.
- **Callbacks: abstract `Engine` with open suspend callbacks** (olc model;
  callbacks reach protected state). The `C10c` mixins extend it.
- **Layers/content: `C10c`** (with the addons). `C10a` stays clear + present.
- **Plan scope: `C10a` only** for now; `C10b`/`C10c` micro-plans are written
  when those sessions start (roadmap just-in-time).

## Files

New: `commonMain/.../time/{TimeService,FrameAccumulator}.kt`,
`commonMain/.../engine/{Driver,DriverService,Engine,WindowConfig,Viewport fitting}.kt`,
`jvmMain`/`webMain` driver backends + service defaults, `commonTest` recording
driver and loop/lifecycle tests, real-GL smoke in `jvmTest`/`webTest`.

Out of scope: input (`C10b`); addon mixins, layers/content (`C10c`); text
(`R6`); multi-engine; user shaders; audio.
