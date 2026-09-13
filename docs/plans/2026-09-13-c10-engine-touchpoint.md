# Engine (C10) — touch-point material

**Date:** 2026-09-13. Pre-touch-point material for `C10` (engine: `E1`
loop/window + `E2` addons + `E3` state + `E4` KeyCode/InputAction), the concept
after `C9` (closed 2026-09-12, decisions log #22). It collects the investigation
and records the decisions closed in discussion; **open items are left marked** —
the concept does not start until they are decided.

Status: **decided 2026-09-13**. The concept is split into three sessions,
`C10a` (loop/window/time/lifecycle) → `C10b` (input) → `C10c` (addons), in that
order; each closes with its own gate and decisions-log entry. The open items
below are now closed (see "Decisions closed at this touch-point").

## Real consumer

A user application that opens a window, draws through the C9 stack (`Renderer`
over `ResourceScope` + `GLService`, `Decal`, `Rasterizer`) and reads input, on
JVM and web. None of C10 exists yet: `GpuDevice` is the only seam in place (used
by the renderer and the tests); the window/context, the loop, time and the
user-facing surface do not exist.

## Established rejections (from the sources, not re-decided)

- **`WithKGEState` god-interface** — mixes window/GL, time, input, renderer
  modes, layers and text (decisions log #20; confirmed against `main`).
- **Blocking/suspend asymmetry** — `main` ships two engines: JVM `start(): Unit`
  blocking with sync callbacks, JS `start(): Job` suspend with suspend
  callbacks. The unified suspend loop is the approved direction (E1 spike,
  roadmap).
- **`TimeState` defects** — per-platform unit (JVM seconds × JS milliseconds),
  non-monotonic web clock (`Date.now()`), and FPS computed inside the clock
  with per-platform magic constants (decisions log #20; confirmed).
- **`KeyboardKey` as a platform `expect enum`** — different membership and order
  per platform, indexed by `ordinal`, so a key-state array is not portable
  (`main` defects).
- **`DimensionState` as "state"** — it is window/GL state; splits to E6 (window)
  / R4 (GL viewport), with the pure letterbox math feeding the R2 `Viewport`
  family.

## Foundation in the kernel (C10 consumes)

- `Renderer` is stateless (`KGEOverridable`) over an engine-owned
  `ResourceScope`; `createResources(device, scope)` builds the built-in quad.
- `GpuDevice` (`makeCurrent`/`present`) is the external context seam; the
  renderer never owns a context. Test doubles: `RecordingGpuDevice`,
  `GlfwTestDevice` (jvmTest), `WebGlTestDevice` (webTest).
- The real-context creation already materialized in tests is the skeleton the
  production window must mirror: JVM hidden GLFW window + GL 3.3 core; web
  canvas + WebGL2.
- `Viewport` (R2) owns the pure clipping math; the engine owns window→viewport.

## Decisions closed in discussion (owner)

1. **Time source is a global `KGEOverridable` seam: `TimeService`.** Overridable
   process-wide for a virtual clock in tests — the same shape as
   `PixelFormatService`/`ImageService`. One unit on every platform: **seconds**,
   from a **monotonic** source (olc's convention; `main`'s seconds×milliseconds
   split and `Date.now()` are rejected). **Refinement (2026-09-13, micro-plan):**
   the default needs no `expect`/`actual` — `TimeSource.Monotonic` is a
   multiplatform stdlib facility already backed by `System.nanoTime()` (JVM) and
   `performance.now()` (web), so the default is a **common** implementation.
2. **Time representation is `kotlin.time.Duration`** (owner, 2026-09-13) — the
   `TimeService` return type and the frame time exposed to the app. Rationale: the
   stdlib type is unit-safe (no seconds×milliseconds ambiguity in the global
   seam), and its `Long`-backed arithmetic is **native on JVM (`long`) and
   `wasmJs` (`i64`)**; only the `js` target emulates `Long` as a boxed two-`Int`
   class on the project's current ES5 target (measured ~9x a `Number` op, with
   allocation). The cost is bounded: the loop performs only a handful of
   `Duration` operations per frame (~µs/s at 60 fps). **Constraint noted:** do
   not put `Duration`/`Long` in a hot path on the `js` target — convert to a
   `Double`/`Float` seconds at the boundary. Boxing applies only where a
   `Duration` is used as *another* type (nullable, `Any`, a generic type
   argument, or a supertype it implements); a method whose declared type is
   `Duration` (e.g. the `TimeService` method) stays unboxed — verified by
   compiling `interface TimeService { fun now(): Duration }`, which emits
   `long now-...()` on the JVM.
3. **FPS is computed by the loop and held in a focused, read-only state holder**
   (owner, 2026-09-13). `TimeService` is a pure time seam — no FPS callback; the
   loop owns frame accounting (olc's `>= 1s` window with drift carry) and writes
   a cohesive holder (`elapsed`/`fps`/`frameCount`). The `main` coupling — the
   clock receiving an `fpsUpdater` (`ClockService.create { fps = it }`) — is
   rejected; the window is `1.seconds` (one unit on every target), and the
   first-frame artifact of `main`'s `reset()` (`frameTimer = 1.0`) is dropped.
4. **`E2` is ISP addons over role interfaces — no central state contract**
   (owner, 2026-09-13). Each capability is a narrow role (`HasTime`,
   `HasWindow`, `HasInput`, …) owning one cohesive read-only holder; an addon is
   a set of defaults over the role it needs. The engine is a composition root
   implementing the roles; addons and consumers depend on the narrow role. The
   `WithKGEState`/`WindowDependentAddon` forwarding-all shape is rejected;
   "pass the role, not the engine" is the rule.

## Decisions closed at this touch-point (owner, 2026-09-13)

1. **Split and order: `C10a` → `C10b` → `C10c`.** `C10a` (E1/E6): the Driver
   seam, window + context, the loop, timing and frame accounting, lifecycle and
   the user callbacks. `C10b` (E4): input. `C10c` (E2): the addon mixins over
   the roles. The callbacks are **loop-owned** (the loop invokes them), so they
   ship in `C10a`; the mixins are ergonomics over the roles `C10a`/`C10b` expose,
   so they ship last. Each session closes with its own gate and log entry;
   `C10b`/`C10c` micro-plans are written when they start (just-in-time).
2. **Driver seam: `Driver : GpuDevice`.** The platform seam is the C9 `GpuDevice`
   (`makeCurrent`/`present`) extended with window lifecycle + dimensions,
   `pollEvents()` (JVM GLFW pump; web no-op — DOM events are already queued) and
   `awaitNextFrame()` (web `requestAnimationFrame`; JVM vsync swap paces, no-op
   when vsync is off). Rationale: on the JVM the window and the GL context are
   the same GLFW object, the E1 spike validated a single Driver seam, and
   `GpuDevice` stays the renderer's dependency unchanged. The driver creates the
   window/context; it becomes current inside the engine thread (olc
   `olc_PrepareEngine`). Window creation goes through an overridable
   `DriverService : KGEOverridable` (the `MemoryAllocatorService` shape), so
   tests fake the window and the web holder is supplied by an override.
3. **Threading: mode-1 (calling/main thread).** `fun main() = runBlocking {
   engine.start() }`; the loop is confined to the thread that called `start()`.
   The engine exposes `engine.dispatcher` (that thread's confined dispatcher) and
   a fail-fast `requireEngineThread()`; `Dispatchers.Main` is rejected. Thread
   identity is the OS `Thread.id`, never the thread name (the spike's identity
   pitfall). Frame work never runs on `Dispatchers.Default`/`IO`.
4. **Callbacks and lifecycle: olc parity.** `suspend` `onUserCreate(): Boolean`
   (false aborts startup), `onUserUpdate(elapsed: Duration): Boolean` (false
   requests shutdown), `onUserDestroy(): Boolean` (false restarts the loop —
   `bAtomActive` semantics kept, no re-create). `elapsed` is the frame delta as
   `Duration` (decision 2 above). `suspend fun start()`; external termination via
   `stop()` (olc `olc_Terminate`). One engine per process (the service registry is
   process-global; `resetAll` on destroy); the engine owns a `ResourceScope`,
   closes it before the window/context dies, then the driver, then `resetAll`.
5. **Window/config: explicit holder, no global mutable config.** A config value
   (screenWidth/Height, pixelWidth/Height, title, resizable, vsync, fullScreen,
   cohesion) replaces `main`'s global mutable `KGEConfiguration`; non-positive
   sizes fail fast (olc `Construct`'s `FAIL`).
6. **HiDPI/viewport: framebuffer pixels, fractional scale.** The engine tracks
   `windowSize` (points/CSS px — input), `framebufferSize` (physical px —
   viewport) and `pixelSize` (art zoom). The letterbox is computed in framebuffer
   pixels with the real (possibly fractional) scale and fed to
   `Renderer.updateViewport`; mouse input is mapped back to logical screen
   coordinates through the same view. Fixes `main`'s integer-truncated DPR ratio,
   the retina-off default and the web DPR-blind canvas. olc's cohesion (integer
   floor scale) becomes an opt-in flag, not the default.
7. **Input (E4): common `KeyCode` + edge flags in bitsets.** A single common
   `KeyCode` (not `main`'s expect enum) with a platform mapping table (GLFW key /
   DOM `code`); per-key `held`/`pressed`/`released` as bitsets, latched once per
   frame (olc's `HWButton`); the mouse (position in logical coordinates, buttons,
   wheel delta) in the same model; modifiers; focus (held state cleared on focus
   loss). An ordered event list is not shipped this round.

## Time representation — verified facts (2026-09-13)

- **`kotlin.time.Duration` is `@JvmInline value class Duration internal
  constructor(private val rawValue: Long)`** (stdlib 2.4.10): a bit-packed
  `Long`, saturation, `INFINITE`/`NEG_INFINITE`/`INVALID`. `Duration` is a value
  class, so it is unboxed in locals/fields but boxed where a reference is
  required (nullable/`Any`/a generic type argument such as `List`/`Sequence`; a
  method whose declared type is `Duration` stays unboxed).
- **`TimeSource.Monotonic`** returns a `ValueTimeMark` (also an inline value
  class over the platform reading): JVM `System.nanoTime()`; js/wasmJs
  `performance.now()` `Double` milliseconds.
- **`Long` per target:** JVM `long` primitive; `wasmJs` `i64` (native Wasm
  value type — the interop table maps `Long`↔`BigInt` only at the JS boundary);
  **`js` emulated** as a boxed two-`Int` class on this project's ES5 target
  (compiled `function Long(low, high)` and `add` ends in `new Long(...)`).
- **Measured (Node 20 / V8, 20M adds):** `Number` 13.2 ms (~1520 M ops/s);
  `BigInt` 76.4 ms (~262 M/s); boxed `Long` 117.9 ms (~170 M/s) — the js target
  is ~9x a `Number` op and allocates per op.
- Consequence: `Duration` is fine for the per-frame loop on every target; the
  only hot-path risk is on the `js` target.

