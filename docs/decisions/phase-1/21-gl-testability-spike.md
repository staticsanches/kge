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

### 2026-09-12 — first CI run: findings and corrections (supersedes the CI decisions above)

The first pushed run (`34671433552`) failed on all three runners:

- **ubuntu — `setup-chrome` breaks the sandbox.** Chrome for Testing aborts with
  `FATAL ... No usable sandbox!`; Ubuntu 24.04 disables unprivileged user
  namespaces via AppArmor. The image's own Chrome (used before this change) did
  not hit it.
- **macOS/Windows — `ChromeHeadless has not captured in 60000 ms`.** The
  combination of `setup-chrome`/`CHROME_BIN` and the
  `--use-angle=swiftshader` flag made Karma time out launching Chrome. Windows
  previously ran the web suites green with the image Chrome and the default
  launcher.
- **JVM GL — `glfwCreateWindow` returned 0 on the macOS and Windows runners.**
  `glfwInit` succeeds but the hosted runners have no usable display/GPU for a GL
  window. The hidden-window approach is therefore not viable there.
- Minor: `coactions/setup-xvfb@v1` targets Node 20 (deprecated) and its
  `dist/cleanup.sh` is missing (post-run error).

Corrections (owner decisions):

- **Drop `setup-chrome`/`CHROME_BIN`; use the Chrome the runner image ships.**
- **Karma launcher flags become `--no-sandbox --enable-unsafe-swiftshader`**
  (drop `--use-angle=swiftshader`).
- **JVM GL moves to an offscreen context (owner): EGL surfaceless (Mesa) where
  available, hidden-GLFW fallback for dev machines, and a clean skip when
  neither works.** No xvfb: an offscreen context needs no display. The ubuntu
  job installs Mesa's EGL (`libegl-mesa0`, `libgl1-mesa-dri`).
- EGL is Linux-only (no EGL on macOS); OSMesa would need the native library.
  So JVM GL coverage in CI is expected on ubuntu only; macOS/Windows skip.

Still unverified after the correction (next CI run): EGL surfaceless on the
ubuntu runner; the image Chrome with the corrected launcher on all three OSes;
Windows JVM (expected to skip).

### 2026-09-12 — GLFW + Mesa instead of EGL/OSMesa (owner)

Reconsidering what the engine actually uses: EGL is a non-production context API
and is absent on macOS, so it is the wrong base for the harness. The engine's
JVM path is GLFW; the closest CI can get to it without a GPU is the production
GLFW path on a real software GL driver (Mesa llvmpipe):

- **Linux:** Xvfb + Mesa (`libgl1-mesa-dri`, `libglx-mesa0`); the build runs
  under `xvfb-run -a`.
- **Windows:** `ssciwr/setup-mesa-dist-win@v3` installs Mesa (llvmpipe) so GLFW
  gets an OpenGL context without a GPU. (Low-adoption action; unverified.)
- **macOS:** hosted runners cannot create a GL context and no software GL stack
  exists for macOS, so the probe skips there.

`lwjgl-egl` was removed. This is still software rendering, not the user's GPU:
it exercises the API and driver, not pixel parity. The C9 primary oracle
remains a recording GL backend (all OSes, no GPU); real GL is a smoke test.

### 2026-09-12 — macOS: no software-GL path into GLFW's Cocoa backend (research)

Follow-up to skipping OSX real-GL: is there a Mesa analog for macOS? The macOS
runner ships only Apple's Software Renderer, which reports **OpenGL 2.1**.
GLFW's `nsgl_context.m` unconditionally adds `NSOpenGLPFAAccelerated` and
requests a 3.2/4.1 core profile, so pixel-format selection fails and
`glfwCreateWindow` returns 0. The blocker is renderer capability, not a missing
display: Metal windows render on `macos-14`.

Dead ends (do not re-research):

- `brew install mesa` — the macOS build is X11/GLX (XQuartz): `llvmpipe` +
  Zink, no EGL/OSMesa target; a Cocoa/NSOpenGL app cannot consume it.
- SwiftShader — CPU Vulkan 1.3 ICD (`VK_ICD_FILENAMES`); builds on macOS but
  exposes no desktop GL.
- ANGLE — GLES 2/3.x only; prebuilts (e.g. Godot's) are static GLES-over-Metal
  build artifacts; stock GLFW's Cocoa backend maps only OPENGL/METAL, not the
  ANGLE Vulkan/SwiftShader device.
- No macOS software-GL GitHub Action exists; the only Mesa action is
  Windows-only (`ssciwr/setup-mesa-dist-win`).

Working but non-production options, if macOS real-GL ever becomes required:
patched GLFW (drop `NSOpenGLPFAAccelerated` or retry with
`NSOpenGLPFARendererID = kCGLRendererGenericFloatID` → Apple Software Renderer
at 4.1 core; upstream PR closed/unreleased, `GLFW_CONTEXT_RENDERER` is not in
3.5.1; LWJGL can load a patched dylib via `org.lwjgl.glfw.libname`); SDL2
(reaches the Apple Software Renderer where stock GLFW fails); OSMesa built from
source (removed from Mesa ≥ 25.1, offscreen-only; GLFW's
`GLFW_OSMESA_CONTEXT_API` is Null-platform-only, so not with a Cocoa window);
SwANGLE (ANGLE Vulkan + SwiftShader ICD, GLES 3.1, EGL/GLES route).

Consequence for CI: the JVM production path (GLFW + GL 3.3) is CI-testable on
ubuntu (Mesa llvmpipe + Xvfb) and windows (Mesa dist) pending verification, but
**not on macOS**. The E1 loop has no GL dependency (recording backend on all
OSes) and the web path (rAF + WebGL2/SwiftShader) runs on all three runners,
macOS included — consistent with the C9 oracle decision.

Sources: GLFW `src/nsgl_context.m`; `actions/runner-images#9712`; `glfw#2080`
and `glfw#2571`; Mesa macOS notes (`docs.mesa3d.org/macos.html`); Homebrew
`mesa` formula; F3D's macOS OSMesa CI workflow.
