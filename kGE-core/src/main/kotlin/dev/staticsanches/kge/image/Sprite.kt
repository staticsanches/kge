@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package dev.staticsanches.kge.image

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.image.pixelmap.PixelMap
import dev.staticsanches.kge.image.pixelmap.buffer.RGBABuffer
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.types.vector.Float2D
import dev.staticsanches.kge.types.vector.Int2D
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min


/**
 * Representation of an image in KGE.
 */
open class Sprite(
	@KGESensitiveAPI
	val pixmap: RGBABuffer,
	var sampleMode: SampleMode = SampleMode.NORMAL
) : PixelMap by pixmap, KGEResource by pixmap {

	fun getPixel(x: Int, y: Int): Pixel = this[x, y]
	fun setPixel(x: Int, y: Int, pixel: Pixel): Boolean = set(x, y, pixel)

	fun getPixel(position: Int2D): Pixel = this[position]
	fun setPixel(position: Int2D, pixel: Pixel): Boolean = set(position, pixel)

	operator fun get(position: Int2D): Pixel = this[position.x, position.y]
	operator fun set(position: Int2D, pixel: Pixel): Boolean = set(position.x, position.y, pixel)

	override operator fun get(x: Int, y: Int): Pixel =
		when (sampleMode) {
			SampleMode.NORMAL -> super.get(x, y)
			SampleMode.PERIODIC -> uncheckedGet(abs(x % width), abs(y % height))
			SampleMode.CLAMP -> uncheckedGet(max(0, min(x, width - 1)), max(0, min(y, height - 1)))
		}

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

		fun calculateComponent(componentGetter: (Pixel) -> IntColorComponent): Int {
			val v1 = componentGetter(p1)
			val v2 = componentGetter(p2)
			val v3 = componentGetter(p3)
			val v4 = componentGetter(p4)

			return ((v1 * uOpposite + v2 * uRatio) * vOpposite + (v3 * uOpposite + v4 * uRatio) * vRatio).toInt()
		}

		return Pixel.rgba(calculateComponent(Pixel::r), calculateComponent(Pixel::g), calculateComponent(Pixel::b))
	}

	@OptIn(KGESensitiveAPI::class)
	override fun toString(): String = "Sprite $pixmap"

	enum class SampleMode { NORMAL, PERIODIC, CLAMP }

	enum class Flip { NONE, HORIZONTAL, VERTICAL, BOTH }

}
