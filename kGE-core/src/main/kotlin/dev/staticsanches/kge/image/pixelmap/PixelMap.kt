package dev.staticsanches.kge.image.pixelmap

import dev.staticsanches.kge.image.IntColorComponent
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.types.vector.Float2D
import dev.staticsanches.kge.types.vector.Int2D
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min


/**
 * It is logically a 2D matrix of [Pixel]s with a "row major" configuration and "direct access"
 * by coordinates (x, y).
 *
 * (0, 0) indicates the top-left corner and ([width] - 1, [height] - 1) indicates the bottom-right corner.
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

	fun inv()

	fun getPixel(x: Int, y: Int): Pixel = this[x, y]
	fun setPixel(x: Int, y: Int, pixel: Pixel): Boolean = set(x, y, pixel)

	fun getPixel(position: Int2D): Pixel = this[position]
	fun setPixel(position: Int2D, pixel: Pixel): Boolean = set(position, pixel)

	operator fun get(position: Int2D): Pixel = this[position.x, position.y]
	operator fun set(position: Int2D, pixel: Pixel): Boolean = set(position.x, position.y, pixel)

	fun sample(uv: Float2D): Pixel = sample(uv.x, uv.y)

	fun sample(u: Float, v: Float): Pixel =
		this[min((u * width).toInt(), width - 1), min((v * height).toInt(), height - 1)]

	fun sampleBL(uv: Float2D): Pixel = sampleBL(uv.x, uv.y)

	fun sampleBL(u: Float, v: Float): Pixel {
		val computedU = u * width - 0.5f
		val computedV = v * height - 0.5f

		val x = floor(computedU).toInt()
		val y = floor(computedV).toInt()
		val uRatio = computedU - x
		val vRatio = computedV - y
		val uOpposite = 1 - uRatio
		val vOpposite = 1 - vRatio

		val x0 = max(x, 0)
		val y0 = max(y, 0)
		val x1 = min(x + 1, width - 1)
		val y1 = min(y + 1, height - 1)

		val p1 = this[x0, y0]
		val p2 = this[x1, y0]
		val p3 = this[x0, y1]
		val p4 = this[x1, y1]

		fun calculateComponent(componentGetter: (Pixel) -> IntColorComponent): Float {
			val v1 = componentGetter(p1)
			val v2 = componentGetter(p2)
			val v3 = componentGetter(p3)
			val v4 = componentGetter(p4)

			return ((v1 * uOpposite + v2 * uRatio) * vOpposite + (v3 * uOpposite + v4 * uRatio) * vRatio)
		}

		return Pixel.rgba(calculateComponent(Pixel::r), calculateComponent(Pixel::g), calculateComponent(Pixel::b))
	}

	fun duplicate(): PixelMap

	override fun iterator(): Iterator<Pixel> =
		iterator {
			for (y in 0..<height) {
				for (x in 0..<width) {
					yield(uncheckedGet(x, y))
				}
			}
		}

}
