package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.window.Window
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Decal
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.QuadBuffer.Companion.MAX_NUMBER_OF_VERTICES
import dev.staticsanches.kge.renderer.QuadInfo.QuadInfoKey
import dev.staticsanches.kge.resource.IdentifiedResource
import dev.staticsanches.kge.resource.KGECleanable
import dev.staticsanches.kge.resource.KGEInternalResource
import dev.staticsanches.kge.resource.KGELeakDetector
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.MemFreeAction
import dev.staticsanches.kge.resource.applyAndCloseIfFailed
import dev.staticsanches.kge.resource.closeIfFailed
import dev.staticsanches.kge.utils.invokeForAll
import org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR
import org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR
import org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE
import org.lwjgl.glfw.GLFW.GLFW_OPENGL_FORWARD_COMPAT
import org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE
import org.lwjgl.glfw.GLFW.GLFW_TRUE
import org.lwjgl.glfw.GLFW.glfwSwapBuffers
import org.lwjgl.glfw.GLFW.glfwWindowHint
import org.lwjgl.opengl.GL33.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL33.GL_BLEND
import org.lwjgl.opengl.GL33.GL_CLAMP
import org.lwjgl.opengl.GL33.GL_COLOR_BUFFER_BIT
import org.lwjgl.opengl.GL33.GL_DEPTH_BUFFER_BIT
import org.lwjgl.opengl.GL33.GL_DST_COLOR
import org.lwjgl.opengl.GL33.GL_FLOAT
import org.lwjgl.opengl.GL33.GL_FRAGMENT_SHADER
import org.lwjgl.opengl.GL33.GL_LINEAR
import org.lwjgl.opengl.GL33.GL_LINE_LOOP
import org.lwjgl.opengl.GL33.GL_NEAREST
import org.lwjgl.opengl.GL33.GL_NICEST
import org.lwjgl.opengl.GL33.GL_ONE
import org.lwjgl.opengl.GL33.GL_ONE_MINUS_SRC_ALPHA
import org.lwjgl.opengl.GL33.GL_PERSPECTIVE_CORRECTION_HINT
import org.lwjgl.opengl.GL33.GL_REPEAT
import org.lwjgl.opengl.GL33.GL_RGBA
import org.lwjgl.opengl.GL33.GL_SRC_ALPHA
import org.lwjgl.opengl.GL33.GL_STREAM_DRAW
import org.lwjgl.opengl.GL33.GL_TEXTURE_2D
import org.lwjgl.opengl.GL33.GL_TEXTURE_MAG_FILTER
import org.lwjgl.opengl.GL33.GL_TEXTURE_MIN_FILTER
import org.lwjgl.opengl.GL33.GL_TEXTURE_WRAP_S
import org.lwjgl.opengl.GL33.GL_TEXTURE_WRAP_T
import org.lwjgl.opengl.GL33.GL_TRIANGLES
import org.lwjgl.opengl.GL33.GL_TRIANGLE_FAN
import org.lwjgl.opengl.GL33.GL_TRIANGLE_STRIP
import org.lwjgl.opengl.GL33.GL_UNSIGNED_BYTE
import org.lwjgl.opengl.GL33.GL_VERTEX_SHADER
import org.lwjgl.opengl.GL33.GL_ZERO
import org.lwjgl.opengl.GL33.glAttachShader
import org.lwjgl.opengl.GL33.glBindBuffer
import org.lwjgl.opengl.GL33.glBindTexture
import org.lwjgl.opengl.GL33.glBindVertexArray
import org.lwjgl.opengl.GL33.glBlendFunc
import org.lwjgl.opengl.GL33.glBufferData
import org.lwjgl.opengl.GL33.glClear
import org.lwjgl.opengl.GL33.glClearColor
import org.lwjgl.opengl.GL33.glCompileShader
import org.lwjgl.opengl.GL33.glCreateProgram
import org.lwjgl.opengl.GL33.glCreateShader
import org.lwjgl.opengl.GL33.glDeleteBuffers
import org.lwjgl.opengl.GL33.glDeleteProgram
import org.lwjgl.opengl.GL33.glDeleteShader
import org.lwjgl.opengl.GL33.glDeleteTextures
import org.lwjgl.opengl.GL33.glDeleteVertexArrays
import org.lwjgl.opengl.GL33.glDrawArrays
import org.lwjgl.opengl.GL33.glEnable
import org.lwjgl.opengl.GL33.glEnableVertexAttribArray
import org.lwjgl.opengl.GL33.glGenBuffers
import org.lwjgl.opengl.GL33.glGenTextures
import org.lwjgl.opengl.GL33.glGenVertexArrays
import org.lwjgl.opengl.GL33.glHint
import org.lwjgl.opengl.GL33.glLinkProgram
import org.lwjgl.opengl.GL33.glReadPixels
import org.lwjgl.opengl.GL33.glShaderSource
import org.lwjgl.opengl.GL33.glTexImage2D
import org.lwjgl.opengl.GL33.glTexParameteri
import org.lwjgl.opengl.GL33.glTexSubImage2D
import org.lwjgl.opengl.GL33.glUseProgram
import org.lwjgl.opengl.GL33.glVertexAttribPointer
import org.lwjgl.opengl.GL33.glViewport
import java.nio.ByteBuffer

internal data object GL33Renderer : Renderer {
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

    context(Window)
    private val quadInfo: QuadInfo
        get() = getExtraInfo(QuadInfoKey) ?: throw RuntimeException("Quad Info not available for ${this@Window}")

    override fun beforeWindowCreation() {
        println("Requesting OpenGL 3.3")
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
    }

    context(Window)
    override fun afterWindowCreation() {
        putExtraInfo(QuadInfoKey, QuadInfo())
        glEnable(GL_BLEND)
        glEnable(GL_TEXTURE_2D)
        glHint(GL_PERSPECTIVE_CORRECTION_HINT, GL_NICEST)
        prepareDrawing()
    }

    context(Window)
    override fun prepareDrawing() {
        glEnable(GL_BLEND)
        decalMode = Decal.Mode.NORMAL

        val quadInfo = quadInfo
        glUseProgram(quadInfo.program.id)
        glBindVertexArray(quadInfo.quadBuffer.arrayID.id)
    }

    context(Window)
    override fun createTexture(
        filtered: Boolean,
        clamp: Boolean,
    ): Int {
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

        return id
    }

    context(Window)
    override fun deleteTexture(id: Int) = glDeleteTextures(id)

    context(Window)
    override fun initializeTexture(
        id: Int,
        sprite: Sprite,
    ) {
        glBindTexture(GL_TEXTURE_2D, id)
        glTexImage2D(
            GL_TEXTURE_2D,
            0,
            GL_RGBA,
            sprite.width,
            sprite.height,
            0,
            GL_RGBA,
            GL_UNSIGNED_BYTE,
            sprite.pixmap.internalBuffer.clear(),
        )
    }

    context(Window)
    override fun updateTexture(
        id: Int,
        sprite: Sprite,
    ) {
        glBindTexture(GL_TEXTURE_2D, id)
        glTexSubImage2D(
            GL_TEXTURE_2D,
            0,
            0,
            0,
            sprite.width,
            sprite.height,
            GL_RGBA,
            GL_UNSIGNED_BYTE,
            sprite.pixmap.internalBuffer.clear(),
        )
    }

    context(Window)
    override fun readTexture(
        id: Int,
        sprite: Sprite,
    ) {
        glBindTexture(GL_TEXTURE_2D, id)
        glReadPixels(
            0,
            0,
            sprite.width,
            sprite.height,
            GL_RGBA,
            GL_UNSIGNED_BYTE,
            sprite.pixmap.internalBuffer.clear(),
        )
    }

    context(Window)
    override fun applyTexture(id: Int) = glBindTexture(GL_TEXTURE_2D, id)

    context(Window)
    override fun clearBuffer(
        pixel: Pixel,
        depth: Boolean,
    ) {
        glClearColor(pixel.r / 255f, pixel.g / 255f, pixel.b / 255f, pixel.a / 255f)
        glClear(GL_COLOR_BUFFER_BIT)
        if (depth) {
            glClear(GL_DEPTH_BUFFER_BIT)
        }
    }

    context(Window)
    override fun updateViewport(
        position: Int2D,
        size: Int2D,
    ) = glViewport(position.x, position.y, size.x, size.y)

    context(Window)
    override fun displayFrame() = glfwSwapBuffers(glfwHandle)

    context(Window)
    override fun drawDecals(decals: List<DecalInstance>) {
        val quadInfo = quadInfo
        var index = 0
        while (index < decals.size) {
            val current = decals[index++]
            var availableVertices = MAX_NUMBER_OF_VERTICES
            val buffer =
                quadInfo.quadBuffer.bufferResource.buffer
                    .clear()

            fun collect(decal: DecalInstance) =
                with(decal.verticesInfo) {
                    availableVertices -= numberOfVertices
                    putAllXYWUVTint(buffer)
                }

            collect(current)

            while (index < decals.size) {
                val peek = decals[index++]
                if (current.mode != peek.mode ||
                    current.structure != peek.structure ||
                    current.decal != peek.decal ||
                    availableVertices < peek.verticesInfo.numberOfVertices
                ) {
                    index--
                    break
                }
                collect(peek)
            }

            decalMode = current.mode
            glBindTexture(GL_TEXTURE_2D, (current.decal ?: quadInfo.emptyDecal).id)
            glBindBuffer(GL_ARRAY_BUFFER, quadInfo.quadBuffer.bufferID.id)
            glBufferData(
                GL_ARRAY_BUFFER,
                buffer.flip(),
                GL_STREAM_DRAW,
            )
            glDrawArrays(
                if (current.mode == Decal.Mode.WIREFRAME) {
                    GL_LINE_LOOP
                } else {
                    when (current.structure) {
                        Decal.Structure.LINE -> GL_LINE_LOOP
                        Decal.Structure.FAN -> GL_TRIANGLE_FAN
                        Decal.Structure.STRIP -> GL_TRIANGLE_STRIP
                        Decal.Structure.LIST -> GL_TRIANGLES
                    }
                },
                0,
                MAX_NUMBER_OF_VERTICES - availableVertices,
            )
        }
    }

    context(Window)
    override fun drawLayerQuad(
        offset: Float2D,
        scale: Float2D,
        tint: Pixel,
    ) {
        val quadInfo = quadInfo

        val buffer =
            quadInfo.quadBuffer.bufferResource.buffer
                .clear()
                // Vertex 0
                .putFloat(-1f)
                .putFloat(-1f)
                .putFloat(1f) // position
                .putFloat(0f * scale.x + offset.x)
                .putFloat(1f * scale.y + offset.y) // texture
                .putInt(tint.nativeRGBA)
                // Vertex 1
                .putFloat(1f)
                .putFloat(-1f)
                .putFloat(1f) // position
                .putFloat(1f * scale.x + offset.x)
                .putFloat(1f * scale.y + offset.y) // texture
                .putInt(tint.nativeRGBA)
                // Vertex 2
                .putFloat(-1f)
                .putFloat(1f)
                .putFloat(1f) // position
                .putFloat(0f * scale.x + offset.x)
                .putFloat(0f * scale.y + offset.y) // texture
                .putInt(tint.nativeRGBA)
                // Vertex 3
                .putFloat(1f)
                .putFloat(1f)
                .putFloat(1f) // position
                .putFloat(1f * scale.x + offset.x)
                .putFloat(0f * scale.y + offset.y) // texture
                .putInt(tint.nativeRGBA)

        glBindBuffer(GL_ARRAY_BUFFER, quadInfo.quadBuffer.bufferID.id)
        glBufferData(GL_ARRAY_BUFFER, buffer.flip(), GL_STREAM_DRAW)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
    }

    override val servicePriority: Int
        get() = Int.MIN_VALUE

    private data object DecalModeKey : Window.ExtraInfoKey<Decal.Mode>
}

private class QuadInfo private constructor(
    val program: Program,
    val quadBuffer: QuadBuffer,
    val emptyDecal: Decal,
) : KGEInternalResource {
    override fun close() = invokeForAll(program, quadBuffer, emptyDecal, emptyDecal.sprite) { it.close() }

    data object QuadInfoKey : Window.ExtraInfoKey<QuadInfo>

    companion object {
        context(Window)
        operator fun invoke(): QuadInfo =
            Program(
                """
                #version 330 core
                layout(location = 0) in vec3 aPos;
                layout(location = 1) in vec2 aTex;
                layout(location = 2) in vec4 aCol;
                out vec2 oTex;
                out vec4 oCol;
                void main(){
                	float p = 1.0 / aPos.z;
                	gl_Position = p * vec4(aPos.x, aPos.y, 0.0, 1.0);
                	oTex = p * aTex;
                	oCol = aCol;
                }
                """.trimIndent(),
                """
                #version 330 core
                out vec4 pixel;
                in vec2 oTex;
                in vec4 oCol;
                uniform sampler2D sprTex;
                void main(){
                	pixel = texture(sprTex, oTex) * oCol;
                }
                """.trimIndent(),
            ).closeIfFailed { program ->
                QuadBuffer().closeIfFailed { quadBuffer ->
                    Sprite.create(1, 1, defaultPixel = Colors.WHITE).closeIfFailed { sprite ->
                        Decal(sprite).closeIfFailed { decal ->
                            QuadInfo(program, quadBuffer, decal).applyAndCloseIfFailed {
                                bindResource(it)
                            }
                        }
                    }
                }
            }
    }
}

private class Program private constructor(
    private val programID: IdentifiedResource<Int>,
    private val vertexShader: Shader,
    private val fragmentShader: Shader,
) : KGEInternalResource {
    val id: Int by programID::id

    init {
        glAttachShader(id, vertexShader.id)
        glAttachShader(id, fragmentShader.id)
        glLinkProgram(id)
    }

    @KGESensitiveAPI
    override fun close() = invokeForAll(programID, vertexShader, fragmentShader) { it.close() }

    companion object {
        operator fun invoke(
            vertexShader: String,
            fragmentShader: String,
        ): Program =
            Shader(ShaderType.VERTEX, vertexShader).closeIfFailed { vs ->
                Shader(ShaderType.FRAGMENT, fragmentShader).closeIfFailed { fs ->
                    IdentifiedResource("Program", ::glCreateProgram, ::glDeleteProgram).closeIfFailed { id ->
                        Program(id, vs, fs)
                    }
                }
            }
    }
}

private typealias Shader = IdentifiedResource<Int>

private fun Shader(
    type: ShaderType,
    source: String,
): Shader =
    IdentifiedResource("$type", { glCreateShader(type.glType) }, ::glDeleteShader).applyAndCloseIfFailed {
        glShaderSource(it.id, source)
        glCompileShader(it.id)
    }

private enum class ShaderType(
    val glType: Int,
) {
    VERTEX(GL_VERTEX_SHADER) {
        override fun toString(): String = "Vertex Shader"
    },
    FRAGMENT(GL_FRAGMENT_SHADER) {
        override fun toString(): String = "Fragment Shader"
    },
}

private class QuadBuffer private constructor(
    val bufferResource: ByteBufferResource,
    val bufferID: IdentifiedResource<Int>,
    val arrayID: IdentifiedResource<Int>,
) : KGEResource {
    override fun close() = invokeForAll(bufferResource, bufferID, arrayID) { it.close() }

    companion object {
        const val VERTEX_BYTES_COUNT = 5 * Float.SIZE_BYTES + 1 * Int.SIZE_BYTES
        const val MAX_NUMBER_OF_VERTICES = 1_048_576 / VERTEX_BYTES_COUNT

        operator fun invoke(): QuadBuffer =
            ByteBufferResource(VERTEX_BYTES_COUNT * MAX_NUMBER_OF_VERTICES).closeIfFailed { bufferResource ->
                IdentifiedResource("Quad Buffer", ::glGenBuffers, ::glDeleteBuffers).closeIfFailed { bufferID ->
                    IdentifiedResource(
                        "Quad Array",
                        ::glGenVertexArrays,
                        ::glDeleteVertexArrays,
                    ).closeIfFailed { arrayID ->
                        glBindVertexArray(arrayID.id)
                        glBindBuffer(GL_ARRAY_BUFFER, bufferID.id)

                        glBufferData(GL_ARRAY_BUFFER, bufferResource.buffer, GL_STREAM_DRAW)

                        glVertexAttribPointer(0, 3, GL_FLOAT, false, VERTEX_BYTES_COUNT, 0)
                        glEnableVertexAttribArray(0)
                        glVertexAttribPointer(1, 2, GL_FLOAT, false, VERTEX_BYTES_COUNT, 3 * 4)
                        glEnableVertexAttribArray(1)
                        glVertexAttribPointer(2, 4, GL_UNSIGNED_BYTE, true, VERTEX_BYTES_COUNT, 5 * 4)
                        glEnableVertexAttribArray(2)

                        glBindBuffer(GL_ARRAY_BUFFER, 0)
                        glBindVertexArray(0)
                        QuadBuffer(bufferResource, bufferID, arrayID)
                    }
                }
            }
    }
}

private class ByteBufferResource(
    size: Int,
) : KGEInternalResource {
    val buffer: ByteBuffer
    private val cleanable: KGECleanable

    init {
        val action = MemFreeAction(size)
        buffer = action.buffer
        cleanable = KGELeakDetector.register(this, "Quad Byte Buffer", action)
    }

    @KGESensitiveAPI
    override fun close() = cleanable.clean()
}
