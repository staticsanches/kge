package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.engine.window.Window
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.spi.KGESPIExtensible
import dev.staticsanches.kge.types.vector.Float2D
import dev.staticsanches.kge.types.vector.Int2D


interface Renderer : KGESPIExtensible {

	fun beforeWindowCreation()

	context(Window)
	fun afterWindowCreation()

	context(Window)
	fun prepareDrawing()

	context(Window)
	fun createTexture(filtered: Boolean, clamp: Boolean): Int

	context(Window)
	fun deleteTexture(id: Int)

	context(Window)
	fun updateTexture(id: Int, sprite: Sprite)

	context(Window)
	fun readTexture(id: Int, sprite: Sprite)

	context(Window)
	fun applyTexture(id: Int)

	context(Window)
	fun clearBuffer(pixel: Pixel, depth: Boolean)

	context(Window)
	fun updateViewport(position: Int2D, size: Int2D)

	context(Window)
	fun displayFrame()

	context(Window)
	fun drawDecal(decal: DecalInstance)

	context(Window)
	fun drawLayerQuad(offset: Float2D, scale: Float2D, tint: Pixel)

	companion object : Renderer by KGESPIExtensible.getOptionalWithHigherPriority() ?: DefaultRenderer

}

internal data object DefaultRenderer : Renderer {

	var delegate: Renderer = GL11Renderer

	override fun beforeWindowCreation() = delegate.beforeWindowCreation()

	context(Window)
	override fun afterWindowCreation() = delegate.afterWindowCreation()

	context(Window)
	override fun prepareDrawing() = delegate.prepareDrawing()

	context(Window)
	override fun createTexture(filtered: Boolean, clamp: Boolean): Int = delegate.createTexture(filtered, clamp)

	context(Window)
	override fun deleteTexture(id: Int) = delegate.deleteTexture(id)

	context(Window)
	override fun updateTexture(id: Int, sprite: Sprite) = delegate.updateTexture(id, sprite)

	context(Window)
	override fun readTexture(id: Int, sprite: Sprite) = delegate.readTexture(id, sprite)

	context(Window)
	override fun applyTexture(id: Int) = delegate.applyTexture(id)

	context(Window)
	override fun clearBuffer(pixel: Pixel, depth: Boolean) = delegate.clearBuffer(pixel, depth)

	context(Window)
	override fun updateViewport(position: Int2D, size: Int2D) = delegate.updateViewport(position, size)

	context(Window)
	override fun displayFrame() = delegate.displayFrame()

	context(Window)
	override fun drawDecal(decal: DecalInstance) = delegate.drawDecal(decal)

	context(Window)
	override fun drawLayerQuad(offset: Float2D, scale: Float2D, tint: Pixel) =
		delegate.drawLayerQuad(offset, scale, tint)

	override val servicePriority: Int
		get() = Int.MIN_VALUE

}
