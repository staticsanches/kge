package dev.staticsanches.kge.image.service

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.buffer.ByteBufferWrapper
import dev.staticsanches.kge.buffer.service.ByteBufferWrapperService
import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.applyAndCloseIfFailed
import dev.staticsanches.kge.resource.closeIfFailed
import dev.staticsanches.kge.utils.BytesSize
import dev.staticsanches.kge.utils.toHumanReadableByteCountBin
import org.lwjgl.stb.STBIWriteCallback
import org.lwjgl.stb.STBImage
import org.lwjgl.stb.STBImageWrite
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.nio.channels.Channels
import java.nio.channels.WritableByteChannel
import java.util.Base64
import kotlin.use

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
        fileName: String,
        sampleMode: Sprite.SampleMode,
        name: String?,
    ): Sprite

    fun loadPNG(
        url: URL,
        sampleMode: Sprite.SampleMode,
        name: String?,
    ): Sprite

    fun loadPNG(
        isProvider: () -> InputStream,
        sampleMode: Sprite.SampleMode,
        name: String?,
    ): Sprite

    fun loadPNGFromBase64(
        data: String,
        sampleMode: Sprite.SampleMode,
        name: String?,
    ): Sprite

    fun writePNG(
        sprite: Sprite,
        fileName: String,
    )

    fun writePNG(
        sprite: Sprite,
        os: OutputStream,
    )

    fun writePNG(
        sprite: Sprite,
        channel: WritableByteChannel,
    )

    fun toBase64PNG(sprite: Sprite): String

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
        fileName: String,
        sampleMode: Sprite.SampleMode,
        name: String?,
    ): Sprite = STBBuffer(fileName).toSprite(sampleMode, name)

    override fun loadPNG(
        url: URL,
        sampleMode: Sprite.SampleMode,
        name: String?,
    ): Sprite = loadPNG(url::openStream, sampleMode, name)

    override fun loadPNG(
        isProvider: () -> InputStream,
        sampleMode: Sprite.SampleMode,
        name: String?,
    ): Sprite =
        isProvider().use { inputStream ->
            val bytes = inputStream.readAllBytes()
            ByteBufferWrapper(bytes.size)
                .applyAndCloseIfFailed { it().clear().put(bytes) }
                .use { STBBuffer(it()).toSprite(sampleMode, name) }
        }

    override fun loadPNGFromBase64(
        data: String,
        sampleMode: Sprite.SampleMode,
        name: String?,
    ): Sprite = loadPNG({ ByteArrayInputStream(Base64.getUrlDecoder().decode(data)) }, sampleMode, name)

    override fun writePNG(
        sprite: Sprite,
        fileName: String,
    ) = check(STBImageWrite.stbi_write_png(fileName, sprite.width, sprite.height, 4, sprite().clear(), 0)) {
        "Unable to write $sprite to $fileName"
    }

    override fun writePNG(
        sprite: Sprite,
        os: OutputStream,
    ) = writePNG(sprite, Channels.newChannel(os))

    override fun writePNG(
        sprite: Sprite,
        channel: WritableByteChannel,
    ) = check(
        STBImageWrite.stbi_write_png_to_func(
            { _, data, size -> channel.write(STBIWriteCallback.getData(data, size)) },
            MemoryUtil.NULL, sprite.width, sprite.height, 4, sprite().clear(), 0,
        ),
    ) { "Unable to write $sprite" }

    override fun toBase64PNG(sprite: Sprite): String =
        ByteArrayOutputStream(BytesSize { sprite.width * sprite.height * 4 }).use { os ->
            writePNG(sprite, os)
            Base64.getUrlEncoder().encodeToString(os.toByteArray())
        }

    override val servicePriority: Int
        get() = Int.MIN_VALUE

    private class STBBuffer(
        val data: ByteBuffer,
        val width: Int,
        val height: Int,
    ) : KGECleanAction {
        fun toSprite(
            sampleMode: Sprite.SampleMode,
            name: String?,
        ): Sprite =
            ResourceWrapper(
                name ?: "Sprite ${width}x$height (${data.capacity().toHumanReadableByteCountBin()})", data, this,
            ).closeIfFailed { Sprite(width, height, it, sampleMode) }

        override fun invoke() = STBImage.stbi_image_free(data.clear())

        companion object {
            operator fun invoke(fileName: String): STBBuffer =
                MemoryStack.stackPush().use { stack ->
                    val width = stack.mallocInt(1)
                    val height = stack.mallocInt(1)
                    val components = stack.mallocInt(1)

                    STBBuffer(
                        checkNotNull(STBImage.stbi_load(fileName, width, height, components, 4)) {
                            "Unable to load $fileName"
                        },
                        width[0], height[0],
                    )
                }

            operator fun invoke(originalData: ByteBuffer): STBBuffer =
                MemoryStack.stackPush().use { stack ->
                    val width = stack.mallocInt(1)
                    val height = stack.mallocInt(1)
                    val components = stack.mallocInt(1)

                    STBBuffer(
                        checkNotNull(
                            STBImage.stbi_load_from_memory(originalData.clear(), width, height, components, 4),
                        ) { "Unable to load image" },
                        width[0], height[0],
                    )
                }
        }
    }
}
