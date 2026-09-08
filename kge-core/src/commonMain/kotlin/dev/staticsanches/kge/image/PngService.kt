package dev.staticsanches.kge.image

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.buffer.byteAt
import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.resource.ResourceWrapper
import kotlin.io.encoding.Base64

/**
 * Decodes and encodes PNG surfaces to and from [Sprite]s.
 *
 * The codec is an extension capability of the engine with a platform default:
 * the JVM default is STB (`stbi_load_from_memory`/`stbi_write_png_to_func`),
 * the web default is pngjs — both producing and consuming the raw R,G,B,A
 * row-major bytes a [Sprite] already stores, so decoding and encoding move
 * bytes without per-pixel conversion. A consumer may replace the codec for
 * the whole process via [override][KGEOverridable.Proxy.override].
 *
 * [decode] and [encode] are synchronous; only [load] is suspend — reading a
 * [PngSource] is the async boundary. The returned surfaces and buffers are
 * engine resources: the caller closes them.
 */
interface PngService : KGEOverridable {
    /**
     * Decodes the PNG bytes in [data] into a [width]x[height] RGBA surface
     * born with [sampleMode] and [name] ([Sprite]'s diagnostic label).
     *
     * [data] holds exactly the PNG payload, read from its start — decode does
     * not depend on any (JVM) buffer position. The caller keeps ownership and
     * must close it. Throws when [data] is not a decodable PNG.
     */
    fun decode(
        data: ByteBuffer,
        sampleMode: Pixmap.SampleMode = Pixmap.SampleMode.NORMAL,
        name: String? = null,
    ): Sprite

    /**
     * Encodes [sprite] into PNG bytes in an engine buffer; the caller owns
     * and must close the returned wrapper.
     */
    fun encode(sprite: Sprite): ResourceWrapper<ByteBuffer>

    /**
     * Encodes [sprite] into a base64 PNG payload — the portable download
     * form (a [PngSource.base64] reads it back).
     */
    fun encodeToBase64(sprite: Sprite): String {
        val png = encode(sprite)
        return try {
            Base64.Default.encode(png.resource.readBytes())
        } finally {
            png.close()
        }
    }

    /**
     * Loads a PNG from [source]: `read()` then [decode]. The read wrapper is
     * always closed — on a decode failure too.
     */
    suspend fun load(
        source: PngSource,
        sampleMode: Pixmap.SampleMode = Pixmap.SampleMode.NORMAL,
        name: String? = null,
    ): Sprite {
        val bytes = source.read()
        return try {
            decode(bytes.resource, sampleMode, name)
        } finally {
            bytes.close()
        }
    }

    companion object :
        KGEOverridable.Proxy<PngService>(PngService::class, pngServiceDefault),
        PngService {
        override fun decode(
            data: ByteBuffer,
            sampleMode: Pixmap.SampleMode,
            name: String?,
        ): Sprite = delegate.decode(data, sampleMode, name)

        override fun encode(sprite: Sprite): ResourceWrapper<ByteBuffer> = delegate.encode(sprite)

        override fun encodeToBase64(sprite: Sprite): String = delegate.encodeToBase64(sprite)

        override suspend fun load(
            source: PngSource,
            sampleMode: Pixmap.SampleMode,
            name: String?,
        ): Sprite = delegate.load(source, sampleMode, name)
    }
}

/** Reads every byte of [this] in order — the common engine-buffer escape hatch. */
internal fun ByteBuffer.readBytes(): ByteArray = ByteArray(capacity()) { byteAt(it).toByte() }

internal expect val pngServiceDefault: PngService
