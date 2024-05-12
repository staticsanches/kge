package dev.staticsanches.kge.endian

import dev.staticsanches.kge.image.IntColorComponent
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.pixelmap.buffer.RGBABuffer
import java.lang.Integer.toUnsignedLong
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ByteOrder.BIG_ENDIAN
import java.nio.ByteOrder.nativeOrder
import kotlin.experimental.inv

internal sealed interface EndianAwareUtils {
    /**
     * Given that KGE works with C libraries, [Pixel.nativeRGBA] stores the RGBA color using
     * [ByteOrder.nativeOrder] to minimize the number of conversions needed when working with [RGBABuffer].
     *
     * This problem rises since Java uses [ByteOrder.BIG_ENDIAN] to load data and if the system's endian
     * is [ByteOrder.LITTLE_ENDIAN] some conversions are necessary.
     *
     * If [ByteOrder.nativeOrder] is [ByteOrder.BIG_ENDIAN] then [Pixel.nativeRGBA] must be structured as:
     * | 8 bits | 8 bits | 8 bits | 8 bits |
     * |  Red   | Green  |  Blue  | Alpha  |
     *
     * Otherwise:
     * | 8 bits | 8 bits | 8 bits | 8 bits |
     * | Alpha  |  Blue  | Green  |  Red   |
     */
    fun toNativeRGBA(rgba: Int): Int

    /**
     * @see toNativeRGBA
     */
    fun toNativeRGBA(
        r: Int,
        g: Int,
        b: Int,
        a: Int,
    ): Int

    /**
     * @see toNativeRGBA
     */
    fun fromNativeRGBA(nativeRGBA: Int): UInt

    /**
     * @see toNativeRGBA
     */
    fun redFromNativeRGBA(nativeRGBA: Int): IntColorComponent

    /**
     * @see toNativeRGBA
     */
    fun greenFromNativeRGBA(nativeRGBA: Int): IntColorComponent

    /**
     * @see toNativeRGBA
     */
    fun blueFromNativeRGBA(nativeRGBA: Int): IntColorComponent

    /**
     * @see toNativeRGBA
     */
    fun alphaFromNativeRGBA(nativeRGBA: Int): IntColorComponent

    /**
     * Inverts RGB portion keeping the alpha channel untouched.
     */
    fun invNativeRGBA(nativeRGBA: Int): Int

    /**
     * Inverts RGB portion keeping the alpha channel untouched.
     */
    fun invRGBABuffer(buffer: ByteBuffer)

    companion object :
        EndianAwareUtils by if (nativeOrder() == BIG_ENDIAN) BigEndianAwareUtils else LittleEndianAwareUtils
}

private data object BigEndianAwareUtils : EndianAwareUtils {
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

    override fun invNativeRGBA(nativeRGBA: Int): Int = (toUnsignedLong(nativeRGBA) xor 0xFF_FF_FF_00L).toInt()

    override fun invRGBABuffer(buffer: ByteBuffer) {
        check(buffer.capacity() % 4 == 0)
        for (i in 0..<buffer.capacity() step 4) {
            val r = buffer.position(i).get()
            val g = buffer.get()
            val b = buffer.get()

            buffer.position(i)
                .put(r.inv())
                .put(g.inv())
                .put(b.inv())
        }
    }
}

private data object LittleEndianAwareUtils : EndianAwareUtils {
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

    override fun invNativeRGBA(nativeRGBA: Int): Int = (toUnsignedLong(nativeRGBA) xor 0x00_FF_FF_FFL).toInt()

    override fun invRGBABuffer(buffer: ByteBuffer) {
        check(buffer.capacity() % 4 == 0)
        for (i in 1..<buffer.capacity() step 4) {
            val b = buffer.position(i).get()
            val g = buffer.get()
            val r = buffer.get()

            buffer.position(i)
                .put(b.inv())
                .put(g.inv())
                .put(r.inv())
        }
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
