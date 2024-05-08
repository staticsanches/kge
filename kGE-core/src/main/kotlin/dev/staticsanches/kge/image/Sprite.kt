@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package dev.staticsanches.kge.image

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.image.pixelmap.PixelMap
import dev.staticsanches.kge.image.pixelmap.buffer.RGBABuffer
import dev.staticsanches.kge.resource.KGEResource
import kotlin.math.abs
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

	override operator fun get(x: Int, y: Int): Pixel =
		when (sampleMode) {
			SampleMode.NORMAL -> if (x in 0..<width && y in 0..<height) uncheckedGet(x, y) else Colors.BLANK
			SampleMode.PERIODIC -> uncheckedGet(abs(x % width), abs(y % height))
			SampleMode.CLAMP -> uncheckedGet(max(0, min(x, width - 1)), max(0, min(y, height - 1)))
		}

	@OptIn(KGESensitiveAPI::class)
	override fun toString(): String = "Sprite $pixmap"

	enum class SampleMode { NORMAL, PERIODIC, CLAMP }

	enum class Flip { NONE, HORIZONTAL, VERTICAL, BOTH }

}
