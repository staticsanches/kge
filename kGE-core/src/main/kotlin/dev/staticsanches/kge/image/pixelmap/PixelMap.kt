package dev.staticsanches.kge.image.pixelmap

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.types.vector.Int2D


/**
 * It is logically a 2D matrix of [Pixel]s with a "row major" configuration and "direct access"
 * by coordinate (x, y).
 *
 * The coordinates (0, 0) indicates the top-left corner and ([width] - 1, [height] - 1)
 * indicates the bottom-right corner.
 */
interface PixelMap : Sequence<Pixel> {

	/**
	 * The number of columns.
	 */
	val width: Int

	/**
	 * The number of rows.
	 */
	val height: Int

	/**
	 * width x height
	 */
	val size: Int2D

	operator fun get(x: Int, y: Int): Pixel {
		if (x !in 0..<width || y !in 0..<height) {
			throw IndexOutOfBoundsException("Coordinates ($x, $y) does not comply to 0 <= x < $width and 0 <= y < $height")
		}
		return uncheckedGet(x, y)
	}

	operator fun set(x: Int, y: Int, pixel: Pixel): Boolean =
		x in 0..<width && y in 0..<height && uncheckedSet(x, y, pixel)

	fun uncheckedGet(x: Int, y: Int): Pixel
	fun uncheckedSet(x: Int, y: Int, pixel: Pixel): Boolean

	fun clear(pixel: Pixel)
	fun clear(pixelByXY: (x: Int, y: Int) -> Pixel)

	override fun iterator(): Iterator<Pixel> =
		iterator {
			for (y in 0..<height) {
				for (x in 0..<width) {
					yield(uncheckedGet(x, y))
				}
			}
		}

}
