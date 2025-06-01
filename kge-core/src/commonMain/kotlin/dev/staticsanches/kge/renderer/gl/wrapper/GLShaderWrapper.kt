package dev.staticsanches.kge.renderer.gl.wrapper

import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.GLShader
import dev.staticsanches.kge.renderer.gl.GLenum
import dev.staticsanches.kge.renderer.gl.wrapper.ShaderType.FRAGMENT_SHADER
import dev.staticsanches.kge.renderer.gl.wrapper.ShaderType.VERTEX_SHADER
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.applyClosingIfFailed
import dev.staticsanches.kge.resource.toCleanerProvider

typealias GLVertexShaderWrapper = ResourceWrapper<GLShader>
typealias GLFragmentShaderWrapper = ResourceWrapper<GLShader>

fun GLVertexShaderWrapper(source: String): GLVertexShaderWrapper = VERTEX_SHADER(source)

fun GLFragmentShaderWrapper(source: String): GLFragmentShaderWrapper = FRAGMENT_SHADER(source)

private operator fun ShaderType.invoke(source: String): ResourceWrapper<GLShader> =
    ResourceWrapper(
        { toRepresentation(source) },
        { GL.createShader(type) },
        GL::deleteShader.toCleanerProvider(),
    ).applyClosingIfFailed {
        GL.shaderSource(resource, source)
        GL.compileShader(resource)
    }

private enum class ShaderType(
    val type: GLenum,
) {
    VERTEX_SHADER(GL.VERTEX_SHADER),
    FRAGMENT_SHADER(GL.FRAGMENT_SHADER),
    ;

    fun toRepresentation(source: String): String =
        name
            .lowercase()
            .split("_")
            .map { it.replaceFirstChar { it.uppercase() } }
            .joinToString(" ") + ":\n" + source.lineSequence().map { "    $it" }.joinToString("\n")
}
