package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.letClosingIfFailed
import dev.staticsanches.kge.resource.onCollectionObserved
import kotlin.io.encoding.Base64

/**
 * A font face backed by a native shaping engine, loaded from its own payload.
 *
 * Close releases the native face and the payload it stands on where the engine
 * allows; use after close fails fast.
 */
class Font private constructor(
    private val face: ResourceWrapper<NativeFace>,
) : KGEResource by face {
    /**
     * Shapes [text] as a single left-to-right Latin run at [sizePx] pixels:
     * each glyph carries its font id, pixel offset/advance and the index of the
     * code point it starts at.
     */
    fun shape(
        text: String,
        sizePx: Int,
    ): ShapedRun {
        checkNotReleased()
        require(sizePx > 0) { "sizePx must be positive: $sizePx" }

        val native = face.resource
        return ShapedRun(
            glyphs = native.shape(text.toCodePoints(), sizePx),
            metrics = native.metrics(sizePx),
        )
    }

    private fun checkNotReleased() {
        if (face.cleaned) {
            throw IllegalStateException("the font has been released and can not be used")
        }
    }

    /** Deterministic test seam: fires the platform collection trigger. */
    @OptIn(KGESensitiveAPI::class)
    internal fun onCollectionObserved() = face.onCollectionObserved()

    companion object {
        /** Loads a complete font payload. */
        fun load(bytes: ByteArray): Font =
            BufferService
                .allocate(bytes.size, "font")
                .letClosingIfFailed { storage ->
                    val buffer = storage.resource
                    for (index in bytes.indices) {
                        buffer.put(index, bytes[index])
                    }
                    Font(wrapNativeFace(createNativeFace(storage)))
                }

        /** Loads the chunked base64 a bundled data module emits. */
        fun load(base64: List<String>): Font = load(Base64.decode(base64.joinToString("")))
    }
}

/** Creates the resource that owns [face]; a failed hand-off closes [face] instead. */
@OptIn(KGESensitiveAPI::class)
private fun wrapNativeFace(face: NativeFace): ResourceWrapper<NativeFace> =
    try {
        ResourceWrapper("font face", face, KGECleanAction { closeNativeFace(face) })
    } catch (failure: Throwable) {
        try {
            closeNativeFace(face)
        } catch (closeFailure: Throwable) {
            failure.addSuppressed(closeFailure)
        }
        throw failure
    }

/** The text's Unicode code points, pairing a surrogate pair into one. */
private fun String.toCodePoints(): IntArray {
    val codePoints = ArrayList<Int>(length)
    var index = 0
    while (index < length) {
        val high = this[index]
        if (high.isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()) {
            codePoints +=
                SUPPLEMENTARY_CODE_POINT_OFFSET +
                ((high.code - Char.MIN_HIGH_SURROGATE.code) shl 10) +
                (this[index + 1].code - Char.MIN_LOW_SURROGATE.code)
            index += 2
        } else {
            codePoints += high.code
            index++
        }
    }
    return codePoints.toIntArray()
}

private const val SUPPLEMENTARY_CODE_POINT_OFFSET = 0x10000
