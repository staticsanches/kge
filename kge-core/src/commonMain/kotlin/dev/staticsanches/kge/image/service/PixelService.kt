package dev.staticsanches.kge.image.service

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.image.IntColorComponent
import dev.staticsanches.kge.image.Pixel

/**
 * Given that KGE works with native libraries, [Pixel.nativeRGBA] stores the RGBA color using (usually) system's
 * endianness to minimize the number of conversions needed when working with the values stored in [ByteBuffer].
 *
 * In JVM, if the native endianness is big-endian then [Pixel.nativeRGBA] must be structured as:
 * | 8 bits | 8 bits | 8 bits | 8 bits |
 * |  Red   | Green  |  Blue  | Alpha  |
 *
 * Otherwise:
 * | 8 bits | 8 bits | 8 bits | 8 bits |
 * | Alpha  |  Blue  | Green  |  Red   |
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

expect val originalPixelServiceImplementation: PixelService
