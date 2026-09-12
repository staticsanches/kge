@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.staticsanches.kge.image

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.buffer.byteAt
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.letClosingIfFailed
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.khronos.webgl.DataView
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.Uint8ClampedArray
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.ImageData
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.toJsArray
import kotlin.js.unsafeCast

/**
 * The browser-native codec primitive (js + wasmJs, shared in `webMain`).
 *
 * Decode: encoded bytes -> `Blob` -> `createImageBitmap` -> a detached canvas
 * -> `getImageData().data` (RGBA) -> an engine buffer wrapper that is handed to
 * `consume` (ownership transferred; closed by the consumer). Encode: a
 * [Sprite]'s RGBA -> `ImageData` -> `putImageData` -> `toDataURL` ->
 * base64-decoded bytes.
 *
 * The canvas storage is premultiplied but `getImageData`/`toDataURL` un-
 * premultiply, so an opaque or binary-alpha surface round-trips.
 */
internal object WebImageCodec {
    suspend fun decode(
        source: ByteBuffer,
        consume: (width: Int, height: Int, pixels: ResourceWrapper<ByteBuffer>) -> Unit,
    ) {
        val bytes = ByteArray(source.capacity()) { source.byteAt(it).toByte() }
        val bitmap = window.createImageBitmap(bytes.toBlob()).await()
        try {
            val width = bitmap.width
            val height = bitmap.height
            val canvas = newCanvas(width, height)
            val context = canvas.context2D()
            context.drawImage(bitmap, 0.0, 0.0)
            val image = context.getImageData(0.0, 0.0, width.toDouble(), height.toDouble())
            val data = DataView(image.data.buffer)
            val wrapper = BufferService.allocate(width * height * Int.SIZE_BYTES, "WebImageCodec.decode")
            // Only the fill is guarded: once `consume` is invoked ownership
            // transfers, and the consumer closes the wrapper, not the codec.
            wrapper.letClosingIfFailed { pixels ->
                val target = pixels.resource
                for (i in 0 until width * height * Int.SIZE_BYTES) {
                    target.put(i, data.getUint8(i))
                }
            }
            consume(width, height, wrapper)
        } finally {
            bitmap.close()
        }
    }

    fun encodePng(sprite: Sprite): ByteArray = encode(sprite, "image/png")

    fun encodeJpeg(sprite: Sprite): ByteArray = encode(sprite, "image/jpeg")

    private fun encode(
        sprite: Sprite,
        mimeType: String,
    ): ByteArray {
        val width = sprite.width
        val height = sprite.height
        val bytes = width * height * Int.SIZE_BYTES
        val rgba = Uint8ClampedArray(bytes)
        val pixels = DataView(rgba.buffer)
        val buffer = sprite.buffer
        for (i in 0 until bytes) {
            pixels.setUint8(i, buffer.get(i))
        }
        val canvas = newCanvas(width, height)
        canvas.context2D().putImageData(ImageData(rgba, width, height), 0.0, 0.0)
        val dataUrl = canvas.toDataURL(mimeType)
        return base64Decode(dataUrl.substringAfter(','))
    }
}

private fun newCanvas(
    width: Int,
    height: Int,
): HTMLCanvasElement =
    document.createElement("canvas").unsafeCast<HTMLCanvasElement>().apply {
        this.width = width
        this.height = height
    }

private fun HTMLCanvasElement.context2D(): CanvasRenderingContext2D = getContext("2d") as CanvasRenderingContext2D

private fun ByteArray.toBlob(): Blob {
    val array = Uint8Array(size)
    val bytes = DataView(array.buffer)
    for (i in indices) {
        bytes.setUint8(i, this[i])
    }
    val parts = arrayOf<JsAny?>(array)
    // No MIME type: decode is format-agnostic, so the browser sniffs the bytes.
    return Blob(parts.toJsArray(), BlobPropertyBag())
}

private fun base64Decode(base64: String): ByteArray {
    val binary = window.atob(base64)
    return ByteArray(binary.length) { binary[it].code.toByte() }
}
