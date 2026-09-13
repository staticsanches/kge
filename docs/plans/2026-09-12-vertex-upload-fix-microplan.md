# Vertex upload fix micro-plan (interactive execution)

**Date:** 2026-09-12. Confirmed bug fix on the renderer's per-draw vertex
upload. Concept: replace the reused-staging `bufferSubData` overwrite with an
olc-style exact-size orphan `bufferData` per draw. No new public surface beyond
one exact-count command on the internal `GLService` seam.

## Problem

`DefaultRenderer.drawLayerQuad`/`drawDecal` upload the CPU staging `ByteBuffer`
with `GL.bufferSubData(GL.ARRAY_BUFFER, 0, data.resource)`. The CPU buffer's
capacity exceeds the bytes drawn, so each upload re-specifies a storage still
referenced by queued draws; the driver then syncs/re-uploads. Measured on JVM
GL33: ~96 µs/quad for the `bufferSubData` path vs ~1.2 µs/quad for the orphan
upload. Reference: olcPixelGameEngine `DrawDecal` does
`glBufferData(target, sizeof(vertex)*points, ptr, GL_STREAM_DRAW)` per draw.

## Confirmed seam + buffer + renderer changes

- **Seam.** `GLService.bufferData(target, srcData, byteCount, usage)` replaces
  the whole-buffer `bufferData(target, srcData, usage)` overload, which is
  removed; the `size`-allocation overload and `bufferSubData` stay. The
  companion `Proxy` forwards the new form and the KDoc states it orphan-uploads
  the first `byteCount` bytes.
- **Backends.** JVM limits a `duplicate()` view (`position(0)`,
  `limit(byteCount)`) and calls `GL33.glBufferData`. Web limits through a
  `Uint8Array(nativeBytes.buffer, 0, byteCount)` view.
- **`StagingBuffer`.** `ensureCapacity` now grows only the CPU buffer (no GL
  calls, no `letClosingIfFailed` GPU branch). New `upload(byteCount)` requires
  `byteCount in 0..capacityBytes` and emits `bindBuffer(ARRAY_BUFFER,
  staging.buffer)` then `bufferData(ARRAY_BUFFER, data, byteCount, STREAM_DRAW)`.
- **`DefaultRenderer`.** Both draws replace the explicit `bindBuffer` +
  `bufferSubData` pair with `quad.staging.upload(VertexLayout.BYTES * count)`;
  the recorded order is unchanged (`bindBuffer` then `bufferData`).

## TDD tests

- `StagingBufferTest`: `ensureCapacity` issues no `bufferData`; `upload(n)`
  records `bindBuffer` then `bufferData(ARRAY_BUFFER, data, n, STREAM_DRAW)`;
  an out-of-capacity `n` is rejected.
- `RendererDrawTest`: expected uploads are `bufferData(ARRAY_BUFFER, data,
  byteCount, STREAM_DRAW)` with `4 * VertexLayout.BYTES` (112) for the layer
  quad and `vertices * VertexLayout.BYTES` for decals; the consecutive-instance
  method list and the created-once/upload-per-draw test follow.
- `BufferDrawStateTest`: the exact-count overload records the byte count (16).
- The JVM/Web real-GL smoke tests still run real draws through the new path.

## Out of scope

Batching (state cache + one upload + per-quad/multi-draw `drawArrays`) remains
future work; this fix only stops the over-upload of the per-draw path.

## Gate

```bash
./gradlew build --rerun-tasks   # ktlint + all targets' tests + assemble/metadata
```
