## 2026-09-13 — Engine C10a (loop/window/time): touch-point + close

`C10` splits at the 2026-09-13 touch-point into `C10a` (loop/window/time) →
`C10b` (input) → `C10c` (addons); each closes with its own gate and log entry.
Design material: `docs/plans/2026-09-13-c10-engine-touchpoint.md` and
`docs/plans/2026-09-13-c10a-engine-microplan.md` (written just-in-time).
`C10a` delivers the `Driver` seam, the window/context, `TimeService`, frame
accounting, lifecycle, the user callbacks, the letterbox and the per-frame
render step (clear + present). Input and the addon mixins/layers are
`C10b`/`C10c`.

### Time

- **`TimeService : KGEOverridable`** — `fun elapsed(): Duration`, one unit on every
  platform, monotonic. The default is a **common** implementation over
  `TimeSource.Monotonic` (JVM `System.nanoTime()`, web `performance.now()`); no
  `expect`/`actual` is needed because the stdlib source is already multiplatform
  and monotonic. Facade `object Time : TimeService by TimeService`. This
  supersedes `main`'s `TimeState` defects: the per-platform unit split (JVM
  seconds × JS milliseconds) and the non-monotonic `Date.now()` are rejected, as
  is coupling FPS to the clock (`fpsUpdater`).
- **`FrameAccumulator`** (internal, pure): `tick(now)` returns the frame delta
  and carries olc's FPS window exactly (`olcPixelGameEngine.h:4929-4937`):
  accumulate into a `>= 1.seconds` window, publish the window count, subtract
  `1.seconds` (drift carry, not reset), reset the window counter. The first tick
  returns `Duration.ZERO` and publishes no FPS — the `main` `frameTimer = 1.0`
  fake-FPS artifact is dropped.
- **Divergence (recorded):** `FrameInfo.frameCount` is a total since start; olc
  exposes no public frame count and resets its `nFrameCount` each window.

### Window / Driver

- **`WindowConfig`** — an immutable explicit holder replacing `main`'s global
  mutable `KGEConfiguration`; olc-anchored defaults (`pixelSize = 1`,
  `resizable = true`, `vsync = false`, `fullScreen = false`,
  `cohesion = false`), fail-fast when any size is non-positive (olc `Construct`
  returns `FAIL`). No process-global mutable configuration.
- **`Driver : GpuDevice, AutoCloseable`** — the platform window/context seam:
  `pollEvents()`, `suspend awaitNextFrame()`, `windowSize()` (logical points),
  `framebufferSize()` (physical pixels), `isClosing()`, idempotent `close()`;
  `makeCurrent`/`present` come from the C9 `GpuDevice`. The driver owns the
  window and the context.
- **`DriverService : KGEOverridable`** with `fun create(config): Driver` and an
  `internal expect val driverServiceDefault` — the `MemoryAllocatorService`
  shape. JVM: GLFW (GL 3.3 core/forward-compat; `glfw_async` before `glfwInit`
  on macOS; context made current before `glfwSwapInterval`). Web: canvas +
  WebGL2 with `awaitNextFrame` over `requestAnimationFrame`, `present`/`pollEvents`
  no-ops. A public web `WebDriverService(canvas)` binds the engine to a
  caller-owned canvas; the default creates and owns one. `GlfwDriverService`
  stays `internal` with an internal `visible` seam so the real-GL smoke uses a
  hidden window.
- **Driver creation is a service, not a constructor factory** (decided at the
  touch-point): uniform override for tests and the web holder; no provisional
  API.

### Engine loop and lifecycle

- **`Engine`** — abstract, user-subclassed (the olc model); `open suspend`
  `onUserCreate()` (false aborts startup), `onUserUpdate(elapsed)` (false leaves
  the inner loop), `onUserDestroy()` (false restarts, no re-create); `suspend
  start()`, `fun stop()`, `val dispatcher`, `fun requireEngineThread()`,
  `val frame`. Mode-1 confinement: the loop runs on the thread that called
  `start()` and never switches dispatchers; `dispatcher` is captured from the
  calling coroutine context, and `requireEngineThread()` compares the OS
  `Thread.id` (`internal expect/actual currentThreadId`) — the thread name is
  never identity. `Dispatchers.Main` is never used: the engine stays on the
  caller's captured dispatcher (a design choice, not a runtime check).
- **The active flag is atomic** (`kotlin.concurrent.atomics.AtomicBoolean`),
  mirroring olc's `std::atomic<bool> bAtomActive` because `stop()` (olc
  `olc_Terminate`, `:4636`) may be called from another thread; the flag is an
  instance field, not a process global. `onUserDestroy()` is always consulted:
  returning true exits, returning false restarts the loop without re-create —
  olc parity (`:4655-4665`) and `main` parity
  (`KotlinGameEngine.doStart`: `if (!onUserDestroy()) windowShouldClose = false`).
  A `false` return vetoes a pending close request through the new
  `Driver.cancelClose()` (JVM `glfwSetWindowShouldClose(window, false)`, a web
  no-op); clearing the sticky platform request lets the restarted loop render
  instead of spinning on the still-set flag. No terminal-close special case.
- **Ownership/teardown:** the engine creates the `Driver`, makes it current,
  owns one `ResourceScope`, runs `Renderer.createResources`, then the loop; on
  any exit it closes the scope **before** the driver (context alive for GPU
  release) and calls `resetAll()`. One engine per process; the service registry
  is process-global.

### Viewport and render step

- **`fitViewport`** (internal, pure) computes the GL viewport from
  `screenSize × pixelSize` fitted into `framebufferSize` in physical pixels,
  centered. **Divergence (recorded, touch-point decision 6):** the default scale
  is **fractional**, fixing `main`'s integer-truncated DPR ratio; olc truncates
  to int. `cohesion` is the opt-in uniform integer-floor snap (olc's mode);
  degenerate (non-positive) framebuffer axes yield a zero-size viewport.
- **Render step** per frame: `updateViewport` → `clearBuffer(Colors.BLACK,
  depth = true)` → `prepareDrawing` → `present` → `awaitNextFrame`. The context
  is made current **once** at startup, not per frame (`olc_CoreUpdate`/`main`
  bind once); the letterbox is recomputed only when `framebufferSize()` changes.
  Content/layers/decals are `C10c`.
- **Web HiDPI (touch-point decision 6):** the canvas CSS size is set to the
  logical size and the backing store to `logical × devicePixelRatio`;
  `windowSize()` reports the logical size, `framebufferSize()` the backing store.

### Accepted divergences and limitations

- `TimeSource.Monotonic`'s origin differs from `glfwGetTime` (since `glfwInit`);
  only deltas are used, so the origin is never observable.
- The JVM `awaitNextFrame` and the web `pollEvents` are no-ops (vsync swap / DOM
  queue respectively).
- Web `WindowConfig.title`/`resizable`/`fullScreen`/`vsync` have no canvas
  analogue (vsync is implicit in `requestAnimationFrame`); accepted, not wired.
- The web window size is fixed at creation; a browser-resize path is deferred
  (`C10b`, with the resize/input mapping).
- No `expect`/`actual` for the time source; `Duration` is a `Long` (native on
  JVM/wasmJs, emulated on the `js` target) — the loop does a handful of ops per
  frame.

### Review and gate

Two-axis review (Standards + Spec, fresh sub-agents; reports
`.opencode/reviews/c10a-engine-{standards,spec}.md`): 1 Important per axis —
per-frame `makeCurrent`/viewport work with no rationale, and the web HiDPI CSS
size never set — plus minors. Fixed in one round (context bound once, viewport
cached on resize, JVM size arrays reused, web CSS size set, `cohesion` KDoc,
`RecordingDriver` gaps closed); the `isClosing` exit was found while testing the
close path, and the close-veto semantics (`onUserDestroy() == false` clears the
sticky request and continues) were settled on the final re-reviews.
Scoped re-review clean. Gate: `./gradlew build
--rerun-tasks` green (JVM + js browser + wasmJs browser + ktlint +
assemble/metadata). The JVM real-GL smoke skips on macOS; both web smokes
execute.

### Carry-forward

- `C10b` (input): `KeyCode` + edge-flag bitsets, mouse, focus, and the web
  resize/input mapping.
- `C10c` (addons): the ISP role interfaces (`HasTime`/`HasWindow`/…), addon
  mixins, layers/content drawing.
- Multi-engine and any global-handle policy (none required so far).
- The `js` `Duration`/`Long` hot-path constraint stands (benchmark-gated).
