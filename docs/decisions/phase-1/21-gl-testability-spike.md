## 2026-09-12 — GL testability spike: context + readback on JVM and web

`C9` (renderer/GL/decals) has no test oracle: `main` shipped the whole
renderer/GL/decal stack with **zero tests** (commonTest held only
Sprite/rasterizer/benchmark). Before designing the C9 harness, a throwaway probe
verified whether a GL context and a pixel readback are reachable from the test
suites at all. The probe is kept as the **seed of the C9 harness** (owner
decision). Findings below are verified locally on macOS (arm64, Google Chrome
152); CI verification on ubuntu/windows is still pending (see the end).

### Verified facts (local, 2026-09-12)

- **JVM — hidden GLFW window + OpenGL 3.3 core + FBO + `glReadPixels` works.**
  `kge-core/src/jvmTest/.../GLSmokeTest.kt`: hidden window (no display needed
  once a display exists), core-profile 3.3, off-screen framebuffer, clear red,
  read one pixel back.
- **macOS requires `-XstartOnFirstThread`.** Without it `glfwInit` throws
  `IllegalStateException: GLFW may only be used on the main thread ...`. The
  `Test` tasks get the flag conditionally when `os.name` starts with `Mac`
  (`kge-core/build.gradle.kts`); the full JVM suite (373 tests) is green with
  it.
- **KGP's default `ChromeHeadless` yields no WebGL2.** In headless Chrome 152,
  `canvas.getContext("webgl2")` returned `null` under Karma's default launcher.
  A custom launcher based on `ChromeHeadless` with
  `--enable-unsafe-swiftshader --use-angle=swiftshader`
  (`kge-core/karma.config.d/webgl.js`, appended by KGP into `karma.conf.js`)
  makes WebGL2 available; the probe is green on **both js and wasmJs**.
- **`webTest`** — the default-hierarchy source set common to `js` and `wasmJs`
  — exists and compiles; the web probe lives there once
  (`kge-core/src/webTest/.../WebGLSmokeTest.kt`).
- **`web.gl.WebGL2RenderingContext` comes from `kotlin-browser`, not
  `kotlin-js`.** `kotlin-browser` 2026.9.0 has a `wasm-js` klib. Test source
  sets do **not** inherit the main source set's `implementation` deps, so the
  binding is declared in `webTest.dependencies`.
- **The macOS runner image ships Chrome.** `macos-latest` (macOS 26 arm64,
  image 20260907) lists `Google Chrome 152`, `Google Chrome for Testing 152`
  and `ChromeDriver 152`. The earlier CI macOS exclusion (decisions-log #19,
  "macOS runner Chrome is unverified") was an unverified assumption; the claim
  is now falsified and the exclusion removed.

### Decisions (owner)

- **Keep the two smoke tests + `karma.config.d` as the seed of the C9 harness.**
- **CI is uniform:** all three runners execute the full `build`.
  `browser-actions/setup-chrome@v2` pins a Chrome for Testing channel on every
  runner and `CHROME_BIN` points the KGP Karma launcher at it; the build runs
  through `coactions/setup-xvfb`, which installs xvfb and wraps the command on
  the display-less ubuntu runner and just runs it on macOS/Windows.

### Unverified — requires a CI run (owner pushes)

- ubuntu JVM: GL 3.3 core through `xvfb`/Mesa (`llvmpipe`) — the Xvfb path was
  never exercised.
- ubuntu/windows web: WebGL2 through SwiftShader (ubuntu) and ANGLE/D3D
  (windows).
- `setup-chrome`/`CHROME_BIN` resolution inside the Karma-launched Node
  process.
- windows JVM: GLFW/OpenGL context creation.
- Pixel-exactness across GPU backends is **not** expected to hold; C9 harness
  golden tests must tolerate driver differences (out of scope for this probe).

Note: `.github/workflows/build.yaml` still triggers on `branches: [restructure]`;
the current work branch is `feature`, so the run must be targeted accordingly.
