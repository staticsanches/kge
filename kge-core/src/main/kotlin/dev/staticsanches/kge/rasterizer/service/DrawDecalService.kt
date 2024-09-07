package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.image.Decal
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.DecalInstance
import dev.staticsanches.kge.renderer.LayerDescriptor
import dev.staticsanches.kge.spi.KGESPIExtensible
import java.nio.ByteBuffer

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
        targetLayer.decalInstances +=
            DecalInstance(
                decal,
                decalMode,
                decalStructure,
                DefaultVerticesInfo(position, decal, scale, tint, invertedScreenSize),
            )
    }

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}

private class DefaultVerticesInfo(
    position: Int2D,
    decal: Decal,
    scale: Float2D,
    val tint: Pixel,
    invertedScreenSize: Float2D,
) : DecalInstance.VerticesInfo {
    val startX = 2f * position.x * invertedScreenSize.x - 1f
    val startY = -2f * position.y * invertedScreenSize.y + 1f
    val endX = startX + 2f * decal.sprite.width * invertedScreenSize.x * scale.x
    val endY = startY - 2f * decal.sprite.height * invertedScreenSize.y * scale.y

    override val numberOfVertices: Int
        get() = 4

    override fun x(index: Int): Float = if (index <= 1) startX else endX

    override fun y(index: Int): Float = if (index == 0 || index == 3) startY else endY

    override fun w(index: Int): Float = 1f

    override fun u(index: Int): Float = if (index <= 1) 0f else 1f

    override fun v(index: Int): Float = if (index == 0 || index == 3) 0f else 1f

    override fun tint(index: Int): Pixel = tint

    override fun putAllXYWUVTint(buffer: ByteBuffer) {
        buffer
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
    }
}
