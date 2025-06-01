package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.image.Decal
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.renderer.DecalInstance

interface DrawDecalService : KGEExtensibleService {
    fun drawDecal(
        position: Float2D,
        decal: Decal,
        scale: Float2D,
        tint: Pixel,
        invertedScreenSize: Float2D,
        decalMode: Decal.Mode,
        decalStructure: Decal.Structure,
        decalInstanceCollector: (DecalInstance) -> Unit,
    )

    companion object : DrawDecalService by KGEExtensibleService.getOptionalWithHigherPriority()
        ?: originalDrawDecalServiceImplementation
}

val originalDrawDecalServiceImplementation: DrawDecalService
    get() = DefaultDrawDecalService

private data object DefaultDrawDecalService : DrawDecalService {
    override fun drawDecal(
        position: Float2D,
        decal: Decal,
        scale: Float2D,
        tint: Pixel,
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
                VerticesInfo(position, decal, scale, tint, invertedScreenSize),
            ),
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

        val screenSpaceDimX = screenSpacePosX + 2f * decal.sprite.width * invertedScreenSize.x * scale.x
        val screenSpaceDimY = screenSpacePosY - 2f * decal.sprite.height * invertedScreenSize.y * scale.y

        override val numberOfVertices: Int
            get() = 4

        override fun x(index: Int): Float = if (index <= 1) screenSpacePosX else screenSpaceDimX

        override fun y(index: Int): Float = if (index == 0 || index == 3) screenSpacePosY else screenSpaceDimY

        override fun z(index: Int): Float = 1f

        override fun w(index: Int): Float = 0f

        override fun u(index: Int): Float = if (index <= 1) 0f else 1f

        override fun v(index: Int): Float = if (index == 0 || index == 3) 0f else 1f

        override fun tint(index: Int): Pixel = tint

        override fun putAll(buffer: ByteBuffer) {
            buffer
                // Vertex 0
                .putFloat(screenSpacePosX) // x
                .putFloat(screenSpacePosY) // y
                .putFloat(1f) // z
                .putFloat(0f) // w
                .putFloat(0f) // u
                .putFloat(0f) // v
                .putInt(tint.nativeRGBA) // tint
                // Vertex 1
                .putFloat(screenSpacePosX) // x
                .putFloat(screenSpaceDimY) // y
                .putFloat(1f) // z
                .putFloat(0f) // w
                .putFloat(0f) // u
                .putFloat(1f) // v
                .putInt(tint.nativeRGBA) // tint
                // Vertex 2
                .putFloat(screenSpaceDimX) // x
                .putFloat(screenSpaceDimY) // y
                .putFloat(1f) // z
                .putFloat(0f) // w
                .putFloat(1f) // u
                .putFloat(1f) // v
                .putInt(tint.nativeRGBA) // tint
                // Vertex 3
                .putFloat(screenSpaceDimX) // x
                .putFloat(screenSpacePosY) // y
                .putFloat(1f) // z
                .putFloat(0f) // w
                .putFloat(1f) // u
                .putFloat(0f) // v
                .putInt(tint.nativeRGBA) // tint
        }
    }
}
