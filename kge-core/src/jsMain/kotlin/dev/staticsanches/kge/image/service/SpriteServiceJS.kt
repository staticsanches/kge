package dev.staticsanches.kge.image.service

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.buffer.ByteBufferWrapper
import dev.staticsanches.kge.buffer.ByteOrder
import dev.staticsanches.kge.buffer.isNative
import dev.staticsanches.kge.buffer.service.ByteBufferWrapperService
import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.resource.closeIfFailed
import dev.staticsanches.kge.utils.BytesSize
import dev.staticsanches.kge.utils.BytesSize.int
import dev.staticsanches.kge.utils.toHumanReadableByteCountBin
import ext.pngjs.PNG
import js.buffer.ArrayBufferLike
import js.errors.toJsError
import js.promise.Promise
import js.promise.catch
import js.typedarrays.Uint8Array
import web.encoding.btoa
import web.http.Response
import web.http.fetchAsync

actual interface SpriteService : KGEExtensibleService {
    actual fun create(
        width: Int,
        height: Int,
        sampleMode: Sprite.SampleMode,
        name: String?,
    ): Sprite

    actual fun duplicate(
        original: Sprite,
        newName: String?,
    ): Sprite

    fun loadPNG(
        url: String,
        sampleMode: Sprite.SampleMode,
        name: String?,
    ): Promise<Sprite>

    fun loadPNGFromBase64(
        data: String,
        sampleMode: Sprite.SampleMode,
        name: String?,
    ): Promise<Sprite>

    actual fun toBase64PNG(sprite: Sprite): String

    actual companion object : SpriteService by KGEExtensibleService.getOptionalWithHigherPriority()
        ?: originalSpriteServiceImplementation
}

actual val originalSpriteServiceImplementation: SpriteService
    get() = DefaultSpriteService

private data object DefaultSpriteService : SpriteService {
    override fun create(
        width: Int,
        height: Int,
        sampleMode: Sprite.SampleMode,
        name: String?,
    ): Sprite =
        ByteBufferWrapper(BytesSize { width * height * int }) { "Sprite ${width}x$height ($it)" }.closeIfFailed {
            Sprite(width, height, it, sampleMode)
        }

    override fun duplicate(
        original: Sprite,
        newName: String?,
    ): Sprite =
        ByteBufferWrapperService.duplicate(original, newName).closeIfFailed {
            Sprite(original.width, original.height, it, original.sampleMode)
        }

    override fun loadPNG(
        url: String,
        sampleMode: Sprite.SampleMode,
        name: String?,
    ): Promise<Sprite> =
        Promise { resolve, reject ->
            fetchAsync(url)
                .flatThen(Response::arrayBufferAsync)
                .flatThen { loadPNG(it, sampleMode, name) }
                .then(resolve::invoke)
                .catch { reject(it.toJsError()) }
        }

    override fun loadPNGFromBase64(
        data: String,
        sampleMode: Sprite.SampleMode,
        name: String?,
    ): Promise<Sprite> = loadPNG(fromBase64(data), sampleMode, name)

    @Suppress("unused", "UnusedVariable")
    override fun toBase64PNG(sprite: Sprite): String {
        // Create the PNG object
        val width = sprite.width
        val height = sprite.height
        val png = PNG(js("{ width: width, height: height, filterType: -1 }"))

        // Fill image data, fixing endianness if necessary
        png.data.set(sprite.resource.clear().createView(1, ::Uint8Array))
        if (ByteOrder.littleEndian.isNative) {
            ByteBufferWrapperService.create(png.data.buffer, "PNG Buffer").use { reverseBytes(it.resource) }
        }

        // Write PNG data
        return toBase64(PNG.sync.write(png))
    }

    private fun loadPNG(
        rawData: ArrayBufferLike,
        sampleMode: Sprite.SampleMode,
        name: String?,
    ): Promise<Sprite> =
        Promise { resolve, reject ->
            try {
                PNG().parse(rawData) { error, png ->
                    if (error != null) {
                        reject(error.toJsError())
                        return@parse
                    }
                    try {
                        val width = png.width
                        val height = png.height
                        ByteBufferWrapperService
                            .create(
                                png.data.buffer,
                                name ?: (
                                    "Sprite ${width}x$height" +
                                        " (${png.data.buffer.byteLength.toHumanReadableByteCountBin()})"
                                ),
                            ).closeIfFailed { bbw ->
                                if (ByteOrder.littleEndian.isNative) reverseBytes(bbw.resource)
                                resolve(Sprite(width, height, bbw, sampleMode))
                            }
                    } catch (e: Throwable) {
                        reject(e)
                    }
                }
            } catch (e: Throwable) {
                reject(e)
            }
        }

    @Suppress("unused", "SpellCheckingInspection")
    private fun fromBase64(data: String): ArrayBufferLike =
        js("Uint8Array.from(atob(data), function(c) { return c.charCodeAt(0) }).buffer")

    private fun toBase64(bytes: Uint8Array<*>): String {
        var binary = ""
        @Suppress("unused")
        for (i in 0..<bytes.byteLength) {
            binary += js("String.fromCharCode(bytes[i])")
        }
        return btoa(binary)
    }

    private fun reverseBytes(buffer: ByteBuffer) {
        check(buffer.clear().capacity() % int == 0)
        while (buffer.hasRemaining()) {
            buffer.mark()
            val rgba = buffer.getInt()
            buffer.reset()
            buffer.putInt(rgba.reverseBytes())
        }
        buffer.clear()
    }

    private fun Int.reverseBytes(): Int =
        ((this and 0xff) shl 24) or ((this and 0xff00) shl 8) or ((this shr 8) and 0xff00) or ((this shr 24) and 0xff)

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
