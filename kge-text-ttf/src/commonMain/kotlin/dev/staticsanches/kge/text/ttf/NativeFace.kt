package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.resource.ResourceWrapper

/**
 * An open native face standing on an engine-owned font payload, released no
 * later than the face's close. Built by the seam's factory, never by callers.
 */
internal expect class NativeFace {
    fun shape(
        codePoints: IntArray,
        sizePx: Int,
    ): List<ShapedGlyph>

    fun metrics(sizePx: Int): TextMetrics
}

/**
 * Opens [bytes] as its first face and validates it; a payload that is not a
 * usable font is rejected and the face does not own the payload then.
 */
internal expect fun createNativeFace(bytes: ResourceWrapper<ByteBuffer>): NativeFace

/** Releases the native handles; the wrapper runs it at most once. */
internal expect fun closeNativeFace(face: NativeFace)
