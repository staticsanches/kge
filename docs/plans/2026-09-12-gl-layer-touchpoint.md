# GL layer — touch-point material

**Date:** 2026-09-12. Pre-touch-point material for the GL layer — the third of
three concepts treated this session (**decal → renderer/pipeline → GL layer**),
touch-points in **reverse order**; implementation runs in the forward order (GL
layer → renderer/pipeline → decal). Decisions closed in discussion (owner) are
recorded here; the micro-plan is TDD, written after the three touch-points.

## Investigation summary

- **`main` (evidence, not mandate).** `GLService` was a `KGEExtensibleService`
  interface — a thin 1:1 wrapper of the GL commands grouped as texture, shader,
  program, uniform, attribute, VAO, buffer, draw (`drawArrays`/
  `multiDrawArrays`) and state (`enable`/`disable`, `blendFunc`, `clearColor`,
  `clear`, `viewport`, `depthFunc`). A `data object GL : GLService by GLService`
  provided the user namespace plus ~90 common `GLenum` constants. `GLTypes` were
  `expect class` handles; JVM mapped them to `Int`, JS to the `web.gl` DOM
  types. Resources were `ResourceWrapper<GL*>` (texture/program/shader/buffer/
  VAO), with uniform/vertex-attribute extensions; `getUniformLocation` /
  `getAttribLocation` normalized `-1` to `null`.
- **Current kernel.** `BufferService` is the service pattern: a common
  `KGEOverridable` interface + `internal expect val` platform default
  (LWJGL/native web). The T1 resource contract and `letClosingIfFailed` are in
  place. The GL spike (hidden GLFW/WebGL2 smoke tests) is the harness seed.

## Decided (2026-09-12, owner)

1. **Handles are `expect class`** (`GLTexture`, `GLProgram`, `GLShader`,
   `GLBuffer`, `GLVertexArrayObject`, `GLUniformLocation`). The common code
   never constructs a handle — it only transports them (exactly what the
   renderer and `Decal` do). The recording backend lives in `commonTest` and
   fabricates handles through a tiny `expect`/`actual` **test factory**
   (`recordingTextureHandle(seed)`, …): the JVM returns an `Int`; the web
   returns a dummy JS object via an unchecked cast
   (`js("{}").unsafeCast<WebGLTexture>()`), no context needed. On the JVM, try
   `@JvmInline actual value class ... (val id: Int)` for type safety and zero
   overhead; if the modality check rejects it (as it did for `ByteBuffer`), fall
   back to a thin `actual class` or `Int`.
2. **The `GLService` is raw and thin**: commands take `GLenum` integers; the
   typed boundary (Decal.Mode→blendFunc, Structure→primitive, Filter/Wrap→
   parameters) lives in the common renderer. Platform backends stay near 1:1
   with the GL API (minimum platform code).
3. **`object GL : GLService by GLService`** holds the common `GLenum` constants
   (`const val`) and is the user namespace (`GL.bindTexture(...)`,
   `GL.SRC_ALPHA`). It delegates per call to the overridable `GLService`
   companion, so it holds no resolved instance and does not violate the facade
   contract. `GLService` remains the seam; overriding it drives `GL`.
4. **`GLService : KGEOverridable`** with `internal expect val glServiceDefault`:
   JVM = LWJGL `GL33`, web = WebGL2 (`kotlin-browser`). `resetAll` clears a test
   override, as with every service.
5. **Command surface** (all raw GLenums):
   - Texture: `createTexture`, `deleteTexture`, `bindTexture`, `texImage2D`,
     `texSubImage2D`, `texParameteri`, `readPixels`.
   - Shader/program: `createShader`, `deleteShader`, `shaderSource`,
     `compileShader`, `createProgram`, `deleteProgram`, `attachShader`,
     `linkProgram`, `useProgram`, `getUniformLocation` (`-1`→`null`),
     `uniform1i` (the sampler). Compile/link failure is checked **inside the
     backend** and thrown with the info log.
   - Buffer/VAO/attribute: `createBuffer`, `deleteBuffer`, `bindBuffer`,
     `bufferData`, `bufferSubData`, `createVertexArray`, `deleteVertexArray`,
     `bindVertexArray`, `vertexAttribPointer`, `enableVertexAttribArray`.
   - Draw/state: `drawArrays`, `enable`/`disable`, `blendFunc`, `clearColor`,
     `clear`, `viewport`.
6. **Resource wrappers (T1).** `Texture` is **public** (the `Decal` owns it);
   the program/shader/buffer/VAO wrappers are **internal** to the renderer (the
   built-in program), opened when user shaders land. Deletion goes through
   `close()`.
7. **Deferred with a note:** `multiDrawArrays` (the future renderer
   optimization, benchmark-gated), uniforms beyond `uniform1i` (future user
   shaders), FBO/renderbuffer (none — layers are CPU sprites), `getError`.
8. **Platform note.** `-XstartOnFirstThread` stays a **test-only** flag on
   macOS; the production main-thread rule belongs to the engine loop (later
   concept).

## Requirements pushed to the renderer (already recorded there)

The renderer consumes `Texture` (T1), `create/update/read/apply/delete`, and
`drawArrays` via the built-in program built from the shader/program/buffer/VAO
operations. The typed→GL mapping is the renderer's; the GL layer stays raw.

## Framing for the micro-plan

Two seams: `GLService` (all GL commands, overridable, recording-testable) and
the device (context/present, external). The GL layer's own tests pin the
recording backend wiring, the resource wrappers' lifecycle (fail-fast,
leak-audited allocate/close paths and construction-failure branches) and the
real-GL smoke path. The GL constants are data, not a service.
