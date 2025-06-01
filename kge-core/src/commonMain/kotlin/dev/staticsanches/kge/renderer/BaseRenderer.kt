package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.engine.Window
import dev.staticsanches.kge.image.Decal
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.DecalInstance.VerticesInfo.Companion.VERTEX_BYTES_COUNT
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.GLTexture
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.applyClosingIfFailed
import dev.staticsanches.kge.resource.toCleanerProvider

internal abstract class BaseRenderer : Renderer {
    private var decalMode: Decal.Mode? = null
        set(value) {
            if (value == null || value == field) {
                field = value
                return
            }
            when (value) {
                Decal.Mode.NORMAL -> GL.blendFunc(GL.SRC_ALPHA, GL.ONE_MINUS_SRC_ALPHA)
                Decal.Mode.ADDITIVE -> GL.blendFunc(GL.SRC_ALPHA, GL.ONE)
                Decal.Mode.MULTIPLICATIVE -> GL.blendFunc(GL.DST_COLOR, GL.ONE_MINUS_SRC_ALPHA)
                Decal.Mode.STENCIL -> GL.blendFunc(GL.ZERO, GL.SRC_ALPHA)
                Decal.Mode.ILLUMINATE -> GL.blendFunc(GL.ONE_MINUS_SRC_ALPHA, GL.SRC_ALPHA)
                Decal.Mode.WIREFRAME -> GL.blendFunc(GL.SRC_ALPHA, GL.ONE_MINUS_SRC_ALPHA)
            }
        }

    private lateinit var quadInfo: QuadInfo

    protected abstract val glslVersion: String

    override fun afterWindowCreation(window: Window) {
        quadInfo = QuadInfo(glslVersion)
//        GL.enable(GL.TEXTURE_2D)
    }

    override fun prepareDrawing() {
        GL.enable(GL.BLEND)
        decalMode = null
        decalMode = Decal.Mode.NORMAL // force decal mode reassignment

        val quadInfo = quadInfo
        GL.useProgram(quadInfo.program)
        GL.bindVertexArray(quadInfo.vao)

        GL.disable(GL.CULL_FACE)
        GL.depthFunc(GL.LESS)
    }

    override fun drawLayerQuad(
        offset: Float2D,
        scale: Float2D,
        tint: Pixel,
    ) {
        val quadInfo = quadInfo

        GL.disable(GL.CULL_FACE)
        GL.bindBuffer(GL.ARRAY_BUFFER, quadInfo.buffer)

        quadInfo.bufferSubData { data ->
            data
                // Vertex 1
                .putFloat(-1f) // x
                .putFloat(-1f) // y
                .putFloat(1f) // z
                .putFloat(0f) // w
                .putFloat(0f * scale.x + offset.x) // u
                .putFloat(1f * scale.y + offset.y) // v
                .putInt(tint.nativeRGBA) // tint
                // Vertex 2
                .putFloat(+1f) // x
                .putFloat(-1f) // y
                .putFloat(1f) // z
                .putFloat(0f) // w
                .putFloat(1f * scale.x + offset.x) // u
                .putFloat(1f * scale.y + offset.y) // v
                .putInt(tint.nativeRGBA) // tint
                // Vertex 3
                .putFloat(-1f) // x
                .putFloat(+1f) // y
                .putFloat(1f) // z
                .putFloat(0f) // w
                .putFloat(0f * scale.x + offset.x) // u
                .putFloat(0f * scale.y + offset.y) // v
                .putInt(tint.nativeRGBA) // tint
                // Vertex 4
                .putFloat(+1f) // x
                .putFloat(+1f) // y
                .putFloat(1f) // z
                .putFloat(0f) // w
                .putFloat(1f * scale.x + offset.x) // u
                .putFloat(0f * scale.y + offset.y) // v
                .putInt(tint.nativeRGBA) // tint
        }

        GL.drawArrays(GL.TRIANGLE_STRIP, 0, 4)
    }

    override fun drawDecals(dis: List<DecalInstance>) {
        GL.disable(GL.CULL_FACE)
        GL.bindBuffer(GL.ARRAY_BUFFER, quadInfo.buffer)

        val quadInfo = quadInfo
        var index = 0
        while (index < dis.size) {
            val di = dis[index]

            val firsts = quadInfo.firsts.clear()
            val counts = quadInfo.counts.clear()

            quadInfo.bufferSubData { data ->
                var nextFirst = 0

                fun collect(instance: DecalInstance) =
                    with(instance.verticesInfo) {
                        putAll(data)
                        firsts.put(nextFirst)
                        nextFirst += numberOfVertices
                        counts.put(numberOfVertices)
                    }

                collect(di)

                var peekIndex = index + 1
                while (peekIndex < dis.size) {
                    if (
                        data.remaining() < VERTEX_BYTES_COUNT ||
                        !firsts.hasRemaining()
                    ) {
                        break // filled buffers
                    }

                    val diPeek = dis[peekIndex++]
                    if (
                        di.mode != diPeek.mode ||
                        di.structure != diPeek.structure ||
                        di.decal?.uuid != diPeek.decal?.uuid
                    ) {
                        break // peek is not compatible
                    }

                    collect(diPeek)
                }
            }

            decalMode = di.mode
            GL.bindTexture(GL.TEXTURE_2D, (di.decal ?: quadInfo.blankDecal).resource)

            index += firsts.position()

            if (firsts.position() > 1) {
                GL.multiDrawArrays(
                    if (di.mode == Decal.Mode.WIREFRAME) {
                        GL.LINE_LOOP
                    } else {
                        when (di.structure) {
                            Decal.Structure.FAN -> GL.TRIANGLE_FAN
                            Decal.Structure.STRIP -> GL.TRIANGLE_STRIP
                            Decal.Structure.LIST -> GL.TRIANGLES
                            Decal.Structure.LINE -> GL.LINES
                        }
                    },
                    firsts.flip(), counts.flip(),
                )
            } else {
                GL.drawArrays(
                    if (di.mode == Decal.Mode.WIREFRAME) {
                        GL.LINE_LOOP
                    } else {
                        when (di.structure) {
                            Decal.Structure.FAN -> GL.TRIANGLE_FAN
                            Decal.Structure.STRIP -> GL.TRIANGLE_STRIP
                            Decal.Structure.LIST -> GL.TRIANGLES
                            Decal.Structure.LINE -> GL.LINES
                        }
                    },
                    0, di.verticesInfo.numberOfVertices,
                )
            }
        }
    }

    override fun createTexture(
        name: String?,
        filtered: Boolean,
        clamp: Boolean,
    ): ResourceWrapper<GLTexture> =
        ResourceWrapper
            .Companion({ name ?: "GLTexture $it" }, GL::createTexture, GL::deleteTexture.toCleanerProvider())
            .applyClosingIfFailed {
                GL.bindTexture(GL.TEXTURE_2D, resource)

                if (filtered) {
                    GL.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MAG_FILTER, GL.LINEAR)
                    GL.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MIN_FILTER, GL.LINEAR)
                } else {
                    GL.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MAG_FILTER, GL.NEAREST)
                    GL.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MIN_FILTER, GL.NEAREST)
                }

                if (clamp) {
                    GL.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_S, GL.CLAMP_TO_EDGE)
                    GL.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_T, GL.CLAMP_TO_EDGE)
                } else {
                    GL.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_S, GL.REPEAT)
                    GL.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_T, GL.REPEAT)
                }
            }

    override fun applyTexture(texture: GLTexture) = GL.bindTexture(GL.TEXTURE_2D, texture)

    override fun updateTexture(
        texture: GLTexture,
        sprite: Sprite,
    ) {
        GL.bindTexture(GL.TEXTURE_2D, texture)
        GL.texImage2D(
            GL.TEXTURE_2D,
            0,
            GL.RGBA,
            sprite.width,
            sprite.height,
            0,
            GL.RGBA,
            GL.UNSIGNED_BYTE,
            sprite.resource.clear(),
        )
    }

    override fun readTexture(
        texture: GLTexture,
        sprite: Sprite,
    ) {
        GL.bindTexture(GL.TEXTURE_2D, texture)
        GL.readPixels(0, 0, sprite.width, sprite.height, GL.RGBA, GL.UNSIGNED_BYTE, sprite.resource.clear())
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

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
