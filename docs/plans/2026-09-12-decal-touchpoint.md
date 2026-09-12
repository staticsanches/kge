# Decal — touch-point material

**Date:** 2026-09-12. Pre-touch-point material for the decal concept — the first
of three concepts treated in this session (**decal → renderer/pipeline → GL
layer**), whose touch-points run in **reverse order** (top consumer first) so
each layer's requirements are collected by the layer above before it is
designed. Implementation runs in the forward order. Decisions closed in
discussion (owner) are recorded here; the micro-plan is TDD, written after the
three touch-points.

## Investigation summary

- **olc v2.30** (`.tmp/olc/olcPixelGameEngine.h`). `Decal` is the GPU texture
  owner (`id`, `width`, `height`, `sprite*`, `vUVScale`); `Update()` is
  CPU→GPU, `UpdateSprite()` is GPU→CPU; `Patch(...)` produces
  `DecalPatch{decal, coords[4]}`. Draw forms: `DrawDecal(pos, decal, scale,
  tint)`, `DrawPartialDecal(pos, decal, source_pos, source_size, scale, tint)`,
  `DrawPolygonDecal(decal, verts, uvs, tints)`, and `DrawDecal(pos, patch,
  scale)`. `DecalInstance` (variable-length geometry + `mode`/`structure`/
  `points`) is the internal renderer unit; `DecalMode`/`DecalStructure` are
  engine state; `Renderable` owns a `Sprite` + `Decal`. **No batching**: one
  `DrawDecal(instance)` per instance. `decal == nullptr` never occurs on a
  `DecalInstance` (all 11 production sites pass a non-null decal); the null
  lives on `GPUTask`, the **3D** path (`HW3D_DrawLine`/`HW3D_DrawLineBox`) —
  the `DrawDecal` null branches are defensive/dead.
- **`main` (evidence, not mandate).** Same `Decal` model, but `DecalInstance`
  batches via `multiDrawArrays`; no rotation and no `DecalPatch`. Its
  `DecalInstance.decal` is nullable, but both producers always pass a non-null
  decal: `BaseRenderer`'s `di.decal ?: quadInfo.blankDecal` is dead code and
  the 1×1 `blankDecal` is an allocated, unused resource (a leftover of the olc
  structure, where the null belongs to the 3D `GPUTask`).
- **Current kernel.** `Sprite` (`Pixmap.Mutable` + `RawBacked`, owns its
  `ByteBuffer` under the T1 contract), `SampleMode` (CPU sampling), `Flip`,
  `Pixel.Mode` (CPU blend), `BlitService`, `BufferService`, `ResourceWrapper`/
  `KGEResource`, `KGEOverridable`. The GL filter/wrap is orthogonal to the CPU
  `SampleMode`.

## Decided (2026-09-12, owner)

1. **`Decal` = GPU texture resource.** Owns the texture through the T1
   resource contract; carries no geometry. Created from a `Sprite` (RGBA) with
   the upload in the constructor. It does **not** own the `Sprite`; it keeps a
   reference for `update()` (CPU→GPU) and `updateSprite()` (GPU→CPU). olc/main
   parity.
2. **Draw surface — full olc parity.** `drawDecal(pos, decal, scale, tint,
   mode, structure)`, `drawPartialDecal(pos, decal, sourcePos, sourceSize,
   scale, tint, mode, structure)`, `drawPolygonDecal(decal, verts, uvs, tints,
   mode, structure)` (public), and `DecalPatch` + `drawDecal(pos, patch,
   scale, ...)`. "Warped decals" out of scope is narrowed: the arbitrary
   textured polygon is in; other warp forms stay out.
3. **Batching — this stage = olc.** Instances are collected per layer and
   drawn in order, one draw per instance, with texture bind and blend per run.
   The `multiDrawArrays` optimization is recorded as **future work**, gated on a
   benchmark measuring real gains; a possible shape is an alternative default
   renderer implementation, consumer-selectable through the overridable service
   mechanism.
4. **`mode`/`structure` — required per-call parameters, no state, no default.**
   The kernel stays stateless (adherent to the other services). The
   developer-facing default lives in the engine/state layer (later concept) as
   an immutable `DecalStyle(mode, structure)` forwarded by the engine draw
   facade, with an optional per-call override and an optional scoped helper.
5. **`Decal.Mode { NORMAL, ADDITIVE, MULTIPLICATIVE, STENCIL, ILLUMINATE,
   WIREFRAME }`** and **`Decal.Structure { LINE, FAN, STRIP, LIST }`**, nested.
   The exact `glBlendFunc` mapping is a renderer/GL requirement.
6. **`Decal.Filter { NEAREST, LINEAR }`** and **`Decal.Wrap { CLAMP_TO_EDGE,
   REPEAT }`**, nested, required (no defaults). Closed to the oracle's scope: the
   GPU has more values (mipmap filters, mirrored repeat, clamp-to-border), but
   olc/main use only these and mipmaps are out of scope; adding values is a
   widening done with a consumer and a test, and the behavior seam is the
   overridable GL backend.
7. **Geometry lives in common overridable draw services** producing
   `DecalInstance`s (the `main` `DrawDecalService`/`DrawPartialDecalService`
   analogue); the renderer consumes instances and only uploads/draws. Pure
   math, testable through the recording backend, separate from GL.
8. **`DecalInstance`** is the geometry unit: non-null `decal`, per-vertex
   `pos`/`uv`/`tint`, plus `mode`/`structure`; the vertex count derives from the
   lists (no redundant `points`). No `w`/depth/3D. Public as the contract
   between the draw services and the renderer (not the primary user API).
9. **No `DecalService` and no `Renderable` now** (YAGNI): the `Decal` uses the
   overridable renderer service; an owner of sprite+decal lands with its
   consumer (layers).
10. **Geometry parity** (clip-space conversion, quantization, UV epsilon,
    per-vertex tint) follows olc and is pinned by tests.

## 3D note (owner intent)

The owner intends to add 3D support to the engine later. The 2D `DecalInstance`
stays non-null; the "untextured" case belongs to the future 3D task type (the
olc `GPUTask` analogue), decided at the 3D concept's touch-point — as a sealed
source (`Textured`/`Solid`) or a renderer-owned blank. Nothing is pre-added to
the 2D concept for it, and `main`'s dead blank decal is not ported.

## Requirements pushed to the renderer (input to the next touch-point)

1. **Texture lifecycle** under T1: create (`width`, `height`, `filter`,
   `wrap`), update from a `Sprite` (CPU→GPU), read back to a `Sprite`
   (GPU→CPU), delete.
2. **Apply/bind** a texture.
3. **Draw a `DecalInstance`**: N vertices (`pos`/`uv`/`tint`), `mode` → blend,
   `structure` → primitive.
4. **Viewport/clear/present** (renderer level).
5. The renderer service must be resolvable before decal creation (the `Decal`
   constructor path goes through it).

## Framing for the micro-plan

The decal concept is a resource type plus stateless common geometry services
and the `DecalInstance` contract; the only GL-touching part is the renderer
consumption, which stays behind the renderer service. The micro-plan is TDD
over the geometry services (pure, recording-tested) and the resource lifecycle
(fail-fast, leak audit on every allocate/close path and construction-failure
branch).
