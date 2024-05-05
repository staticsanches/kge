@file:Suppress("unused")

package dev.staticsanches.kge.image.service

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.spi.KGESPIExtensible
import dev.staticsanches.kge.types.vector.Int2D
import java.io.InputStream
import java.net.URL


/**
 * An extensible service that is capable of loading/creating [Sprite].
 */
interface SpriteService : KGESPIExtensible {

	fun create(width: Int, height: Int, defaultPixel: Pixel): Sprite

	fun create(width: Int, height: Int, pixelByXY: (x: Int, y: Int) -> Pixel): Sprite

	fun duplicate(sprite: Sprite): Sprite

	fun duplicate(sprite: Sprite, position: Int2D, size: Int2D): Sprite

	fun load(fileName: String): Sprite

	fun load(url: URL): Sprite

	fun load(isProvider: () -> InputStream): Sprite

	companion object : SpriteService by instance

}

private val instance: SpriteService = KGESPIExtensible.getWithHigherPriority()
