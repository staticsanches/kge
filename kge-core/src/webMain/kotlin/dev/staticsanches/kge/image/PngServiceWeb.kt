package dev.staticsanches.kge.image

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.letClosingIfFailed

/**
 * Web codec default (js + wasmJs): pngjs `.sync`, reached through [WebPngJs].
 * Both pngjs decoders produce the R,G,B,A row-major bytes a [Sprite] stores,
 * so decode/encode move raw bytes — no per-pixel conversion.
 */
internal actual val pngServiceDefault: PngService = WebPngService

/** A pngjs-decoded surface: dimensions plus the raw R,G,B,A pixel bytes. */
internal data class WebPngSurface(
    val width: Int,
    val height: Int,
    val rgba: ByteArray,
)

/**
 * The pngjs byte-level codec operations, actualized per leaf target (the
 * js and wasmJs targets need different JS-interop annotations, so the raw
 * pngjs access is not a shared webMain file).
 */
internal expect object WebPngJs {
    fun decodePng(pngBytes: ByteArray): WebPngSurface

    fun encodePng(
        width: Int,
        height: Int,
        rgba: ByteArray,
    ): ByteArray
}

private object WebPngService : PngService {
    override fun decode(
        data: ByteBuffer,
        sampleMode: Pixmap.SampleMode,
        name: String?,
    ): Sprite {
        val surface = WebPngJs.decodePng(data.readBytes())
        return BufferService.allocate(surface.rgba.size, name).letClosingIfFailed { wrapper ->
            val destination = wrapper.resource
            for (i in surface.rgba.indices) {
                destination.put(i, surface.rgba[i])
            }
            Sprite(surface.width, surface.height, wrapper, sampleMode, name)
        }
    }

    override fun encode(sprite: Sprite): ResourceWrapper<ByteBuffer> {
        val pixelBytes = sprite.width * sprite.height * Int.SIZE_BYTES
        val rgba = ByteArray(pixelBytes) { sprite.buffer.get(it) }
        return WebPngJs.encodePng(sprite.width, sprite.height, rgba).toEngineBuffer("PNG")
    }
}
