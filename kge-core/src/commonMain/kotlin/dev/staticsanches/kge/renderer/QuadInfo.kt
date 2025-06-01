@file:Suppress("unused")

package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.buffer.wrapper.ByteBufferWrapper
import dev.staticsanches.kge.buffer.wrapper.IntBufferWrapper
import dev.staticsanches.kge.configuration.KGEConfiguration
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Decal
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.extension.create
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.wrapper.GLBufferWrapper
import dev.staticsanches.kge.renderer.gl.wrapper.GLProgramWrapper
import dev.staticsanches.kge.renderer.gl.wrapper.GLVertexArrayObjectWrapper
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.letClosingIfFailed
import dev.staticsanches.kge.utils.BytesSize.FLOAT
import dev.staticsanches.kge.utils.BytesSize.INT
import dev.staticsanches.kge.utils.invokeForAll

internal class QuadInfo private constructor(
    private val programWrapper: GLProgramWrapper,
    private val bufferWrapper: GLBufferWrapper,
    private val bufferDataWrapper: ByteBufferWrapper,
    private val vaoWrapper: GLVertexArrayObjectWrapper,
    private val firstWrapper: IntBufferWrapper,
    private val countWrapper: IntBufferWrapper,
    val blankDecal: Decal,
    val maxNumberOfVertices: Int,
    val maxNumberOfDecals: Int,
) : KGEResource {
    val program by programWrapper
    val buffer by bufferWrapper
    val vao by vaoWrapper

    init {
        GL.bindVertexArray(vao)
        GL.bindBuffer(GL.ARRAY_BUFFER, buffer)
        GL.bufferData(GL.ARRAY_BUFFER, maxNumberOfVertices * VERTEX_SIZE, GL.STREAM_DRAW) // allocates buffer data

        GL.vertexAttribPointer(0, 4, GL.FLOAT, false, VERTEX_SIZE, 0)
        GL.enableVertexAttribArray(0)
        GL.vertexAttribPointer(1, 2, GL.FLOAT, false, VERTEX_SIZE, 4 * FLOAT)
        GL.enableVertexAttribArray(1)
        GL.vertexAttribPointer(2, 4, GL.UNSIGNED_BYTE, true, VERTEX_SIZE, 6 * FLOAT)
        GL.enableVertexAttribArray(2)

        GL.bindBuffer(GL.ARRAY_BUFFER, null)
        GL.bindVertexArray(null)
    }

    inline fun bufferSubData(block: ByteBuffer.() -> Unit) =
        bufferDataWrapper.resource.clear().apply {
            block()
            GL.bufferSubData(GL.ARRAY_BUFFER, 0, flip())
        }

    override fun close() =
        invokeForAll(
            programWrapper,
            bufferWrapper,
            bufferDataWrapper,
            vaoWrapper,
            blankDecal.sprite,
            blankDecal,
            firstWrapper,
            countWrapper,
        ) { it.close() }

    companion object {
        const val VERTEX_SIZE = 4 * FLOAT + 2 * FLOAT + 1 * INT

        operator fun invoke(glslVersion: String): QuadInfo =
            GLProgramWrapper(
                vertexShader =
                    """
                    #version $glslVersion
                    precision mediump float;

                    layout(location = 0) in vec4 aPos;
                    layout(location = 1) in vec2 aTex;
                    layout(location = 2) in vec4 aCol;

                    out vec2 oTex;
                    out vec4 oCol;

                    void main() {
                        float p = 1.0 / aPos.z;
                        gl_Position = p * vec4(aPos.x, aPos.y, 0.0, 1.0);
                        oTex = p * aTex;
                        oCol = aCol;
                    }
                    """.trimIndent(),
                fragmentShader =
                    """
                    #version $glslVersion
                    precision mediump float;

                    in vec2 oTex;
                    in vec4 oCol;

                    uniform sampler2D sprTex;

                    out vec4 pixel;

                    void main() {
                        pixel = texture(sprTex, oTex) * oCol;
                    }
                    """.trimIndent(),
                name = "Quad Program",
            ).letClosingIfFailed { QuadInfo(it) }

        private operator fun invoke(programWrapper: GLProgramWrapper): QuadInfo =
            GLBufferWrapper { "Quad Buffer" }.letClosingIfFailed {
                QuadInfo(
                    programWrapper = programWrapper,
                    bufferWrapper = it,
                )
            }

        private operator fun invoke(
            programWrapper: GLProgramWrapper,
            bufferWrapper: GLBufferWrapper,
        ): QuadInfo =
            GLVertexArrayObjectWrapper("Quad VAO").letClosingIfFailed {
                QuadInfo(
                    programWrapper = programWrapper,
                    bufferWrapper = bufferWrapper,
                    vaoWrapper = it,
                )
            }

        private operator fun invoke(
            programWrapper: GLProgramWrapper,
            bufferWrapper: GLBufferWrapper,
            vaoWrapper: GLVertexArrayObjectWrapper,
        ): QuadInfo =
            Sprite
                .create(
                    width = 1, height = 1,
                    sampleMode = Sprite.SampleMode.NORMAL,
                    color = Colors.WHITE,
                    name = "Quad Blank Sprite",
                ).letClosingIfFailed { sprite ->
                    Decal(sprite).letClosingIfFailed {
                        QuadInfo(
                            programWrapper = programWrapper,
                            bufferWrapper = bufferWrapper,
                            vaoWrapper = vaoWrapper,
                            blankDecal = it,
                        )
                    }
                }

        private operator fun invoke(
            programWrapper: GLProgramWrapper,
            bufferWrapper: GLBufferWrapper,
            vaoWrapper: GLVertexArrayObjectWrapper,
            blankDecal: Decal,
        ): QuadInfo {
            val maxNumberOfVertices = KGEConfiguration.maxNumberOfVertices
            val maxNumberOfDecals = maxNumberOfVertices / 4 // each decal by default has 4 vertices
            return ByteBufferWrapper(VERTEX_SIZE * maxNumberOfVertices) { "Quad Buffer Data" }
                .letClosingIfFailed { bufferDataWrapper ->
                    IntBufferWrapper(maxNumberOfDecals) { "Quad First" }
                        .letClosingIfFailed { firstWrapper ->
                            IntBufferWrapper(maxNumberOfDecals) { "Quad Count" }
                                .letClosingIfFailed { countWrapper ->
                                    QuadInfo(
                                        programWrapper = programWrapper,
                                        bufferWrapper = bufferWrapper,
                                        bufferDataWrapper = bufferDataWrapper,
                                        vaoWrapper = vaoWrapper,
                                        firstWrapper = firstWrapper,
                                        countWrapper = countWrapper,
                                        blankDecal = blankDecal,
                                        maxNumberOfVertices = maxNumberOfVertices,
                                        maxNumberOfDecals = maxNumberOfDecals,
                                    )
                                }
                        }
                }
        }
    }
}
