package dev.staticsanches.kge.image.service

import dev.staticsanches.kge.buffer.ByteBufferWrapper
import dev.staticsanches.kge.buffer.service.ByteBufferWrapperService
import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.resource.closeIfFailed
import dev.staticsanches.kge.utils.BytesSize

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

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
