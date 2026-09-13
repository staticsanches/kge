package dev.staticsanches.kge.image

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.applyClosingIfFailed
import dev.staticsanches.kge.resource.letClosingIfFailed

/** Reads [this]'s pixels row-major (storage order), for comparison with [tinyPngPixels]. */
internal fun Sprite.rowMajorPixels(): List<Pixel> =
    (0 until height).flatMap { y -> (0 until width).map { x -> uncheckedGet(x, y) } }

/**
 * The shared fixture: a 2x2 RGBA8 PNG (bit depth 8, color type 6, no
 * interlace) whose four pixels are distinct and carry non-trivial alpha,
 * generated offline and byte-verified (signature, per-chunk CRC32, zlib round
 * trip). Embedded so every test target decodes the same input.
 */
internal val tinyPngBytes: ByteArray =
    byteArrayOf(
        -119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 0, 2,
        0, 0, 0, 2, 8, 6, 0, 0, 0, 114, -74, 13, 36, 0, 0, 0, 22, 73, 68, 65,
        84, 120, -38, 99, -8, -49, -64, -16, 31, 8, 27, 24, -128, 52, 16, 48, 52, 0, 0, 66,
        85, 7, -6, -103, -125, -104, -34, 0, 0, 0, 0, 73, 69, 78, 68, -82, 66, 96, -126,
    )

/** The base64 payload of [tinyPngBytes], for the base64 source and platform I/O tests. */
internal val tinyPngBase64: String =
    "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFklEQVR42mP4z8DwHwgbGIA0EDA0AABCVQf6mYOY3gAAAABJRU5ErkJggg=="

/** The row-major pixels [tinyPngBytes] decodes to: red opaque, green 50%, blue opaque, yellow 50%. */
internal val tinyPngPixels: List<Pixel> =
    listOf(
        Pixel.rgba(255, 0, 0, 255),
        Pixel.rgba(0, 255, 0, 128),
        Pixel.rgba(0, 0, 255, 255),
        Pixel.rgba(255, 255, 0, 128),
    )

/** Bytes that are not a PNG — decode must throw on them. */
internal val notAPngBytes: ByteArray =
    byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)

/** Wraps [bytes] in an engine buffer the caller owns and must close. */
internal fun ByteArray.asEngineBuffer(): ResourceWrapper<dev.staticsanches.kge.buffer.ByteBuffer> =
    BufferService.allocate(size).letClosingIfFailed { wrapper ->
        val buffer = wrapper.resource
        for (i in indices) {
            buffer.put(i, this[i])
        }
        wrapper
    }

/**
 * A freshly created 2x2 sprite with four distinct pixels (the same palette as
 * the fixture) — the encode/decode round-trip oracle, independent of the
 * fixture bytes. The caller owns and must close the sprite.
 */
internal fun distinctSprite(): Sprite =
    SpriteService
        .create(2, 2, Pixmap.SampleMode.NORMAL, null)
        .applyClosingIfFailed {
            set(0, 0, Pixel.rgba(255, 0, 0, 255))
            set(1, 0, Pixel.rgba(0, 255, 0, 128))
            set(0, 1, Pixel.rgba(0, 0, 255, 255))
            set(1, 1, Pixel.rgba(255, 255, 0, 128))
        }
