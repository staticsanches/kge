package dev.staticsanches.kge.renderer.internal

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.Renderer
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.renderer.device.GpuDevice
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.resource.Texture
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.resource.letClosingIfFailed

/**
 * The engine-defined common [Renderer] default: drawing logic over the
 * overridable [GL], with no state of its own. The built-in quad program and its
 * staging buffer are built eagerly by [createResources] into the engine-owned
 * [ResourceScope] and resolved from it by every draw.
 */
internal class DefaultRenderer : Renderer {
    override fun createResources(
        device: GpuDevice,
        scope: ResourceScope,
    ) {
        BuiltInQuad(device).letClosingIfFailed { scope.register(QuadKey, it) }
    }

    override fun prepareDrawing(scope: ResourceScope) {
        val quad = scope.get(QuadKey)
        GL.enable(GL.BLEND)
        GL.blendFunc(GL.SRC_ALPHA, GL.ONE_MINUS_SRC_ALPHA)
        GL.useProgram(quad.programHandle)
        GL.bindVertexArray(quad.vertexArrayHandle)
    }

    override fun createTexture(
        width: Int,
        height: Int,
        filter: Decal.Filter,
        wrap: Decal.Wrap,
        name: String?,
    ): Texture = Texture.create(width, height, filter.toGLFilter(), wrap.toGLWrap(), name)

    override fun updateTexture(
        texture: Texture,
        sprite: Sprite,
    ) = texture.update(sprite)

    override fun readTexture(
        texture: Texture,
        sprite: Sprite,
    ) = texture.read(sprite)

    override fun applyTexture(texture: Texture) = texture.apply()

    override fun drawLayerQuad(
        scope: ResourceScope,
        offset: Float2D,
        scale: Float2D,
        tint: Pixel,
    ) {
        val quad = scope.get(QuadKey)
        GL.disable(GL.CULL_FACE)

        val data = quad.staging.ensureCapacity(VertexLayout.BYTES * LAYER_QUAD_VERTICES)
        val u0 = offset.x
        val u1 = scale.x + offset.x
        val v0 = offset.y
        val v1 = scale.y + offset.y
        data.resource.putVertex(0 * VertexLayout.BYTES, -1f, -1f, 1f, 0f, u0, v1, tint)
        data.resource.putVertex(1 * VertexLayout.BYTES, 1f, -1f, 1f, 0f, u1, v1, tint)
        data.resource.putVertex(2 * VertexLayout.BYTES, -1f, 1f, 1f, 0f, u0, v0, tint)
        data.resource.putVertex(3 * VertexLayout.BYTES, 1f, 1f, 1f, 0f, u1, v0, tint)

        quad.staging.upload(VertexLayout.BYTES * LAYER_QUAD_VERTICES)
        GL.drawArrays(GL.TRIANGLE_STRIP, 0, LAYER_QUAD_VERTICES)
    }

    override fun drawDecal(
        scope: ResourceScope,
        instance: DecalInstance,
    ) {
        val quad = scope.get(QuadKey)
        GL.disable(GL.CULL_FACE)
        val (source, destination) = instance.mode.toGLBlend()
        GL.blendFunc(source, destination)
        instance.decal.texture.apply()

        val vertices = instance.vertices
        val count = vertices.vertexCount
        val data = quad.staging.ensureCapacity(VertexLayout.BYTES * count)
        for (index in 0 until count) {
            data.resource.putVertex(
                index * VertexLayout.BYTES,
                vertices.x(index),
                vertices.y(index),
                1f,
                0f,
                vertices.u(index),
                vertices.v(index),
                vertices.tint(index),
            )
        }

        quad.staging.upload(VertexLayout.BYTES * count)
        GL.drawArrays(instance.structure.toGLPrimitive(instance.mode), 0, count)
    }

    override fun clearBuffer(
        color: Pixel,
        depth: Boolean,
    ) {
        GL.clearColor(color.r / 255f, color.g / 255f, color.b / 255f, color.a / 255f)
        GL.clear(if (depth) GL.COLOR_BUFFER_BIT or GL.DEPTH_BUFFER_BIT else GL.COLOR_BUFFER_BIT)
    }

    override fun updateViewport(
        position: Int2D,
        size: Int2D,
    ) = GL.viewport(position.x, position.y, size.x, size.y)

    private companion object {
        object QuadKey : ResourceScope.Key<BuiltInQuad>

        /** Vertices of the full-screen layer quad. */
        const val LAYER_QUAD_VERTICES = 4
    }
}

/** The engine-defined default of [Renderer]. */
internal val rendererDefault: Renderer = DefaultRenderer()
