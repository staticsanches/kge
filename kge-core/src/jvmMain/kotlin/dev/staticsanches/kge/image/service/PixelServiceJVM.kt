package dev.staticsanches.kge.image.service

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.buffer.ByteOrder
import dev.staticsanches.kge.buffer.isNative
import dev.staticsanches.kge.image.IntColorComponent
import dev.staticsanches.kge.utils.BytesSize.int
import kotlin.experimental.inv

actual val originalPixelServiceImplementation: PixelService
    get() = if (ByteOrder.BIG_ENDIAN.isNative) BigEndianService else LittleEndianService

private data object BigEndianService : PixelService {
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

    override fun invNativeRGBA(nativeRGBA: Int): Int = (Integer.toUnsignedLong(nativeRGBA) xor 0xFF_FF_FF_00L).toInt()

    override fun invRGBABuffer(buffer: ByteBuffer) {
        check(buffer.clear().capacity() % int == 0)
        for (i in 0..<buffer.capacity() step int) {
            val r = buffer.position(i).get()
            val g = buffer.get()
            val b = buffer.get()

            buffer
                .position(i)
                .put(r.inv())
                .put(g.inv())
                .put(b.inv())
        }
    }

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}

private data object LittleEndianService : PixelService {
    override fun toNativeRGBA(rgba: Int): Int = Integer.reverseBytes(rgba)

    override fun toNativeRGBA(
        r: Int,
        g: Int,
        b: Int,
        a: Int,
    ): Int = r.toComponent() or (g.toComponent() shl 8) or (b.toComponent() shl 16) or (a.toComponent() shl 24)

    override fun fromNativeRGBA(nativeRGBA: Int): UInt = Integer.reverseBytes(nativeRGBA).toUInt()

    override fun redFromNativeRGBA(nativeRGBA: Int): IntColorComponent = nativeRGBA and 0xFF

    override fun greenFromNativeRGBA(nativeRGBA: Int): IntColorComponent = nativeRGBA shr 8 and 0xFF

    override fun blueFromNativeRGBA(nativeRGBA: Int): IntColorComponent = nativeRGBA shr 16 and 0xFF

    override fun alphaFromNativeRGBA(nativeRGBA: Int): IntColorComponent = nativeRGBA shr 24 and 0xFF

    override fun invNativeRGBA(nativeRGBA: Int): Int = (Integer.toUnsignedLong(nativeRGBA) xor 0x00_FF_FF_FFL).toInt()

    override fun invRGBABuffer(buffer: ByteBuffer) {
        check(buffer.clear().capacity() % int == 0)
        for (i in 1..<buffer.capacity() step int) {
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

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}

private fun Int.toComponent(): Int =
    if (this < 0) {
        0
    } else if (this > 255) {
        255
    } else {
        this
    }
