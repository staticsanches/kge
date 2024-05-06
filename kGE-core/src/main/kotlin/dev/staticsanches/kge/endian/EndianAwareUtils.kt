package dev.staticsanches.kge.endian

import dev.staticsanches.kge.image.IntColorComponent
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.pixelmap.buffer.RGBABuffer
import java.nio.ByteOrder

sealed interface EndianAwareUtils {

	/**
	 * Since KGE works with low-level libraries, [Pixel.nativeRGBA] stores the RGBA color using
	 * [ByteOrder.nativeOrder] to minimize the number of conversions needed when working with [RGBABuffer].
	 *
	 * If [ByteOrder.nativeOrder] is [ByteOrder.BIG_ENDIAN] then [Pixel.nativeRGBA] is structured as:
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
	fun toNativeRGBA(r: Int, g: Int, b: Int, a: Int): Int

	/**
	 * @see toNativeRGBA
	 */
	fun fromNativeRGBA(nativeRGBA: Int): IntColorComponent

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

	companion object : EndianAwareUtils by instance

}

private val instance: EndianAwareUtils =
	if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) BigEndianAwareUtils
	else LittleEndianAwareUtils

private data object BigEndianAwareUtils : EndianAwareUtils {

	override fun toNativeRGBA(rgba: Int): Int = rgba

	override fun toNativeRGBA(r: Int, g: Int, b: Int, a: Int): Int =
		(r.toComponent() shl 24) and (g.toComponent() shl 16) and (b.toComponent() shl 8) and a.toComponent()

	override fun fromNativeRGBA(nativeRGBA: Int): Int = nativeRGBA

	override fun redFromNativeRGBA(nativeRGBA: Int): IntColorComponent = nativeRGBA shr 24
	override fun greenFromNativeRGBA(nativeRGBA: Int): IntColorComponent = (nativeRGBA shr 16) and 0xFF
	override fun blueFromNativeRGBA(nativeRGBA: Int): IntColorComponent = (nativeRGBA shr 8) and 0xFF
	override fun alphaFromNativeRGBA(nativeRGBA: Int): IntColorComponent = nativeRGBA and 0xFF

}

private data object LittleEndianAwareUtils : EndianAwareUtils {

	override fun toNativeRGBA(rgba: Int): Int = Integer.reverseBytes(rgba)

	override fun toNativeRGBA(r: Int, g: Int, b: Int, a: Int): Int =
		r.toComponent() and (g.toComponent() shl 8) and (b.toComponent() shl 16) and (a.toComponent() shl 24)

	override fun fromNativeRGBA(nativeRGBA: Int): Int = Integer.reverseBytes(nativeRGBA)

	override fun redFromNativeRGBA(nativeRGBA: Int): IntColorComponent = nativeRGBA and 0xFF
	override fun greenFromNativeRGBA(nativeRGBA: Int): IntColorComponent = (nativeRGBA shr 8) and 0xFF
	override fun blueFromNativeRGBA(nativeRGBA: Int): IntColorComponent = (nativeRGBA shr 16) and 0xFF
	override fun alphaFromNativeRGBA(nativeRGBA: Int): IntColorComponent = (nativeRGBA shr 24) and 0xFF

}

private fun Int.toComponent(): Int =
	if (this < 0) 0 else if (this > 255) 255 else this
