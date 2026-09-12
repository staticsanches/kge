# Renderer/pipeline — touch-point material

**Date:** 2026-09-12. Pre-touch-point material for the renderer/pipeline concept
— the second of three concepts treated this session (**decal →
renderer/pipeline → GL layer**), touch-points in **reverse order** so each
layer's requirements are collected by its consumer. Implementation runs in the
forward order. Decisions closed in discussion (owner) are recorded here; the
micro-plan is TDD, written after the three touch-points.

## Investigation summary

- **olc v2.30.** `Renderer` is an abstract class with per-platform backends plus
  `Renderer_Headless` (no-op), **separate** from `Platform` (window/context/
  events). Methods: `PrepareDevice`, `CreateDevice(params,…)`, `DestroyDevice`,
  `DisplayFrame`, `PrepareDrawing`, `SetDecalMode`, `DrawLayerQuad`,
  `DrawDecal(DecalInstance)`, `DoGPUTask`, `Set3DProjection`, `CreateTexture`/
  `UpdateTexture`/`ReadTexture`/`DeleteTexture`/`ApplyTexture`, `UpdateViewport`,
  `ClearBuffer`. The engine loop drives viewport/clear/prepare, per layer
  (apply+update texture, layer quad, decals), flush, display.
- **`main` (evidence, not mandate).** `Renderer` + a shared `BaseRenderer`
  (common logic) + thin platform backends; `QuadInfo` (program/VAO/streaming
  VBO/embedded shaders/blank decal); `LayerDescriptor`. `KGEConfiguration` is a
  mutable global (capacity); `defaultClearBufferColor` diverges per platform
  (JVM `BLACK`, JS `BLANK`). The window/context lives in the engine; the
  renderer only had `beforeWindowCreation`/`afterWindowCreation(window)`.
- **Current kernel.** No renderer/GL/window in production; GL deps live only in
  `jvmTest`. The spike is `GlTestContext` (hidden GLFW window, GL 3.3 core, FBO
  readback) + `WebGLSmokeTest` in the shared `webTest`.
- **Decal requirements (input).** Texture lifecycle under T1; apply/bind; draw a
  non-null `DecalInstance` (N vertices, `mode`→blend, `structure`→primitive);
  viewport/clear/present; the renderer must be resolvable before decal creation.

## Decided (2026-09-12, owner)

1. **External context + device seam.** The renderer operates on a
   current context owned elsewhere; this concept defines a minimal **device
   seam** (`makeCurrent()`, `present()`) with per-platform actuals. The window
   (later concept) and the test harness implement it (hidden GLFW window on the
   JVM tests; canvas on the web tests). The renderer backend does drawing/GL
   only, never window management — same split as `main`.
2. **Layers belong to the engine** (later concept): draw target (sprite+decal),
   offset/scale/show/update/tint, per-layer instance collection. The renderer
   only receives calls and never knows a layer.
3. **Renderer = common logic + a `GLService` seam.** Vertex building, state and
   draw ordering are common; all GL calls go through an overridable `GLService`
   whose platform backends are the actuals (LWJGL GL33 / WebGL2) and whose test
   backend is the **recording** one. This is the `main` structure
   (`BaseRenderer` + `GLService`) and makes the recording oracle native; the
   renderer itself has a common default (no `expect`/`actual` of its own).
4. **`GLService : KGEOverridable`** with platform defaults via `internal expect
   val` (JVM LWJGL GL33, web WebGL2), like `BufferService`/`ImageService`. The
   recording backend is a test override; `resetAll` clears it. `Renderer` is
   likewise `KGEOverridable`.
5. **`Renderer` surface** (common default): `createTexture(width, height,
   filter, wrap): Texture` (T1 resource; the `Texture` type is defined by the GL
   layer); `updateTexture(texture, sprite)`, `readTexture(texture, sprite)`,
   `applyTexture(texture)`; `prepareDrawing()`; `drawLayerQuad(offset: Float2D,
   scale: Float2D, tint: Pixel)`; `drawDecal(instance: DecalInstance)`;
   `clearBuffer(color: Pixel, depth: Boolean)` and `updateViewport(position:
   Int2D, size: Int2D)` — **no defaults**. Texture deletion goes through
   `Texture.close()`.
6. **Device seam**: `makeCurrent()`, `present()`. `present` is the former
   `displayFrame`, owned by the device, not the renderer.
7. **Internal (not API):** the staging buffer (dynamic; the `KGEConfiguration`
   mutable global is rejected), and **one built-in program** (the quad shader),
   created through the general `GLService` shader/program operations. No
   FBO/render target (layers are CPU sprites). No public user-shader API now.
8. **User shaders are not precluded.** The `GLService` exposes the full
   shader/program/uniform/attribute/buffer/VAO/draw primitive set from the
   start, the built-in program is built through it, and the `Renderer` is
   overridable — so a future user-shader concept can land without breaking the
   built-in path. The vertex layout (pos/uv/tint) is the built-in program's and
   is revisitable then.
9. **Recording `GLService` is the primary oracle** (tests only, all OSes, no
   GPU); the real-GL smoke tests from the spike are promoted (hidden GLFW / FBO
   readback on JVM, WebGL2 on web). macOS cannot run the JVM production GL path
   and skips real GL (decisions log chunk 21); the web path runs on all three
   runners.
10. **Future optimization (from the decal concept):** the `multiDrawArrays`
    batching is future work, gated on a benchmark measuring real gains; a
    possible shape is an alternative default `Renderer` implementation,
    consumer-selectable through the overridable service mechanism.

## Requirements pushed to the GL layer (input to the next touch-point)

1. **Command surface**: texture (gen/bind/texImage/texSubImage/readPixels/
   delete, filter/wrap parameters), shader/program (create/source/compile/
   attach/link/use, get location, uniforms), buffers/VAO (gen/bind/bufferData/
   bufferSubData/vertexAttribPointer/enable), `drawArrays`, state (blendFunc/
   enable/disable, depthFunc), `clearColor`/`clear`, `viewport`.
2. **Handle representation must allow the recording backend on both targets.**
   A web DOM handle (`WebGLTexture`) cannot be fabricated without a context, so
   the common handle is likely an opaque/id-based type with the platform backend
   mapping it to the real GL object. Decided at the GL touch-point.
3. **Resource wrappers** (T1) for the GL objects (texture, program, shader,
   buffer, VAO), used by the renderer and by `Decal`.
4. **`Texture` type** definition/location (the renderer surface names it).
5. **Platform defaults**: LWJGL GL33 (JVM) and WebGL2 (web) implementations of
   `GLService`.

## Framing for the micro-plan

The renderer concept is common drawing logic over two seams: the `GLService`
(all GL commands, overridable, recording-testable) and the device (context/
present, external). Vertices, state, blend mapping and draw order are common and
pinned by recording tests; the GL call sequences and the resource lifecycle are
pinned by the recording backend plus the real-GL smoke tests. Every allocate/
close path and construction-failure branch follows the T1 discipline.
