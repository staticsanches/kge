@file:Suppress("unused")

package dev.staticsanches.kge.image

import dev.staticsanches.kge.buffer.ByteBufferWrapper
import dev.staticsanches.kge.buffer.isNative
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.math.vector.Int2D.Companion.by
import dev.staticsanches.kge.utils.BytesSize
import dev.staticsanches.kge.utils.BytesSize.invoke
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class Sprite(
    override val width: Int,
    override val height: Int,
    private val delegate: ByteBufferWrapper,
    var sampleMode: SampleMode,
) : RGBABuffer,
    ByteBufferWrapper by delegate {
    override val size: Int2D = width by height

    init {
        check(width > 0 && height > 0) { "Invalid buffer dimension ${width}x$height" }

        val (rgbaData) = delegate
        val expectedBufferCapacity = BytesSize { width * height * int }
        check(expectedBufferCapacity == rgbaData.capacity()) {
            "Invalid buffer capacity. Expected: $expectedBufferCapacity. Actual: ${rgbaData.capacity()}"
        }
        check(rgbaData.order().isNative) {
            "Invalid order for the buffer. It must use the native order but it is using ${rgbaData.order()}"
        }
    }

    override operator fun get(
        x: Int,
        y: Int,
    ): Pixel =
        when (sampleMode) {
            SampleMode.NORMAL -> if (x in 0..<width && y in 0..<height) uncheckedGet(x, y) else Colors.BLANK
            SampleMode.PERIODIC -> uncheckedGet(abs(x % width), abs(y % height))
            SampleMode.CLAMP -> uncheckedGet(max(0, min(x, width - 1)), max(0, min(y, height - 1)))
        }

    override fun toString(): String = delegate.toString()

    enum class SampleMode { NORMAL, PERIODIC, CLAMP }

    enum class Flip { NONE, HORIZONTAL, VERTICAL, BOTH }

    companion object
}
