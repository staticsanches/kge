package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.onCollectionObserved
import kotlin.io.encoding.Base64

/**
 * A font face backed by a native shaping engine, loaded from its own payload.
 *
 * Close releases every per-size glyph atlas and then the native face and the
 * payload it stands on where the engine allows; use after close fails fast.
 */
class Font private constructor(
    private val face: ResourceWrapper<NativeFace>,
) : KGEResource {
    private val atlasesBySizePx = mutableMapOf<Int, GlyphAtlas>()

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

    /** The glyph's atlas entry at [sizePx], rasterizing into a new atlas on first use. */
    internal fun glyph(
        sizePx: Int,
        glyphId: Int,
    ): AtlasGlyph {
        checkNotReleased()
        require(sizePx > 0) { "sizePx must be positive: $sizePx" }

        val atlas =
            atlasesBySizePx.getOrPut(sizePx) {
                GlyphAtlas(sizePx) { face.resource.rasterize(it, sizePx) }
            }
        return atlas.glyph(glyphId)
    }

    /** The atlas of [sizePx], null until a glyph has been rasterized at that size. */
    internal fun atlas(sizePx: Int): GlyphAtlas? = atlasesBySizePx[sizePx]

    override fun close() {
        val toClose = mutableListOf<KGEResource>()
        toClose += atlasesBySizePx.values
        toClose += face
        atlasesBySizePx.clear()
        toClose.closeAll()
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
        suspend fun load(bytes: ByteArray): Font = Font(wrapNativeFace(openNativeFace(bytes)))

        /** Loads the chunked base64 a bundled data module emits. */
        suspend fun load(base64: List<String>): Font = load(Base64.decode(base64.joinToString("")))
    }
}

/**
 * Allocates the payload, copies [bytes] into it and opens the face, closing the
 * payload on any failure: the inline guard can not host a suspend call.
 */
private suspend fun openNativeFace(bytes: ByteArray): NativeFace {
    val storage = BufferService.allocate(bytes.size, "font")
    try {
        val buffer = storage.resource
        for (index in bytes.indices) {
            buffer.put(index, bytes[index])
        }
        return createNativeFace(storage)
    } catch (failure: Throwable) {
        try {
            storage.close()
        } catch (closeFailure: Throwable) {
            failure.addSuppressed(closeFailure)
        }
        throw failure
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
