package dev.staticsanches.kge.renderer.internal

import dev.staticsanches.kge.renderer.device.GpuDevice
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.GLProgram
import dev.staticsanches.kge.renderer.gl.GLVertexArrayObject
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.letClosingIfFailed

/**
 * The renderer's built-in 2D program and the geometry it draws through.
 *
 * The program is the single quad shader the renderer uses for both layer quads
 * and decals: a `pos4`/`uv2`/`col4` vertex layout whose fragment is
 * `texture * color`. It is built through the general [GL] shader/program
 * operations, so a future user-shader concept can build on the same seam. The
 * vertex array object binds the [staging] buffer's attributes once; the
 * trailing `z`/`w` of the position attribute are carried but unused by this 2D
 * program.
 *
 * The built-in objects are created eagerly by the renderer's `createResources`
 * while a context is current, and are owned by the caller's
 * [dev.staticsanches.kge.resource.ResourceScope]. Each is freed on construction
 * failure ([letClosingIfFailed]-style guards). [close] makes [device]'s context
 * current first, because the scope does not own a context.
 */
internal class BuiltInQuad private constructor(
    private val device: GpuDevice,
    private val program: ResourceWrapper<GLProgram>,
    private val vertexArray: ResourceWrapper<GLVertexArrayObject>,
    val staging: StagingBuffer,
) : KGEResource {
    /** The linked built-in program. */
    val programHandle: GLProgram get() = program.resource

    /** The vertex array object the program's attributes are bound to. */
    val vertexArrayHandle: GLVertexArrayObject get() = vertexArray.resource

    override fun close() {
        device.makeCurrent()
        staging.close()
        vertexArray.close()
        program.close()
    }

    companion object {
        operator fun invoke(device: GpuDevice): BuiltInQuad {
            val program = createProgramResource(vertexShaderSource(), fragmentShaderSource(), "built-in quad")
            return program.letClosingIfFailed { programResource ->
                val vertexArray = createVertexArrayResource("built-in quad")
                vertexArray.letClosingIfFailed { vertexArrayResource ->
                    val staging = StagingBuffer("built-in quad")
                    staging.letClosingIfFailed { stagingResource ->
                        bindGeometry(vertexArrayResource, stagingResource)
                        BuiltInQuad(device, programResource, vertexArrayResource, stagingResource)
                    }
                }
            }
        }

        private fun bindGeometry(
            vertexArray: ResourceWrapper<GLVertexArrayObject>,
            staging: StagingBuffer,
        ) {
            GL.bindVertexArray(vertexArray.resource)
            GL.bindBuffer(GL.ARRAY_BUFFER, staging.buffer)

            GL.vertexAttribPointer(0, VertexLayout.POSITION_COMPONENTS, GL.FLOAT, false, VertexLayout.BYTES, 0)
            GL.enableVertexAttribArray(0)
            GL.vertexAttribPointer(
                1,
                VertexLayout.UV_COMPONENTS,
                GL.FLOAT,
                false,
                VertexLayout.BYTES,
                VertexLayout.UV_OFFSET,
            )
            GL.enableVertexAttribArray(1)
            GL.vertexAttribPointer(
                2,
                VertexLayout.COLOR_COMPONENTS,
                GL.UNSIGNED_BYTE,
                true,
                VertexLayout.BYTES,
                VertexLayout.COLOR_OFFSET,
            )
            GL.enableVertexAttribArray(2)

            GL.bindBuffer(GL.ARRAY_BUFFER, null)
            GL.bindVertexArray(null)
        }

        private fun vertexShaderSource(): String =
            """
            #version $glslVersion
            precision mediump float;

            layout(location = 0) in vec4 aPos;
            layout(location = 1) in vec2 aTex;
            layout(location = 2) in vec4 aCol;

            out vec2 oTex;
            out vec4 oCol;

            void main() {
                gl_Position = vec4(aPos.xy, 0.0, 1.0);
                oTex = aTex;
                oCol = aCol;
            }
            """.trimIndent()

        private fun fragmentShaderSource(): String =
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
            """.trimIndent()
    }
}
