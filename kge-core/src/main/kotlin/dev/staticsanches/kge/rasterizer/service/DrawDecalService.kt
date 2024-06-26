package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.image.Decal
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.DecalInstance
import dev.staticsanches.kge.renderer.LayerDescriptor
import dev.staticsanches.kge.spi.KGESPIExtensible

interface DrawDecalService : KGESPIExtensible {
    fun drawDecal(
        position: Int2D,
        decal: Decal,
        scale: Float2D,
        tint: Pixel,
        invertedScreenSize: Float2D,
        decalMode: Decal.Mode,
        decalStructure: Decal.Structure,
        targetLayer: LayerDescriptor,
    )
}

internal object DefaultDrawDecalService : DrawDecalService {
    override fun drawDecal(
        position: Int2D,
        decal: Decal,
        scale: Float2D,
        tint: Pixel,
        invertedScreenSize: Float2D,
        decalMode: Decal.Mode,
        decalStructure: Decal.Structure,
        targetLayer: LayerDescriptor,
    ) {
        val startX = 2f * position.x * invertedScreenSize.x - 1f
        val startY = -2f * position.y * invertedScreenSize.y + 1f
        val endX = startX + 2f * decal.sprite.width * invertedScreenSize.x * scale.x
        val endY = startY - 2f * decal.sprite.height * invertedScreenSize.y * scale.y

        val decalInstance =
            DecalInstance(
                decal,
                decalMode,
                decalStructure,
                4,
            )
        decalInstance.verticesData.buffer
            .clear()
            // Vertex 0
            .putFloat(startX)
            .putFloat(startY)
            .putFloat(1f)
            .putFloat(0f)
            .putFloat(0f)
            .putInt(tint.nativeRGBA)
            // Vertex 1
            .putFloat(startX)
            .putFloat(endY)
            .putFloat(1f)
            .putFloat(0f)
            .putFloat(1f)
            .putInt(tint.nativeRGBA)
            // Vertex 2
            .putFloat(endX)
            .putFloat(endY)
            .putFloat(1f)
            .putFloat(1f)
            .putFloat(1f)
            .putInt(tint.nativeRGBA)
            // Vertex 3
            .putFloat(endX)
            .putFloat(startY)
            .putFloat(1f)
            .putFloat(1f)
            .putFloat(0f)
            .putInt(tint.nativeRGBA)
        targetLayer.decalInstances.add(decalInstance)
    }

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
