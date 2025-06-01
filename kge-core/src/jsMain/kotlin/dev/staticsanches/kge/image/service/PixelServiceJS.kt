package dev.staticsanches.kge.image.service

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.image.IntColorComponent
import dev.staticsanches.kge.utils.BytesSize.INT
import kotlin.experimental.inv

actual val originalPixelServiceImplementation: PixelService
    get() = DefaultPixelService

private data object DefaultPixelService : PixelService {
    override fun toNativeRGBA(rgba: Int): Int = rgba

    override fun toNativeRGBA(
        r: Int,
        g: Int,
        b: Int,
        a: Int,
    ): Int = (r.toComponent() shl 24) or (g.toComponent() shl 16) or (b.toComponent() shl 8) or a.toComponent()

    override fun fromNativeRGBA(nativeRGBA: Int): UInt = nativeRGBA.toUInt()

    override fun redFromNativeRGBA(nativeRGBA: Int): IntColorComponent = nativeRGBA shr 24 and 0xFF

    override fun greenFromNativeRGBA(nativeRGBA: Int): IntColorComponent = nativeRGBA shr 16 and 0xFF

    override fun blueFromNativeRGBA(nativeRGBA: Int): IntColorComponent = nativeRGBA shr 8 and 0xFF

    override fun alphaFromNativeRGBA(nativeRGBA: Int): IntColorComponent = nativeRGBA and 0xFF

    override fun invNativeRGBA(nativeRGBA: Int): Int =
        ((nativeRGBA.toLong() and 0xffffffffL) xor 0xFF_FF_FF_00L).toInt()

    override fun invRGBABuffer(buffer: ByteBuffer) {
        check(buffer.clear().capacity() % INT == 0)
        for (i in 1..<buffer.capacity() step 4) {
            val b = buffer.position(i).get()
            val g = buffer.get()
            val r = buffer.get()

            buffer
                .position(i)
                .put(b.inv())
                .put(g.inv())
                .put(r.inv())
        }
    }

    private fun Int.toComponent(): Int =
        if (this < 0) {
            0
        } else if (this > 255) {
            255
        } else {
            this
        }

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
