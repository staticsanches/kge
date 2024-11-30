package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.image.Decal
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.renderer.DecalInstance
import dev.staticsanches.kge.renderer.LayerDescriptor
import dev.staticsanches.kge.spi.KGESPIExtensible
import java.nio.ByteBuffer

interface DrawDecalService : KGESPIExtensible {
    fun drawDecal(
        position: Float2D,
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
        position: Float2D,
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
                VerticesInfo(position, decal, scale, tint, invertedScreenSize),
            )
    }

    override val servicePriority: Int
        get() = Int.MIN_VALUE

    private class VerticesInfo(
        position: Float2D,
        decal: Decal,
        scale: Float2D,
        val tint: Pixel,
        invertedScreenSize: Float2D,
    ) : DecalInstance.VerticesInfo {
        val screenSpacePosX = 2f * position.x * invertedScreenSize.x - 1f
        val screenSpacePosY = -2f * position.y * invertedScreenSize.y + 1f

        val screenSpaceDimX = screenSpacePosX + 2f * decal.size.x * invertedScreenSize.x * scale.x
        val screenSpaceDimY = screenSpacePosY - 2f * decal.size.y * invertedScreenSize.y * scale.y

        override val numberOfVertices: Int
            get() = 4

        override fun x(index: Int): Float = if (index <= 1) screenSpacePosX else screenSpaceDimX

        override fun y(index: Int): Float = if (index == 0 || index == 3) screenSpacePosY else screenSpaceDimY

        override fun w(index: Int): Float = 1f

        override fun u(index: Int): Float = if (index <= 1) 0f else 1f

        override fun v(index: Int): Float = if (index == 0 || index == 3) 0f else 1f

        override fun tint(index: Int): Pixel = tint

        override fun putAllXYWUVTint(buffer: ByteBuffer) {
            buffer
                // Vertex 0
                .putFloat(screenSpacePosX)
                .putFloat(screenSpacePosY)
                .putFloat(1f)
                .putFloat(0f)
                .putFloat(0f)
                .putInt(tint.nativeRGBA)
                // Vertex 1
                .putFloat(screenSpacePosX)
                .putFloat(screenSpaceDimY)
                .putFloat(1f)
                .putFloat(0f)
                .putFloat(1f)
                .putInt(tint.nativeRGBA)
                // Vertex 2
                .putFloat(screenSpaceDimX)
                .putFloat(screenSpaceDimY)
                .putFloat(1f)
                .putFloat(1f)
                .putFloat(1f)
                .putInt(tint.nativeRGBA)
                // Vertex 3
                .putFloat(screenSpaceDimX)
                .putFloat(screenSpacePosY)
                .putFloat(1f)
                .putFloat(1f)
                .putFloat(0f)
                .putInt(tint.nativeRGBA)
        }
    }
}
