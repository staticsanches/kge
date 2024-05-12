package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.window.Window
import dev.staticsanches.kge.image.Decal
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.types.vector.Float2D
import dev.staticsanches.kge.types.vector.Int2D
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11.*

@OptIn(KGESensitiveAPI::class)
internal data object GL11Renderer : Renderer {

	context(Window)
	private var decalMode: Decal.Mode
		get() = getExtraInfo(DecalModeKey) ?: Decal.Mode.NORMAL
		set(decalMode) {
			val oldDecalMode = putExtraInfo(DecalModeKey, decalMode)
			if (oldDecalMode != decalMode) {
				when (decalMode) {
					Decal.Mode.NORMAL -> glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
					Decal.Mode.ADDITIVE -> glBlendFunc(GL_SRC_ALPHA, GL_ONE)
					Decal.Mode.MULTIPLICATIVE -> glBlendFunc(GL_DST_COLOR, GL_ONE_MINUS_SRC_ALPHA)
					Decal.Mode.STENCIL -> glBlendFunc(GL_ZERO, GL_SRC_ALPHA)
					Decal.Mode.ILLUMINATE -> glBlendFunc(GL_ONE_MINUS_SRC_ALPHA, GL_SRC_ALPHA)
					Decal.Mode.WIREFRAME -> glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
				}
			}
		}

	override fun beforeWindowCreation() {
		// Request OpenGL 1.1
		GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 1)
		GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 1)
	}

	context(Window)
	override fun afterWindowCreation() {
		glEnable(GL_BLEND)
		glEnable(GL_TEXTURE_2D)
		glHint(GL_PERSPECTIVE_CORRECTION_HINT, GL_NICEST)
		prepareDrawing()
	}

	context(Window)
	override fun prepareDrawing() {
		glEnable(GL_BLEND)
		decalMode = Decal.Mode.NORMAL
	}

	context(Window)
	override fun createTexture(filtered: Boolean, clamp: Boolean): Int {
		val id = glGenTextures()
		glBindTexture(GL_TEXTURE_2D, id)

		if (filtered) {
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
		} else {
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
		}

		if (clamp) {
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP)
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP)
		} else {
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT)
		}

		glTexEnvi(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_MODULATE)

		return id
	}

	context(Window)
	override fun deleteTexture(id: Int) = glDeleteTextures(id)

	context(Window)
	override fun updateTexture(id: Int, sprite: Sprite) {
		glBindTexture(GL_TEXTURE_2D, id)
		glTexImage2D(
			GL_TEXTURE_2D, 0, GL_RGBA,
			sprite.width, sprite.height,
			0, GL_RGBA, GL_UNSIGNED_BYTE,
			sprite.pixmap.internalBuffer.clear()
		)
	}

	context(Window)
	override fun readTexture(id: Int, sprite: Sprite) {
		glBindTexture(GL_TEXTURE_2D, id)
		glReadPixels(
			0, 0, sprite.width, sprite.height,
			GL_RGBA, GL_UNSIGNED_BYTE,
			sprite.pixmap.internalBuffer.clear()
		)
	}

	context(Window)
	override fun applyTexture(id: Int) = glBindTexture(GL_TEXTURE_2D, id)

	context(Window)
	override fun clearBuffer(pixel: Pixel, depth: Boolean) {
		glClearColor(pixel.r / 255f, pixel.g / 255f, pixel.b / 255f, pixel.a / 255f)
		glClear(GL_COLOR_BUFFER_BIT)
		if (depth) {
			glClear(GL_DEPTH_BUFFER_BIT)
		}
	}

	context(Window)
	override fun updateViewport(position: Int2D, size: Int2D) = glViewport(position.x, position.y, size.x, size.y)

	context(Window)
	override fun displayFrame() = GLFW.glfwSwapBuffers(glfwHandle)

	context(Window)
	override fun drawDecal(decal: DecalInstance) {
		decalMode = decal.mode
		glBindTexture(GL_TEXTURE_2D, decal.decal?.id ?: 0)

		if (decal.mode == Decal.Mode.WIREFRAME) {
			glBegin(GL_LINE_LOOP)
		} else {
			when (decal.structure) {
				Decal.Structure.LINE -> glBegin(GL_LINE_LOOP)
				Decal.Structure.FAN -> glBegin(GL_TRIANGLE_FAN)
				Decal.Structure.STRIP -> glBegin(GL_TRIANGLE_STRIP)
				Decal.Structure.LIST -> glBegin(GL_TRIANGLES)
			}
		}

		// Render as 2D Spatial entity
		for ((position, uv, w, tint) in decal.points) {
			glColor4ub(tint.r.toByte(), tint.g.toByte(), tint.b.toByte(), tint.a.toByte())
			glTexCoord4f(uv.x, uv.y, 0.0f, w)
			glVertex2f(position.x, position.y)
		}

		glEnd()
	}

	context(Window)
	override fun drawLayerQuad(offset: Float2D, scale: Float2D, tint: Pixel) {
		glBegin(GL_QUADS)
		glColor4ub(tint.r.toByte(), tint.g.toByte(), tint.b.toByte(), tint.a.toByte())
		glTexCoord2f(0.0f * scale.x + offset.x, 1.0f * scale.y + offset.y)
		glVertex3f(-1.0f /*+ vSubPixelOffset.x*/, -1.0f /*+ vSubPixelOffset.y*/, 0.0f)
		glTexCoord2f(0.0f * scale.x + offset.x, 0.0f * scale.y + offset.y)
		glVertex3f(-1.0f /*+ vSubPixelOffset.x*/, 1.0f /*+ vSubPixelOffset.y*/, 0.0f)
		glTexCoord2f(1.0f * scale.x + offset.x, 0.0f * scale.y + offset.y)
		glVertex3f(1.0f /*+ vSubPixelOffset.x*/, 1.0f /*+ vSubPixelOffset.y*/, 0.0f)
		glTexCoord2f(1.0f * scale.x + offset.x, 1.0f * scale.y + offset.y)
		glVertex3f(1.0f /*+ vSubPixelOffset.x*/, -1.0f /*+ vSubPixelOffset.y*/, 0.0f)
		glEnd()
	}

	override val servicePriority: Int
		get() = Int.MIN_VALUE

	private data object DecalModeKey : Window.ExtraInfoKey<Decal.Mode>

}
