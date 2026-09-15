## 2026-09-14 — FPS benchmark tool + window config restorations

A runnable FPS benchmark (`kge-benchmark` KMP module, `jvm` + `wasmJs`) that
sweeps screen sizes and reports frame pacing on both backends, plus the
`WindowConfig` fields it needed. It is distinct from the `#23`
`kotlinx-benchmark`/JMH harness: this measures the whole engine loop (open
window, render step, pacing), not one hot function, so JMH's function-level
model does not fit.

### Benchmark

- **New `kge-benchmark` module** (KMP `jvm` + `wasmJs`, depends on `:kge-core`):
  `commonMain` subclasses `Engine` and drives the sweep; `jvmMain` has a `main`
  entry behind a custom `benchmarkJvm` JavaExec task; `wasmJsMain` has a browser
  `main` + `index.html` built by `wasmJsBrowserDistribution`. The pure
  aggregation (`FrameSampler`, `formatTable`) is pinned by `commonTest`.
- **Protocol**: one engine per (size, mode); 2 s warmup discarded + 5 s measured;
  reports average FPS, the slowest distinct one-second window and ms/frame; text
  table on the JVM, HTML table on the web. Sizes 320x240 → 3840x2160.
- **Modes**: JVM `vsync` × `highDpi` (4); web `rAF` × `highDpi` (2). The web keeps
  the production `requestAnimationFrame` pacing — there is no uncapped web mode.
- **Not in CI**: the module is part of `./gradlew build` (compiles, lints, runs
  the unit tests, builds the web distribution), but the sweep is the manual
  `benchmarkJvm` / `wasmJsBrowserDevelopmentRun` task only.

### Window config (restorations vs `main`)

- **`WindowConfig.highDpi` (default `false`)** restores `main`'s `enableRetina`.
  The JVM sets `GLFW_COCOA_RETINA_FRAMEBUFFER`; the web scales the backing store
  by `devicePixelRatio`. `main` rendered the web backing store in CSS pixels
  (DPR ignored), so `false` is parity and makes `C10a`'s web HiDPI behavior
  (`24-engine-c10a`, touch-point decision 6) **opt-in** — its default is
  superseded for the web.
- **`WindowConfig.keepAspectRatio` (default `false`)** restores `main`'s
  `glfwSetWindowAspectRatio` (JVM), dropped without a record in `C10a`.
- **`WindowConfig.decorated` (default `true`)** is new: on macOS a decorated
  window is clamped to the display's usable area, so a drawable larger than the
  screen cannot be measured; a borderless window is not clamped. The benchmark
  runs borderless.
- **`FrameInfo.framebufferSize`** exposes the drawable size per frame, so the
  sweep reports the physical resolution next to the logical one.

### Findings (macOS JVM, M1, 60 Hz panel)

- **`glfwSwapInterval(1)` is not a strict cap**: the swap chain is
  triple-buffered, so a share of `glfwSwapBuffers` returns early (measured
  p50 = 16.67 ms but mean = 14.4 ms with `glfwPollEvents`). Independent of the
  `glfw_async` library and of `glFinish`. The displayed image is still 60 Hz;
  only the loop's frame count runs ahead. The benchmark reports the slowest
  window and, with `highDpi`/`vsync` crossed, the throughput ceiling, rather than
  trusting the mean.
- **`glfw_async` + `-XstartOnFirstThread` traps (SIGTRAP)** in `glfwInit`.
  `main` never set the flag; the restructure's test tasks do
  (`kge-core/build.gradle.kts`) and the macOS GL smoke skips, so it is latent.
  The benchmark omits the flag and relies on `glfw_async`.
- **Decorated windows are clamped**: `glfwCreateWindow(3840x2160)` on the
  2560x1600 panel yields 1440x753; `glfwSetWindowSize` lifts only the width;
  borderless yields the requested size (and `highDpi` doubles it).
- HiDPI costs roughly 3–4.4× at equal logical size (2× pixels plus bandwidth):
  e.g. 1280x720 862 vs 2443 fps, 3840x2160 81 vs 358 fps (`uncapped`).

### Accepted divergences and limitations

- The web `vsync` config has no canvas analogue (rAF is implicit); the benchmark
  has no uncapped web mode, so web numbers are paced at the display.
- The larger JVM cells need borderless windows; on a display with DPR 1,
  `highDpi` on/off are identical.
- Web numbers from headless software rendering (SwiftShader) carry lower
  confidence than the JVM numbers.

### Carry-forward

- `C10c` (addons) carries the rest of `main`'s window manipulation, absent here
  because `WindowConfig` only holds the initial state: the mutable title
  (`main` `WindowManipulationAddon.changeWindowTitle` → `glfwSetWindowTitle`),
  show/hide, and the settable close flag (`windowShouldClose`). The `HasWindow`
  role/addon and a `Driver` seam extension (`setTitle`/`show`/`hide`) land there.

