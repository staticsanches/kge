package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.image.Decal
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DecalInstance(
    val decal: Decal?,
    val mode: Decal.Mode,
    val structure: Decal.Structure,
    numberOfVertices: Int,
) {
    @KGESensitiveAPI
    val verticesData: VerticesData

    init {
        check(numberOfVertices > 0) { "Invalid number of vertices" }
        verticesData = VerticesData(numberOfVertices)
    }

    /**
     * Stores [numberOfVertices] representations of vertices that defines a [DecalInstance].
     * Each vertex is composed of 6 coordinates (x: Float, y: Float, w: Float, u: Float, v: Float, tint: Pixel).
     */
    @JvmInline
    value class VerticesData private constructor(
        @property:KGESensitiveAPI val buffer: ByteBuffer,
    ) {
        internal constructor(numberOfVertices: Int) : this(
            ByteBuffer.allocate(numberOfVertices * VERTEX_BYTES_COUNT).order(ByteOrder.nativeOrder()),
        )

        val numberOfVertices: Int
            get() = buffer.capacity() / VERTEX_BYTES_COUNT

        companion object {
            /**
             * Stores the bytes count for one point.
             */
            const val VERTEX_BYTES_COUNT = 5 * Float.SIZE_BYTES + 1 * Int.SIZE_BYTES
        }
    }
}
