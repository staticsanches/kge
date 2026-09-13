package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.image.Sprite
import org.lwjgl.stb.STBIWriteCallback
import org.lwjgl.stb.STBImageWrite
import org.lwjgl.system.MemoryUtil
import java.io.ByteArrayOutputStream
import java.nio.channels.Channels

/**
 * JVM backend: STB. Encodes the sprite's row-major R,G,B,A bytes as PNG
 * (default stride) or JPEG at the fixed [JPEG_QUALITY].
 */
internal actual fun encodePngBytes(sprite: Sprite): ByteArray {
    val output = ByteArrayOutputStream()
    val channel = Channels.newChannel(output)
    val written =
        STBImageWrite.stbi_write_png_to_func(
            { _, data, size -> channel.write(STBIWriteCallback.getData(data, size)) },
            MemoryUtil.NULL,
            sprite.width,
            sprite.height,
            4,
            sprite.buffer,
            0,
        )
    check(written) { "unable to encode $sprite as PNG" }
    return output.toByteArray()
}

internal actual fun encodeJpegBytes(sprite: Sprite): ByteArray {
    val output = ByteArrayOutputStream()
    val channel = Channels.newChannel(output)
    val written =
        STBImageWrite.stbi_write_jpg_to_func(
            { _, data, size -> channel.write(STBIWriteCallback.getData(data, size)) },
            MemoryUtil.NULL,
            sprite.width,
            sprite.height,
            4,
            sprite.buffer,
            JPEG_QUALITY,
        )
    check(written != 0) { "unable to encode $sprite as JPEG" }
    return output.toByteArray()
}

private const val JPEG_QUALITY = 90
