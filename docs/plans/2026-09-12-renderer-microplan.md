# Renderer/pipeline micro-plan (interactive execution)

**Date:** 2026-09-12. Touch-point decisions in
`docs/plans/2026-09-12-renderer-touchpoint.md`. Concept: the common `Renderer`
service over the `GLService` seam plus the external device seam; the built-in
quad program; the internal staging buffer; the texture ops; `prepareDrawing`,
`drawLayerQuad`, `drawDecal(instance)`, `clearBuffer`, `updateViewport`. Second
of the three concepts (after the GL layer, before the decal).

## Working mode

- **No subagents.** One step = test (red) → implement (green on jvm+js+wasmJs) →
  mark. Doubts resolved in-session with the owner.
- **Oracle.** The recording `GLService` (from the GL layer) records the exact GL
  call sequence the common renderer produces; the real-GL smoke pins the
  platform path. Vertex math, blend mapping and draw order are common and
  recording-tested.
- **Layering.** The renderer owns no window/context (device seam only) and no
  layers (engine-owned).

## Contract (touch-point)

- **Device seam** (external owner): `makeCurrent()`, `present()`, with per-target
  implementations. The window (later concept) and the test harness implement it.
- **`Renderer : KGEOverridable`**, common default:
  `createTexture(width, height, filter, wrap): Texture`;
  `updateTexture(texture, sprite)`, `readTexture(texture, sprite)`,
  `applyTexture(texture)`; `prepareDrawing()`;
  `drawLayerQuad(offset: Float2D, scale: Float2D, tint: Pixel)`;
  `drawDecal(instance: DecalInstance)`;
  `clearBuffer(color: Pixel, depth: Boolean)`;
  `updateViewport(position: Int2D, size: Int2D)`. No defaults on the parameters.
- **Internal:** staging buffer (dynamic; no global config); one built-in program
  (quad shader, GLSL 330 core / 300 es) built through the general `GLService`
  shader/program ops; no FBO/render target; no public user-shader API.
- **Texture deletion** goes through `Texture.close()` (T1); the decal owns the
  texture.

## Resume tracker

- [x] **0.** Device seam + per-target test implementations
- [x] **1.** `Renderer` skeleton + built-in program + staging + `prepareDrawing`
- [x] **2.** Texture ops (`create`/`update`/`read`/`apply`)
- [x] **3.** `drawLayerQuad` + `drawDecal(instance)` (vertex build, blend, primitive)
- [x] **4.** `clearBuffer` + `updateViewport`
- [x] **5.** Real-GL smoke (FBO render + readback)
- [x] **6.** Gate + review + decisions-log close

## Steps

### 0. Device seam

- **Tests:** a test device makes a context current and presents against the
  recording `GLService`; the seam has no window knowledge.
- **Files:** `commonMain/.../renderer/device/GpuDevice.kt`; the promoted spike
  helpers in `jvmTest`/`webTest`.
- **Decided:** `present` is the former `displayFrame`, owned by the device.

### 1. `Renderer` skeleton + built-in program + staging + `prepareDrawing`

- **Tests:** `prepareDrawing` records the expected state program (useProgram,
  bindVertexArray, enable(BLEND), blendFunc default, etc.); the built-in program
  compiles/links through the recorded shader/program ops; the program is built
  once, not per frame; the staging buffer is reused.
- **Files:** `commonMain/.../renderer/Renderer.kt`, `.../renderer/internal/`
  (program/staging).
- **Decided:** the built-in program is created via the general GL ops, so user
  shaders are not precluded; no `KGEConfiguration` global.

### 2. Texture ops

- **Tests:** `createTexture` maps `Decal.Filter`/`Decal.Wrap` to the GL filter/
  wrap parameters; `updateTexture` records bind + `texSubImage2D` from the
  sprite buffer; `readTexture` records `readPixels` into the sprite; `applyTexture`
  records the bind; deletion via `Texture.close()` records `deleteTexture` once.
- **Decided:** the `Texture` type is the GL layer's; the renderer only wires it.

### 3. `drawLayerQuad` + `drawDecal(instance)`

- **Tests:** `drawLayerQuad` builds the full clip-space quad from `offset`/
  `scale`/`tint` and records `drawArrays(TRIANGLE_STRIP, …)`; `drawDecal(instance)`
  maps `Decal.Mode`→`blendFunc` (the exact olc pairs) and `Decal.Structure`→the
  primitive, uploads the N vertices and records the draw; consecutive instances
  draw in order with texture bind and blend per run (no batching).
- **Files:** `.../renderer/` vertex building + the blend/primitive mapping.
- **Decided:** one draw per instance this stage; `multiDrawArrays` is future work,
  benchmark-gated, possibly an alternative default `Renderer`.

### 4. `clearBuffer` + `updateViewport`

- **Tests:** `clearBuffer(color, depth)` records `clearColor` + `clear` (color
  and, when requested, depth); `updateViewport` records `viewport`.
- **Decided:** no default clear color (no platform divergence).

### 5. Real-GL smoke

- **Tests:** render a `DecalInstance` into an FBO and read a pixel back through
  the real platform default (JVM hidden GLFW / web WebGL2); macOS skips the JVM
  path. Uses the promoted spike helper.
- **Decided:** smoke only — pixel exactness across GPU backends is not expected.

### 6. Gate + review + close

- **Gate:** `./gradlew build --rerun-tasks` green. Leak audit of every allocate/
  close path and construction-failure branch (`letClosingIfFailed` on the staged
  program/buffer resources); no-parameter-without-observable-effect audit.
  Two-axis review; decisions-log close entry.

## Files

New: `renderer/device/GpuDevice.kt`, `renderer/Renderer.kt`, internal program/
staging, `commonTest` recording-renderer tests, `jvmTest`/`webTest` real-GL smoke.

Out of scope: `multiDrawArrays` optimization, user shaders, FBO/render targets,
HW3D, layers/engine loop (later concept), window creation (later concept).
