package dev.staticsanches.kge.engine

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.engine.layer.Layer
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.service.DrawPolygonDecalService
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.GLProgram
import dev.staticsanches.kge.renderer.gl.GLTexture
import dev.staticsanches.kge.renderer.gl.GLenum
import dev.staticsanches.kge.renderer.gl.GLint
import dev.staticsanches.kge.renderer.gl.GLsizei
import dev.staticsanches.kge.renderer.gl.service.GLService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

/**
 * The engine render step: the olc layer loop over the recording GL backend. Each
 * test runs one frame (clearing the recorded calls first) and reads the calls
 * the frame produced.
 */
class EngineRenderStepTest :
    FunSpec({
        test("the render step runs viewport, clear, prepare, layer work, present, awaitNextFrame in order") {
            val order = mutableListOf<String>()
            val gl = installGl()
            GLService.override(OrderedGLService(gl, order))
            installDriver(OrderedDriver(RecordingDriver(scriptedFramebufferSize = Int2D(320, 240)), order))
            val engine =
                ScriptedEngine(
                    onUpdate = { _, _ ->
                        order.clear()
                        false
                    },
                )

            engine.start()

            order shouldBe
                listOf(
                    "viewport",
                    "clear",
                    "useProgram",
                    "bindTexture",
                    "bindTexture",
                    "texImage2D",
                    "drawArrays",
                    "present",
                    "awaitNextFrame",
                )
        }

        test("the framebuffer clear uses the configured clear color") {
            val gl = installGl()
            installDriver(RecordingDriver(scriptedFramebufferSize = Int2D(320, 240)))
            val engine =
                ScriptedEngine(
                    config = WindowConfig(screenWidth = 320, screenHeight = 240, clearColor = Colors.RED),
                    onUpdate = { _, _ -> false },
                )

            engine.start()

            gl.calls.single { it.name == "clearColor" }.arguments shouldBe listOf(1f, 0f, 0f, 1f)
        }

        test("decalMode resets to NORMAL each frame before prepareDrawing") {
            val gl = installGl()
            lateinit var engine: ScriptedEngine
            val probe = ProbingGLService(gl) { engine.decalMode }
            GLService.override(probe)
            installDriver(RecordingDriver(scriptedFramebufferSize = Int2D(320, 240)))
            val observedAtUpdate = mutableListOf<Decal.Mode>()
            var frames = 0
            engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        observedAtUpdate += e.decalMode
                        if (++frames == 1) e.decalMode = Decal.Mode.ADDITIVE
                        frames < 2
                    },
                )

            engine.start()

            observedAtUpdate shouldBe listOf(Decal.Mode.NORMAL, Decal.Mode.NORMAL)
            probe.observed shouldBe listOf(Decal.Mode.NORMAL, Decal.Mode.NORMAL)
        }

        test("layer 0 is forced shown and uploaded even when hidden and clean") {
            val gl = installGl()
            installDriver(RecordingDriver(scriptedFramebufferSize = Int2D(320, 240)))
            var shown = false
            var updated = true
            val engine =
                ScriptedEngine(
                    onUpdate = { _, _ ->
                        gl.clear()
                        false
                    },
                    onDestroy = { e ->
                        shown = e.layers[0].show
                        updated = e.layers[0].update
                        true
                    },
                )

            engine.start()

            gl.calls.count { it.name == "texImage2D" } shouldBe 1
            gl.calls.count { it.name == "drawArrays" } shouldBe 1
            shown shouldBe true
            updated shouldBe false
        }

        test("a hidden layer is neither uploaded nor drawn") {
            val gl = installGl()
            installDriver(RecordingDriver(scriptedFramebufferSize = Int2D(320, 240)))
            var shown = true
            var updated = false
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        e.layers.createLayer()
                        e.layers[1].update = true
                        gl.clear()
                        false
                    },
                    onDestroy = { e ->
                        shown = e.layers[1].show
                        updated = e.layers[1].update
                        true
                    },
                )

            engine.start()

            gl.calls.count { it.name == "texImage2D" } shouldBe 1
            gl.calls.count { it.name == "drawArrays" } shouldBe 1
            shown shouldBe false
            updated shouldBe true
        }

        test("a dirty shown layer uploads then draws its quad and clears update") {
            val gl = installGl()
            installDriver(RecordingDriver(scriptedFramebufferSize = Int2D(320, 240)))
            var updated = true
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        e.layers.createLayer()
                        e.layers[1].show = true
                        e.layers[1].update = true
                        gl.clear()
                        false
                    },
                    onDestroy = { e ->
                        updated = e.layers[1].update
                        true
                    },
                )

            engine.start()

            gl.calls.count { it.name == "texImage2D" } shouldBe 2
            gl.calls.count { it.name == "drawArrays" } shouldBe 2
            updated shouldBe false
        }

        test("a clean shown layer draws its quad without uploading") {
            val gl = installGl()
            installDriver(RecordingDriver(scriptedFramebufferSize = Int2D(320, 240)))
            var updated = true
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        e.layers.createLayer()
                        e.layers[1].show = true
                        gl.clear()
                        false
                    },
                    onDestroy = { e ->
                        updated = e.layers[1].update
                        true
                    },
                )

            engine.start()

            gl.calls.count { it.name == "texImage2D" } shouldBe 1
            gl.calls.count { it.name == "drawArrays" } shouldBe 2
            updated shouldBe false
        }

        test("suspendTextureTransfer skips the upload but still draws the quad") {
            val gl = installGl()
            installDriver(RecordingDriver(scriptedFramebufferSize = Int2D(320, 240)))
            var updated = false
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        e.suspendTextureTransfer = true
                        gl.clear()
                        false
                    },
                    onDestroy = { e ->
                        updated = e.layers[0].update
                        true
                    },
                )

            engine.start()

            gl.calls.count { it.name == "texImage2D" } shouldBe 0
            gl.calls.count { it.name == "drawArrays" } shouldBe 1
            updated shouldBe true
        }

        test("a queued decal instance is drawn after the layer quad and the queue is emptied") {
            val gl = installGl()
            installDriver(RecordingDriver(scriptedFramebufferSize = Int2D(320, 240)))
            var queueEmptied = false
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        val layer = e.layers[0]
                        layer.decalInstances +=
                            DrawPolygonDecalService.drawPolygonDecal(
                                decal = layer.decal,
                                pos = listOf(Float2D(0f, 0f), Float2D(1f, 0f), Float2D(0f, 1f)),
                                uv = listOf(Float2D(0f, 0f), Float2D(1f, 0f), Float2D(0f, 1f)),
                                tint = List(3) { Colors.WHITE },
                                mode = Decal.Mode.NORMAL,
                                structure = Decal.Structure.FAN,
                                viewport = e.window.screenSize,
                            )
                        gl.clear()
                        false
                    },
                    onDestroy = { e ->
                        queueEmptied = e.layers[0].decalInstances.isEmpty()
                        true
                    },
                )

            engine.start()

            val draws = gl.calls.filter { it.name == "drawArrays" }
            draws.size shouldBe 2
            draws[0].arguments shouldBe listOf(GL.TRIANGLE_STRIP, 0, 4)
            draws[1].arguments shouldBe listOf(GL.TRIANGLE_FAN, 0, 3)
            queueEmptied shouldBe true
        }

        test("customRender replaces the upload, quad and decals for its layer") {
            val gl = installGl()
            installDriver(RecordingDriver(scriptedFramebufferSize = Int2D(320, 240)))
            var layerRef: Layer? = null
            var invoked: Layer? = null
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        layerRef = e.layers[0]
                        e.layers[0].customRender = { invoked = it }
                        gl.clear()
                        false
                    },
                )

            engine.start()

            invoked shouldBeSameInstanceAs layerRef
            gl.calls.count { it.name == "texImage2D" } shouldBe 0
            gl.calls.count { it.name == "drawArrays" } shouldBe 0
        }

        test("a customRender layer leaves its queued decal instances unflushed") {
            val gl = installGl()
            installDriver(RecordingDriver(scriptedFramebufferSize = Int2D(320, 240)))
            var queued = 0
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        val layer = e.layers[0]
                        layer.decalInstances +=
                            DrawPolygonDecalService.drawPolygonDecal(
                                decal = layer.decal,
                                pos = listOf(Float2D(0f, 0f), Float2D(1f, 0f), Float2D(0f, 1f)),
                                uv = listOf(Float2D(0f, 0f), Float2D(1f, 0f), Float2D(0f, 1f)),
                                tint = List(3) { Colors.WHITE },
                                mode = Decal.Mode.NORMAL,
                                structure = Decal.Structure.FAN,
                                viewport = e.window.screenSize,
                            )
                        layer.customRender = {}
                        gl.clear()
                        false
                    },
                    onDestroy = { e ->
                        queued = e.layers[0].decalInstances.size
                        true
                    },
                )

            engine.start()

            queued shouldBe 1
            gl.calls.count { it.name == "drawArrays" } shouldBe 0
        }

        test("multiple layers composite in reverse order") {
            val gl = installGl()
            installDriver(RecordingDriver(scriptedFramebufferSize = Int2D(320, 240)))
            lateinit var buffers: List<ByteBuffer>
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        e.layers.createLayer()
                        e.layers.createLayer()
                        for (index in 0..2) {
                            e.layers[index].show = true
                            e.layers[index].update = true
                        }
                        buffers = (0..2).map { e.layers[it].target.buffer }
                        gl.clear()
                        false
                    },
                )

            engine.start()

            val uploads = gl.calls.filter { it.name == "texImage2D" }
            uploads.size shouldBe 3
            uploads[0].arguments[8] shouldBeSameInstanceAs buffers[2]
            uploads[1].arguments[8] shouldBeSameInstanceAs buffers[1]
            uploads[2].arguments[8] shouldBeSameInstanceAs buffers[0]
        }
    })

/** A [GLService] that appends the render milestones to [order], sharing it with the driver double. */
private class OrderedGLService(
    private val delegate: GLService,
    private val order: MutableList<String>,
) : GLService by delegate {
    override fun viewport(
        x: GLint,
        y: GLint,
        width: GLsizei,
        height: GLsizei,
    ) {
        order += "viewport"
        delegate.viewport(x, y, width, height)
    }

    override fun clear(mask: GLenum) {
        order += "clear"
        delegate.clear(mask)
    }

    override fun useProgram(program: GLProgram?) {
        order += "useProgram"
        delegate.useProgram(program)
    }

    override fun bindTexture(
        target: GLenum,
        texture: GLTexture?,
    ) {
        order += "bindTexture"
        delegate.bindTexture(target, texture)
    }

    override fun texImage2D(
        target: GLenum,
        level: GLint,
        internalFormat: GLenum,
        width: GLsizei,
        height: GLsizei,
        border: GLint,
        format: GLenum,
        type: GLenum,
        srcData: ByteBuffer?,
    ) {
        order += "texImage2D"
        delegate.texImage2D(target, level, internalFormat, width, height, border, format, type, srcData)
    }

    override fun drawArrays(
        mode: GLenum,
        first: GLint,
        count: GLsizei,
    ) {
        order += "drawArrays"
        delegate.drawArrays(mode, first, count)
    }
}

/** A [Driver] that appends the present milestones to [order], sharing it with the GL double. */
private class OrderedDriver(
    private val delegate: Driver,
    private val order: MutableList<String>,
) : Driver by delegate {
    override fun present() {
        order += "present"
        delegate.present()
    }

    override suspend fun awaitNextFrame() {
        order += "awaitNextFrame"
        delegate.awaitNextFrame()
    }
}

/** A [GLService] that records the draw mode [observe] when `prepareDrawing` binds the program. */
private class ProbingGLService(
    private val delegate: GLService,
    private val observe: () -> Decal.Mode,
) : GLService by delegate {
    override fun useProgram(program: GLProgram?) {
        observed += observe()
        delegate.useProgram(program)
    }

    val observed = mutableListOf<Decal.Mode>()
}
