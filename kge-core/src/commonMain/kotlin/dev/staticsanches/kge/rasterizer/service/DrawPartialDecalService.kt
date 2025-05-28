package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.image.Decal
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.DecalInstance
import kotlin.math.ceil
import kotlin.math.floor

interface DrawPartialDecalService : KGEExtensibleService {
    fun drawPartialDecal(
        position: Float2D,
        decal: Decal,
        sourcePosition: Float2D,
        sourceSize: Float2D,
        scale: Float2D,
        tint: Pixel,
        screenSize: Int2D,
        invertedScreenSize: Float2D,
        decalMode: Decal.Mode,
        decalStructure: Decal.Structure,
        decalInstanceCollector: (DecalInstance) -> Unit,
    )

    companion object : DrawPartialDecalService by KGEExtensibleService.getOptionalWithHigherPriority()
        ?: originalDrawPartialDecalServiceImplementation
}

val originalDrawPartialDecalServiceImplementation: DrawPartialDecalService
    get() = DefaultDrawPartialDecalService

private data object DefaultDrawPartialDecalService : DrawPartialDecalService {
    override fun drawPartialDecal(
        position: Float2D,
        decal: Decal,
        sourcePosition: Float2D,
        sourceSize: Float2D,
        scale: Float2D,
        tint: Pixel,
        screenSize: Int2D,
        invertedScreenSize: Float2D,
        decalMode: Decal.Mode,
        decalStructure: Decal.Structure,
        decalInstanceCollector: (DecalInstance) -> Unit,
    ) {
        decalInstanceCollector(
            DecalInstance(
                decal,
                decalMode,
                decalStructure,
                VerticesInfo(position, decal, sourcePosition, sourceSize, scale, tint, screenSize, invertedScreenSize),
            ),
        )
    }

    override val servicePriority: Int
        get() = Int.MIN_VALUE

    private class VerticesInfo(
        position: Float2D,
        decal: Decal,
        sourcePosition: Float2D,
        sourceSize: Float2D,
        scale: Float2D,
        val tint: Pixel,
        screenSize: Int2D,
        invertedScreenSize: Float2D,
    ) : DecalInstance.VerticesInfo {
        val quantisedPosX: Float
        val quantisedPosY: Float

        val quantisedDimX: Float
        val quantisedDimY: Float

        val uvtlX: Float
        val uvtlY: Float

        val uvbrX: Float
        val uvbrY: Float

        init {
            val screenSpacePosX = 2f * position.x * invertedScreenSize.x - 1f
            val screenSpacePosY = -2f * position.y * invertedScreenSize.y + 1f

            val screenSpaceDimX = 2f * (position.x + sourceSize.x * scale.x) * invertedScreenSize.x - 1f
            val screenSpaceDimY = -2f * (position.y + sourceSize.y * scale.y) * invertedScreenSize.y + 1f

            quantisedPosX = floor(screenSpacePosX * screenSize.x + 0.5f) / screenSize.x
            quantisedPosY = floor(screenSpacePosY * screenSize.y + 0.5f) / screenSize.y

            quantisedDimX = ceil(screenSpaceDimX * screenSize.x + 0.5f) / screenSize.x
            quantisedDimY = ceil(screenSpaceDimY * screenSize.y - 0.5f) / screenSize.y

            uvtlX = (sourcePosition.x + 0.0001f) * decal.uvScale.x
            uvtlY = (sourcePosition.y + 0.0001f) * decal.uvScale.y

            uvbrX = (sourcePosition.x + sourceSize.x - 0.0001f) * decal.uvScale.x
            uvbrY = (sourcePosition.y + sourceSize.y - 0.0001f) * decal.uvScale.y
        }

        override val numberOfVertices: Int
            get() = 4

        override fun x(index: Int): Float = if (index <= 1) quantisedPosX else quantisedDimX

        override fun y(index: Int): Float = if (index == 0 || index == 3) quantisedPosY else quantisedDimY

        override fun w(index: Int): Float = 1f

        override fun u(index: Int): Float = if (index <= 1) uvtlX else uvbrX

        override fun v(index: Int): Float = if (index == 0 || index == 3) uvtlY else uvbrY

        override fun tint(index: Int): Pixel = tint

        override fun putAllXYWUVTint(buffer: ByteBuffer) {
            buffer
                // Vertex 0
                .putFloat(quantisedPosX)
                .putFloat(quantisedPosY)
                .putFloat(1f)
                .putFloat(uvtlX)
                .putFloat(uvtlY)
                .putInt(tint.nativeRGBA)
                // Vertex 1
                .putFloat(quantisedPosX)
                .putFloat(quantisedDimY)
                .putFloat(1f)
                .putFloat(uvtlX)
                .putFloat(uvbrY)
                .putInt(tint.nativeRGBA)
                // Vertex 2
                .putFloat(quantisedDimX)
                .putFloat(quantisedDimY)
                .putFloat(1f)
                .putFloat(uvbrX)
                .putFloat(uvbrY)
                .putInt(tint.nativeRGBA)
                // Vertex 3
                .putFloat(quantisedDimX)
                .putFloat(quantisedPosY)
                .putFloat(1f)
                .putFloat(uvbrX)
                .putFloat(uvtlY)
                .putInt(tint.nativeRGBA)
        }
    }
}
