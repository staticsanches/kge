package dev.staticsanches.kge.renderer.internal

import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.GLBuffer
import dev.staticsanches.kge.renderer.gl.GLProgram
import dev.staticsanches.kge.renderer.gl.GLShader
import dev.staticsanches.kge.renderer.gl.GLVertexArrayObject
import dev.staticsanches.kge.renderer.gl.GLenum
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.letClosingIfFailed

/*
 * The renderer's internal T1 wrappers over the raw GL objects. Each wrapper
 * owns one handle: its creation path is recorded through [GL], `close`
 * deletes the handle exactly once, and any use afterwards fails fast. A
 * failure while the object is being built (a failed compile or link) closes
 * it before the failure propagates (letClosingIfFailed).
 */

/** Creates, fills and compiles a shader of [type]; the caller owns it. */
internal fun createShaderResource(
    type: GLenum,
    source: String,
    name: String,
): ResourceWrapper<GLShader> {
    val handle = GL.createShader(type)
    return ResourceWrapper(
        "$name shader",
        handle,
        KGECleanAction { GL.deleteShader(handle) },
    ).letClosingIfFailed { shader ->
        GL.shaderSource(shader.resource, source)
        GL.compileShader(shader.resource)
        shader
    }
}

/**
 * Compiles [vertexSource] and [fragmentSource], links them into a program and
 * returns the program. The shaders are deleted once linked; a failed compile or
 * link frees everything already allocated.
 */
internal fun createProgramResource(
    vertexSource: String,
    fragmentSource: String,
    name: String,
): ResourceWrapper<GLProgram> =
    createShaderResource(GL.VERTEX_SHADER, vertexSource, "$name vertex").use { vertex ->
        createShaderResource(GL.FRAGMENT_SHADER, fragmentSource, "$name fragment").use { fragment ->
            val handle = GL.createProgram()
            ResourceWrapper(
                "$name program",
                handle,
                KGECleanAction { GL.deleteProgram(handle) },
            ).letClosingIfFailed { program ->
                GL.attachShader(program.resource, vertex.resource)
                GL.attachShader(program.resource, fragment.resource)
                GL.linkProgram(program.resource)
                program
            }
        }
    }

/** Creates an empty GL buffer; the caller owns it. */
internal fun createBufferResource(name: String): ResourceWrapper<GLBuffer> {
    val handle = GL.createBuffer()
    return ResourceWrapper(
        "$name buffer",
        handle,
        KGECleanAction { GL.deleteBuffer(handle) },
    )
}

/** Creates an empty vertex array object; the caller owns it. */
internal fun createVertexArrayResource(name: String): ResourceWrapper<GLVertexArrayObject> {
    val handle = GL.createVertexArray()
    return ResourceWrapper(
        "$name VAO",
        handle,
        KGECleanAction { GL.deleteVertexArray(handle) },
    )
}
