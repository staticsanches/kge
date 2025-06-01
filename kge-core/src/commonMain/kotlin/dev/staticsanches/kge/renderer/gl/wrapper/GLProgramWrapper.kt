package dev.staticsanches.kge.renderer.gl.wrapper

import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.GLProgram
import dev.staticsanches.kge.renderer.gl.GLShader
import dev.staticsanches.kge.renderer.gl.GLUniformLocation
import dev.staticsanches.kge.renderer.gl.GLint
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.applyClosingIfFailed
import dev.staticsanches.kge.resource.letClosingIfFailed
import dev.staticsanches.kge.resource.toCleanerProvider
import dev.staticsanches.kge.utils.invokeForAll

class GLProgramWrapper(
    private val delegate: ResourceWrapper<GLProgram>,
) : ResourceWrapper<GLProgram> by delegate {
    private val uniformLocationHolder = LocationHolder(GL::getUniformLocation)
    private val attribLocationHolder = LocationHolder(GL::getAttribLocation)

    fun getUniformLocation(name: String): GLUniformLocation = uniformLocationHolder[name]

    fun getAttribLocation(name: String): GLint = attribLocationHolder[name]

    override fun close() = invokeForAll(delegate, uniformLocationHolder, attribLocationHolder) { it.close() }

    override fun toString(): String = delegate.toString()

    private inner class LocationHolder<L>(
        val getter: (GLProgram, String) -> L?,
    ) : KGEResource {
        val delegate = HashMap<String, L>()

        operator fun get(name: String): L =
            delegate.getOrPut(name) { checkNotNull(getter(resource, name)) { "Invalid name: $name" } }

        override fun close() = delegate.clear()
    }

    companion object {
        operator fun invoke(
            vertexShader: String,
            fragmentShader: String,
            name: String? = null,
            attribLocations: Map<String, GLint>? = null,
        ): GLProgramWrapper =
            GLVertexShaderWrapper(vertexShader).use { (vertexShader) ->
                GLFragmentShaderWrapper(fragmentShader).use { (fragmentShader) ->
                    GLProgramWrapper(vertexShader, fragmentShader, name, attribLocations)
                }
            }

        operator fun invoke(
            vertexShader: GLShader,
            fragmentShader: GLShader,
            name: String? = null,
            attribLocations: Map<String, GLint>? = null,
        ): GLProgramWrapper =
            ResourceWrapper(
                { "Program ${name ?: it}" },
                GL::createProgram,
                GL::deleteProgram.toCleanerProvider(),
            ).letClosingIfFailed {
                GLProgramWrapper(it).applyClosingIfFailed {
                    val program = resource
                    GL.attachShader(program, vertexShader)
                    GL.attachShader(program, fragmentShader)

                    attribLocations?.forEach { (attrib, location) ->
                        GL.bindAttribLocation(program, location, attrib)
                        attribLocationHolder.delegate[attrib] = location
                    }

                    GL.linkProgram(program)
                }
            }
    }
}
