package dev.staticsanches.kge.image.service

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.buffer.isNative
import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.image.IntColorComponent
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.utils.BytesSize.INT

/**
 * KGE works with native libraries that uses the system's endianness and the interaction with OpenGL/WebGL, when
 * dealing with [Pixel] information, uses [GL.RGBA].
 *
 * KGE assumes that the [ByteBuffer.order] is always native. So, to allow the correct interaction with native
 * libraries, [Pixel.nativeRGBA] stores the correct RGBA representation of the color that, when written/read from a
 * [ByteBuffer], gives us the desired structure:
 * | 8 bits | 8 bits | 8 bits | 8 bits |
 * |  Red   | Green  |  Blue  | Alpha  |
 */
interface PixelService : KGEExtensibleService {
    fun toNativeRGBA(rgba: Int): Int

    fun toNativeRGBA(
        r: Int,
        g: Int,
        b: Int,
        a: Int,
    ): Int

    fun fromNativeRGBA(nativeRGBA: Int): UInt

    fun redFromNativeRGBA(nativeRGBA: Int): IntColorComponent

    fun greenFromNativeRGBA(nativeRGBA: Int): IntColorComponent

    fun blueFromNativeRGBA(nativeRGBA: Int): IntColorComponent

    fun alphaFromNativeRGBA(nativeRGBA: Int): IntColorComponent

    /**
     * Inverts RGB portion keeping the alpha channel untouched.
     */
    fun invNativeRGBA(nativeRGBA: Int): Int

    /**
     * Inverts RGB portion keeping the alpha channel untouched.
     */
    fun invRGBABuffer(buffer: ByteBuffer)

    companion object : PixelService by KGEExtensibleService.getOptionalWithHigherPriority()
        ?: originalPixelServiceImplementation
}

val originalPixelServiceImplementation: PixelService
    get() = DefaultPixelService

/**
 * KGE only supports, by default, little endian systems. So, [Pixel.nativeRGBA] has the following structure:
 * | 8 bits | 8 bits | 8 bits | 8 bits |
 * | Alpha  |  Blue  | Green  |  Red   |
 */
private data object DefaultPixelService : PixelService {
    override fun toNativeRGBA(rgba: Int): Int = reverseBytes(rgba)

    override fun toNativeRGBA(
        r: Int,
        g: Int,
        b: Int,
        a: Int,
    ): Int = (r and 0xff) or ((g and 0xff) shl 8) or ((b and 0xff) shl 16) or ((a and 0xff) shl 24)

    override fun fromNativeRGBA(nativeRGBA: Int): UInt = reverseBytes(nativeRGBA).toUInt()

    override fun redFromNativeRGBA(nativeRGBA: Int): IntColorComponent = nativeRGBA and 0xff

    override fun greenFromNativeRGBA(nativeRGBA: Int): IntColorComponent = (nativeRGBA ushr 8) and 0xff

    override fun blueFromNativeRGBA(nativeRGBA: Int): IntColorComponent = (nativeRGBA ushr 16) and 0xff

    override fun alphaFromNativeRGBA(nativeRGBA: Int): IntColorComponent = (nativeRGBA ushr 24) and 0xff

    override fun invNativeRGBA(nativeRGBA: Int): Int =
        (nativeRGBA.inv() and 0xff_ff_ff) or (((nativeRGBA ushr 24) and 0xff) shl 24)

    override fun invRGBABuffer(buffer: ByteBuffer) {
        check(buffer.clear().capacity() % INT == 0)
        check(buffer.order().isNative)
        for (i in 0..<buffer.capacity() step INT) {
            buffer.putInt(i, invNativeRGBA(buffer.getInt(i)))
        }
    }

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}

internal expect fun reverseBytes(value: Int): Int
