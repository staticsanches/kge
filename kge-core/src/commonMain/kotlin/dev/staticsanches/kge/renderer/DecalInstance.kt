@file:Suppress("unused")

package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.image.Decal
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.utils.BytesSize.FLOAT
import dev.staticsanches.kge.utils.BytesSize.INT

class DecalInstance(
    val decal: Decal?,
    val mode: Decal.Mode,
    val structure: Decal.Structure,
    val verticesInfo: VerticesInfo,
) {
    interface VerticesInfo {
        val numberOfVertices: Int

        fun x(index: Int): Float

        fun y(index: Int): Float

        fun z(index: Int): Float

        fun w(index: Int): Float

        fun u(index: Int): Float

        fun v(index: Int): Float

        fun tint(index: Int): Pixel

        fun putAll(buffer: ByteBuffer)

        companion object {
            // 4 floats (x y z w) + 2 floats (u v) + 1 int (tint)
            const val VERTEX_BYTES_COUNT = 4 * FLOAT + 2 * FLOAT + 1 * INT
        }
    }
}
