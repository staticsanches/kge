// The handles are `web.gl` interop types on the web targets; transporting them
// through the common recording assertions is the intended, safe use.
@file:Suppress("OPT_IN_USAGE")

package dev.staticsanches.kge.renderer.internal

import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.GLProgram
import dev.staticsanches.kge.renderer.gl.RecordingGLService
import dev.staticsanches.kge.renderer.gl.service.GLService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The internal GL resource wrappers: creation pins the raw command sequence,
 * `close` deletes exactly once, use after close fails fast, and a construction
 * failure frees the already-allocated objects.
 */
class GLResourcesTest :
    FunSpec({
        test("a shader resource compiles on creation and deletes exactly once") {
            val recorder = RecordingGLService()
            GLService.override(recorder)

            val shader = createShaderResource(GL.VERTEX_SHADER, "void main() {}", "test")
            val handle = shader.resource
            recorder.calls.map { it.name } shouldBe
                listOf("createShader", "shaderSource", "compileShader")
            recorder.calls[0].arguments shouldBe listOf(GL.VERTEX_SHADER)
            recorder.calls[1].arguments shouldBe listOf(handle, "void main() {}")

            shader.close()
            shader.close()

            recorder.calls.count { it.name == "deleteShader" } shouldBe 1
            shouldThrow<IllegalStateException> { shader.resource }
        }

        test("a program resource attaches and links on creation and deletes exactly once") {
            val recorder = RecordingGLService()
            GLService.override(recorder)

            val program = createProgramResource("vertex", "fragment", "test")
            val handle = program.resource
            val fragment = recorder.lastCreatedShader
            recorder.calls.map { it.name } shouldBe
                listOf(
                    "createShader",
                    "shaderSource",
                    "compileShader",
                    "createShader",
                    "shaderSource",
                    "compileShader",
                    "createProgram",
                    "attachShader",
                    "attachShader",
                    "linkProgram",
                    "deleteShader",
                    "deleteShader",
                )
            recorder.calls.filter { it.name == "attachShader" }.map { it.arguments[0] } shouldBe
                listOf(handle, handle)
            recorder.calls.last { it.name == "attachShader" }.arguments[1] shouldBe fragment
            recorder.calls.last { it.name == "linkProgram" }.arguments shouldBe listOf(handle)

            program.close()
            program.close()

            recorder.calls.count { it.name == "deleteProgram" } shouldBe 1
            shouldThrow<IllegalStateException> { program.resource }
        }

        test("buffer and VAO resources delete exactly once") {
            val recorder = RecordingGLService()
            GLService.override(recorder)

            val buffer = createBufferResource("test")
            val vao = createVertexArrayResource("test")
            recorder.calls.count { it.name == "createBuffer" } shouldBe 1
            recorder.calls.count { it.name == "createVertexArray" } shouldBe 1

            buffer.close()
            buffer.close()
            vao.close()
            vao.close()

            recorder.calls.count { it.name == "deleteBuffer" } shouldBe 1
            recorder.calls.count { it.name == "deleteVertexArray" } shouldBe 1
            shouldThrow<IllegalStateException> { buffer.resource }
            shouldThrow<IllegalStateException> { vao.resource }
        }

        test("a failed link deletes the program and the compiled shaders") {
            val recorder = RecordingGLService()
            val failure = IllegalStateException("link failed")
            GLService.override(
                object : GLService by recorder {
                    override fun linkProgram(program: GLProgram): Unit = throw failure
                },
            )

            shouldThrow<IllegalStateException> {
                createProgramResource("vertex", "fragment", "failing")
            } shouldBe failure

            recorder.calls.count { it.name == "deleteProgram" } shouldBe 1
            recorder.calls.count { it.name == "deleteShader" } shouldBe 2
        }
    })
