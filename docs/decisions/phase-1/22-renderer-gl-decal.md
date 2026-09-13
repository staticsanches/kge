## 2026-09-12 — Renderer/GL/decals (C9): touch-points + close

`C9` (renderer/GL/decals, roadmap `R5 → R4 → R3`) was treated as three concepts
in one session, each with its own touch-point. The touch-points ran in **reverse
order** (decal → renderer → GL layer) so every layer's requirements were
collected by its consumer before it was designed; implementation ran in the
forward order (GL layer → renderer → decal). Design material:
`docs/plans/2026-09-12-{decal,renderer,gl-layer}-touchpoint.md` and the matching
`-microplan.md` files.

### Ordering ruling (owner)

The renderer's surface references decal types (`DecalInstance`,
`Decal.Filter/Wrap/Mode/Structure`) while the `Decal` resource uses the
renderer's `createTexture` — a type cycle inside one module. The owner ruled:
implement the pure decal types (`Decal` nested enums + `DecalInstance`, plus a
`Decal` shell) as a prerequisite before the renderer, accept the cycle, and keep
`DecalInstance.decal` typed as a non-null `Decal` (not the GL `Texture`). The
`Decal` shell was completed in the decal step 1.

### GL layer

- **Handles are `expect class`** (`GLTexture`/`GLProgram`/`GLShader`/`GLBuffer`/
  `GLVertexArrayObject`/`GLUniformLocation`); common code only transports them.
  **Verified:** `@JvmInline actual value class` cannot match `expect class` (the
  modality check rejects it), so the JVM uses a thin `actual class (val id: Int)`
  and web keeps the `web.gl` DOM `actual typealias`. The JVM handle constructors
  are public and `@KGESensitiveAPI`.
- **`object GL : GLService by GLService`** holds the raw `GLenum` constants and
  is the user namespace; it delegates per call to the overridable seam and holds
  no resolved instance.
- **`GLService : KGEOverridable`**, raw/thin (`GLenum` ints; the typed mapping
  lives in the renderer), default `internal expect val glServiceDefault` — JVM
  LWJGL `GL33`, web WebGL2 (`kotlin-browser`). Surface covers texture,
  shader/program, buffer/VAO/attribute, draw and state; compile/link failures
  are checked in the platform backend and thrown with the info log;
  `getUniformLocation` normalizes `-1` to `null`.
- **Texture readback.** The touch-point listed only `readPixels` (framebuffer
  readback), which cannot read a texture. Post-review, a `getTexImage` command
  was added (JVM `glGetTexImage`; web attaches an internal FBO and `readPixels`)
  so `Texture.read`/`Decal.updateSprite` work; `readPixels` remains for
  framebuffer readback in the smoke tests. No public FBO API.
- **Resource wrappers.** `Texture` is public (T1); program/shader/buffer/VAO
  wrappers are internal to the renderer.
- **Oracle.** A common recording `GLService` (with a small platform handle
  factory: JVM fabricates ids, web needs a real headless WebGL2 context) is the
  primary test oracle; the real-GL smoke (hidden GLFW + FBO readback / WebGL2
  canvas) is a smoke test. A real-GL smoke surfaced and fixed a wasmJs bug in the
  web backend (`== true` status checks are false on Kotlin/Wasm).
- **Deferred:** `multiDrawArrays`, uniforms beyond the sampler, user shaders,
  FBO/renderbuffer public API, `getError`.

### Renderer/pipeline

- **`Renderer : KGEOverridable, AutoCloseable`** with a common default; no
  window and no layers (the engine owns layers). Surface: `createTexture`,
  `updateTexture`, `readTexture`, `applyTexture`, `prepareDrawing`,
  `drawLayerQuad`, `drawDecal(instance)`, `clearBuffer`, `updateViewport`; no
  parameter defaults.
- **Device seam.** `GpuDevice` (`makeCurrent`/`present`) is public; the renderer
  operates on an externally owned current context; platform and test impls are
  internal. `present` is the former `displayFrame`, owned by the device.
- **Common renderer over the `GLService` seam**: vertices, blend and draw order
  are common and recording-tested; all GL calls go through `GLService`.
- **Internals:** one built-in quad program (GLSL `330 core` JVM / `300 es` web,
  via an `internal expect val glslVersion`) created through the general GL
  ops — user shaders are not precluded; a dynamic staging buffer (the
  `KGEConfiguration` global is rejected); no FBO/render target. `close()`
  releases the built-in resources (the engine closes it on destroy).

### Decal

- **`Decal` = GPU texture resource.** Created from a `Sprite` (RGBA) through the
  overridable `Renderer`, owns the `Texture`, does not own the `Sprite`;
  `update()`/`updateSprite()` by reference; `close()` is the single release path.
  `Decal.texture` is public `@KGESensitiveAPI` (an external renderer needs it).
- **`DecalInstance`** carries a non-null `Decal`, per-vertex `pos`/`uv`/`tint`,
  `mode`, `structure`; the vertex count derives from the lists; no `w`/depth.
  Non-parallel vertex lists fail fast.
- **Nested closed enums** `Decal.Mode`/`Structure`/`Filter`/`Wrap` (the GPU has
  more values, but the oracle uses only these and mipmaps are out of scope).
- **Draw services** are stateless `KGEOverridable` services (portable defaults)
  returning a `DecalInstance`: `drawDecal`, `drawPartialDecal`,
  `drawPolygonDecal`, plus `DecalPatch` + `drawDecal(pos, patch, scale)`. `mode`
  and `structure` are required; the viewport size is an explicit parameter.
- **Geometry parity.** Clip-space conversion, the `DrawPartialDecal`
  quantization and `0.0001` UV epsilon follow olc. Review correction: the
  `DecalPatch` draw uses the quad olc actually passes to `DrawPolygonDecal`
  (`verts` + patch UVs); olc's computed-and-discarded `transformed` vector is
  **not** used.
- **3D note.** The owner intends 3D later; untextured geometry belongs to the
  future 3D task type, not the 2D `DecalInstance`. `main`'s dead blank decal is
  not ported.

### Review and gate

Two-axis review (Standards + Spec, fresh sub-agents; reports in
`.opencode/reviews/gl-renderer-decal-*.md`): 1 Critical + 3 Important + minors.
Fixed in two rounds, plus an owner-flagged extension issue (public APIs must not
carry `internal` members/constructors an extender needs): the `DecalPatch`
constructor, `Decal.texture`, the web `GLContext` bind/read, and the JVM handle
constructors are public `@KGESensitiveAPI`. Scoped re-review: all findings
addressed, no new breakage. Gate: `./gradlew build --rerun-tasks` green (JVM +
js browser + wasmJs browser + ktlint + assemble/metadata).

### Carry-forward

- The `multiDrawArrays` batching optimization (benchmark-gated; possibly an
  alternative default `Renderer` selected through the overridable service).
- User-shader API (the GL seam already exposes the primitives).
- 3D support (own task type for untextured geometry).
- `getTexImage`'s `width`/`height` are only observable on web (documented).
- `Decal` leak reporting surfaces through its owned `Texture` (single report).

### Correction (2026-09-12) — renderer is stateless; resources live in a scope

The close above first shipped the default renderer as a stateful process-global
service: `DefaultRenderer` held the built-in quad (program, VAO, staging) in a
lazy mutable field and exposed `close()`. That is a defect, not a trade-off. A
fixed engine service must not carry mutable state; and GPU objects are bound to
the context/thread that created them, so a process-global cache goes stale when
the context is recreated and its teardown races engine destroy. Owner ruling: a
solution with state in a service is not acceptable — corrected here, not
carried as a new concept. It supersedes the `Renderer : KGEOverridable,
AutoCloseable` bullet and the `close()` sentence in Renderer/pipeline above.

- **`ResourceScope` + nested `Key` marker (T1).** `ResourceScope : KGEResource` is the
  engine-owned holder: typed `register(key, resource)` / `get(key)`, close in
  reverse registration order (LIFO — dependents close first), idempotent, and it
  keeps closing past a failure (later throwables suppressed). It owns no
  context; a resource that needs one makes it current in its own `close()`.
- **Renderer is stateless.** `Renderer : KGEOverridable` no longer extends
  `AutoCloseable`; `createResources(device, scope)` builds the built-in quad
  into the caller's scope once at startup (context current), and
  `prepareDrawing`/`drawLayerQuad`/`drawDecal` take the scope and resolve the
  quad through it. An override defines its own keys and resources.
- **Device carries the context for teardown.** `BuiltInQuad` holds the
  `GpuDevice` and calls `makeCurrent()` before releasing, so deletion runs on
  the context that created the objects even if the scope is closed by a caller
  that has no device. The scope must close before the window/context is
  destroyed (nesting: window → scope → resources).
- **Ownership.** The engine (C10) creates the scope, calls `createResources`,
  and closes the scope on destroy; tests use the same shape. A `RecordingGpuDevice`
  (common test seam) plus the recording `GLService` pin the stateless flow;
  `ResourceScopeTest` pins the scope contract.

### Correction (2026-09-12) — per-draw vertex upload orphans the buffer

The renderer shipped a per-draw upload that diverged from olc: both draws
issued `bufferSubData(ARRAY_BUFFER, 0, staging)` on the reused staging buffer,
whose CPU capacity can exceed the bytes drawn and whose storage can still be
referenced by queued draws — a driver sync plus over-upload. Measured on JVM
GL33, ~96 µs/quad. olc's `DrawDecal` instead does
`glBufferData(target, sizeof(vertex) * points, ptr, GL_STREAM_DRAW)` per draw
(orphan, exact size), ~1.2 µs/quad.

- **Seam.** `bufferData(target, srcData, byteCount, usage)` replaces the
  whole-buffer `bufferData(target, srcData, usage)` (removed); the JVM limits
  the view (`duplicate`/`position`/`limit`), the web builds a
  `Uint8Array(buffer, 0, byteCount)`. The size-only `bufferData(target, size,
  usage)` and `bufferSubData` stay.
- **`StagingBuffer`.** `ensureCapacity` grows only the CPU arena; `upload(byteCount)`
  binds the buffer and orphan-uploads exactly `byteCount` bytes with
  `GL.STREAM_DRAW`. `DefaultRenderer` uses it for the layer quad (`4 * BYTES`)
  and each decal (`vertices * BYTES`).
- **Review lesson.** Both C9 review axes checked the plan and `main`, not the
  olc draw path, so a plan-level defect (wrong buffer strategy) passed. The
  review process now mandates a behavior-parity check against the olc reference,
  with divergences accepted only when a rationale is recorded (AGENTS.md).

### Correction (2026-09-12) — `Texture.update` re-specifies with `texImage2D`

`Texture.update` issued `texSubImage2D` at `(0, 0)` with the sprite's
dimensions over the storage `Texture.create` had allocated. That is `main`'s
`BaseRenderer.updateTexture`; olc's `UpdateTexture` issues
`glTexImage2D(TEXTURE_2D, 0, RGBA, spr->width, spr->height, 0, RGBA,
UNSIGNED_BYTE, data)`, re-specifying (and resizing) the texture from the sprite.

- **Fix.** `update` uses the seam's `texImage2D`; `create` still allocates a
  null texture so a texture exists before an update, and `read`/`apply` are
  unchanged. The engine path is unaffected (a `Sprite`'s size is fixed and
  `Decal` sizes its texture from the sprite), but a public
  `Renderer.updateTexture`/`Texture.update` with a differently sized sprite now
  resizes instead of leaving stale texels.
- **Tests.** `TextureTest` expects `texImage2D` and pins the re-specify at a
  differing sprite size; the renderer and decal recording expectations were
  updated to the same call.
- **Seam.** `texSubImage2D` stays on `GLService` as a general command, like the
  retained size-only `bufferData`/`bufferSubData`; `update` no longer calls it.

### Correction (2026-09-12) — decal vertex `z`/`w` match olc

`DefaultRenderer.drawDecal` wrote the built-in vertex `z = 0`, `w = 1`. olc's
GL33 `DrawDecal` writes `{pos.x, pos.y, decal.w[i], 0}` with the CPU
`di.w = {1, 1, 1, 1}` (`olcPixelGameEngine.h:6240`, `:3571`), so `z = 1`,
`w = 0` — the same values the layer quad already used. The built-in shader
ignores both components, so this is a byte-layout parity fix, not an observable
one; `RendererDrawTest` now pins `1f, 0f` for decal vertices.

### Post-C9 parity audit — accepted divergences and limitations

A follow-up audit compared the raster, surface and renderer code against olc
v2.30 and closed the remaining candidates: each is recorded here as a decision
rather than left as an open finding (the same audit corrected `sampleBL`, the
texture upload and the decal vertex above).

- **Blit `scale <= 0` paints nothing (accepted).** `BlitService` and
  `RasterizerTest` pin it. olc `DrawSprite` sends `scale == 0` to the `else`
  (1:1) branch because its `scale` is `uint32_t` (`:3369`); KGE's `scale` is a
  signed `Int`, so a non-positive guard is deliberate and the olc zero case is
  an unsigned-type accident (a negative scale is not representable in olc).
- **Wrap maps to `GL_CLAMP_TO_EDGE` (accepted).** Desktop olc uses `GL_CLAMP`
  (`:6284`) and swaps to `GL_CLAMP_TO_EDGE` only under Emscripten (`:1781`);
  `GL_CLAMP` does not exist in the core profile KGE targets, and
  `CLAMP_TO_EDGE` is the olc-web value, so the mapping is the only valid one.
- **Decal reads live `sprite.width`/`height` (no-op).** olc caches the decal's
  `width`/`height` and a UV scale; KGE reads the `Sprite`'s `val` dimensions
  when it builds the geometry. A `Sprite`'s size is immutable, so the values
  coincide and the divergence has no observable path.
- **`fillTriangle` edge math is `Int` (accepted limitation, not an olc
  divergence).** The half-open edge test overflows far from the origin
  (products of coordinate deltas, roughly beyond ±20k); the primitive assumes
  screen-scale coordinates, as olc's screen-space scanline also does. No
  change: the per-pixel test is the hot path and switching to `Long` (emulated
  on web) is a measured-performance risk without a benchmark.
