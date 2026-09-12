# GL layer micro-plan (interactive execution)

**Date:** 2026-09-12. Touch-point decisions in
`docs/plans/2026-09-12-gl-layer-touchpoint.md`. Concept: the raw, overridable
`GLService` (GL commands), the `object GL` namespace + `GLenum` constants, the
`expect class` handles, the T1 resource wrappers (`Texture` public; program/
shader/buffer/VAO internal), the platform defaults (LWJGL GL33 / WebGL2) and the
recording backend. This is the first concept of the three (implemented before
the renderer and the decal).

## Working mode

- **No subagents.** One step = test (red) → implement (green on jvm+js+wasmJs) →
  mark. Doubts resolved in-session with the owner.
- **Oracle.** The recording backend (a `GLService` implementation in
  `commonTest`) pins the command surface; the real-GL smoke (spike seed) pins the
  platform defaults. Command/constant parity with `main`
  (`git show main:kge-core/src/commonMain/kotlin/dev/staticsanches/kge/renderer/gl/...`).
- **Raw/thin.** `GLService` takes `GLenum` ints; the typed mapping belongs to the
  renderer, not here.

## Contract (touch-point)

- **Handles:** `expect class GLTexture/GLProgram/GLShader/GLBuffer/
  GLVertexArrayObject/GLUniformLocation`. The common code only transports them.
  JVM tries `@JvmInline actual value class ... (val id: Int)`; falls back to a
  thin `actual class`/`Int` if the modality check rejects it.
- **`GLService : KGEOverridable`**, `internal expect val glServiceDefault`
  (JVM LWJGL `GL33`, web WebGL2), companion `Proxy`. Surface: texture
  (`createTexture`/`deleteTexture`/`bindTexture`/`texImage2D`/`texSubImage2D`/
  `texParameteri`/`readPixels`); shader/program (`createShader`/`deleteShader`/
  `shaderSource`/`compileShader`/`createProgram`/`deleteProgram`/`attachShader`/
  `linkProgram`/`useProgram`/`getUniformLocation`(`-1`→`null`)/`uniform1i`);
  buffer/VAO/attribute (`createBuffer`/`deleteBuffer`/`bindBuffer`/`bufferData`/
  `bufferSubData`/`createVertexArray`/`deleteVertexArray`/`bindVertexArray`/
  `vertexAttribPointer`/`enableVertexAttribArray`); draw/state (`drawArrays`/
  `enable`/`disable`/`blendFunc`/`clearColor`/`clear`/`viewport`). Compile/link
  failures are checked inside the backend and thrown with the info log.
- **`object GL : GLService by GLService`** — the common `const val` constants and
  the user namespace; delegates per call to the seam. No resolved instance.
- **Recording backend** in `commonTest` + `expect`/`actual` test factories
  (`jvmTest` returns `Int`; `webTest` returns a dummy JS object via
  `unsafeCast`) — the handles are fabricated without a context.
- **`Texture`** is the T1 resource wrapper (public); program/shader/buffer/VAO
  wrappers are `internal`.

## Resume tracker

- [x] **0.** Handles + `GL` constants + `GLService` skeleton + recording backend
- [x] **1.** Texture ops + `Texture` (T1)
- [x] **2.** Shader/program ops
- [x] **3.** Buffer/VAO/attribute/draw/state ops
- [x] **4.** Platform defaults (LWJGL GL33 / WebGL2) + real-GL smoke
- [x] **5.** Gate + review + decisions-log close

## Steps

### 0. Handles + constants + skeleton + recording backend

- **Tests (red):** a recording `GLService` override is picked up by `GL.*` and
  `GLService.*` (extension-contract test); `resetAll` restores the default; the
  fabricated handles round-trip through the recorder.
- **Files:** `commonMain/.../renderer/gl/GLTypes.kt` (expect handles),
  `.../gl/GL.kt` (constants + object), `.../gl/service/GLService.kt` (interface +
  companion + `expect val`), platform `actual` handle defaults; `commonTest`
  recorder + `expect` factories + `jvmTest`/`webTest` actuals.
- **Decided:** handles `expect class`; JVM `@JvmInline actual value class` if the
  modality check allows, else `actual class`/`Int`; `GL` holds the raw `const val`
  constants.

### 1. Texture ops + `Texture`

- **Tests:** `createTexture` returns a `Texture`; `update`/`read`/`apply` record
  the right calls; `close()` deletes exactly once (idempotent), use-after-close
  fails fast; a construction failure between create and wrapper close frees the
  texture (`letClosingIfFailed`); the leak path is exercised.
- **Files:** `commonMain/.../renderer/gl/resource/Texture.kt` + the GL texture
  ops in `GLService`.
- **Decided:** `Texture` is the only public wrapper here.

### 2. Shader/program ops

- **Tests:** create/compile/attach/link/use record in order; a compile failure
  throws with the info log (backend-internal check); `getUniformLocation`
  normalizes `-1`→`null`.
- **Decided:** program/shader wrappers are `internal` (the renderer's built-in
  program is their first consumer).

### 3. Buffer/VAO/attribute/draw/state ops

- **Tests:** buffer/VAO/attrib calls record; `drawArrays`, `blendFunc`,
  `clear`, `viewport` record with the expected arguments.
- **Decided:** no `multiDrawArrays`, no uniforms beyond `uniform1i`, no FBO/getError
  (deferred — touch-point).

### 4. Platform defaults + real-GL smoke

- **Tests:** the spike smoke (hidden GLFW + FBO readback on JVM; WebGL2 canvas on
  web) promoted to the seed harness, exercised through the real platform defaults
  rather than raw LWJGL/`web.gl`. macOS skips the JVM GL smoke (decisions-log
  chunk 21); the web smoke runs on all three runners.
- **Files:** `jvmMain`/`webMain` `GLService` actuals; `jvmTest`/`webTest` device
  helper (promote `GlTestContext`).
- **CI:** the existing workflow (Xvfb+Mesa, `setup-mesa-dist-win`) already
  provisions the software GL; verification is still pending a pushed run.

### 5. Gate + review + close

- **Gate:** `./gradlew build --rerun-tasks` green (all targets + ktlint +
  metadata/assemble). Leak audit of every allocate/close path + construction
  failure branch; no-parameter-without-observable-effect audit. Two-axis review
  (Standards + Spec, two fresh general sub-agents). Decisions-log close entry.

## Files

New: `renderer/gl/GLTypes.kt`, `renderer/gl/GL.kt`,
`renderer/gl/service/GLService.kt`, `renderer/gl/resource/Texture.kt`, JVM/web
`actual`s, `commonTest` recording backend + `expect` factory, `jvmTest`/`webTest`
actual factories and the promoted device helper.

Out of scope: `multiDrawArrays`, uniforms beyond the sampler, user shaders,
FBO/renderbuffer, `getError`, native (Kotlin/Native) backends.
