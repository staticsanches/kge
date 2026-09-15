package dev.staticsanches.kge.engine

import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.gl.GLProgram
import dev.staticsanches.kge.renderer.gl.GLbitfield
import dev.staticsanches.kge.renderer.gl.GLint
import dev.staticsanches.kge.renderer.gl.GLsizei
import dev.staticsanches.kge.renderer.gl.service.GLService
import dev.staticsanches.kge.time.FakeTimeService
import dev.staticsanches.kge.time.TimeService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class EngineFrameTest :
    FunSpec({
        test("the render step runs updateViewport, clear, prepare, present, awaitNextFrame in order") {
            val order = mutableListOf<String>()
            val gl = installGl()
            GLService.override(OrderingGLService(gl, order))
            installDriver(OrderingDriver(RecordingDriver(scriptedFramebufferSize = Int2D(1000, 500)), order))
            TimeService.override(FakeTimeService(16.milliseconds))
            var frameOrder: List<String> = emptyList()
            val engine =
                ScriptedEngine(
                    onUpdate = { _, _ ->
                        order.clear()
                        false
                    },
                    onDestroy = {
                        frameOrder = order.toList()
                        true
                    },
                )

            engine.start()

            frameOrder shouldBe
                listOf(
                    "updateViewport",
                    "clearBuffer",
                    "prepareDrawing",
                    "present",
                    "awaitNextFrame",
                )
        }

        test("the viewport re-fits when the framebuffer size changes mid-run") {
            val driver = RecordingDriver(scriptedFramebufferSize = Int2D(640, 480))
            installDriver(driver)
            val gl = installGl()
            var frames = 0
            val engine =
                ScriptedEngine(
                    onUpdate = { _, _ ->
                        if (++frames == 1) driver.scriptedFramebufferSize = Int2D(400, 400)
                        frames < 2
                    },
                )

            engine.start()

            // The 320x240 art re-fits into the new 400x400 framebuffer.
            gl.calls.last { it.name == "viewport" }.arguments shouldBe listOf(0, 50, 400, 300)
        }

        test("updateViewport receives the letterbox fit of the scripted framebuffer") {
            val gl = installGl()
            installDriver(RecordingDriver(scriptedFramebufferSize = Int2D(1000, 500)))
            val engine = ScriptedEngine(onUpdate = { _, _ -> false })

            engine.start()

            gl.calls.last { it.name == "viewport" }.arguments shouldBe listOf(166, 0, 667, 500)
        }

        test("frame publishes the accumulator delta and the running frame count") {
            installDriver(RecordingDriver(scriptedFramebufferSize = Int2D(320, 240)))
            installGl()
            TimeService.override(FakeTimeService(16.milliseconds))
            var frames = 0
            val engine = ScriptedEngine(onUpdate = { _, _ -> ++frames < 3 })

            engine.start()

            engine.frame.elapsed shouldBe 16.milliseconds
            engine.frame.frameCount shouldBe 3
            engine.frame.framebufferSize shouldBe Int2D(320, 240)
        }

        test("fps stays zero until a full one-second window completes") {
            installDriver(RecordingDriver(scriptedFramebufferSize = Int2D(320, 240)))
            installGl()
            TimeService.override(FakeTimeService(100.milliseconds))
            var frames = 0
            val engine = ScriptedEngine(onUpdate = { _, _ -> ++frames < 5 })

            engine.start()

            engine.frame.fps shouldBe 0
            engine.frame.frameCount shouldBe 5
        }

        test("fps publishes the frame count of the completed one-second window") {
            installDriver(RecordingDriver(scriptedFramebufferSize = Int2D(320, 240)))
            installGl()
            TimeService.override(FakeTimeService(100.milliseconds))
            var frames = 0
            // The first frame's delta is zero, so the one-second window closes on
            // the eleventh frame.
            val engine = ScriptedEngine(onUpdate = { _, _ -> ++frames < 11 })

            engine.start()

            engine.frame.fps shouldBe 11
            engine.frame.frameCount shouldBe 11
        }

        test("the loop renders every requested frame and then stops") {
            val driver = RecordingDriver(scriptedFramebufferSize = Int2D(320, 240))
            installDriver(driver)
            installGl()
            var frames = 0
            val engine = ScriptedEngine(onUpdate = { _, _ -> ++frames < 4 })

            engine.start()

            frames shouldBe 4
            driver.calls.count { it == "pollEvents" } shouldBe 4
            driver.calls.count { it == "present" } shouldBe 4
            driver.calls.count { it == "awaitNextFrame" } shouldBe 4
        }

        test("frame is a zero snapshot before start") {
            val engine = ScriptedEngine()

            engine.frame shouldBe FrameInfo(Duration.ZERO, 0, 0, Int2D(0, 0))
        }
    })

/** A [Driver] that appends the render lifecycle to [order] before delegating. */
private class OrderingDriver(
    private val delegate: Driver,
    private val order: MutableList<String>,
) : Driver by delegate {
    override fun makeCurrent() {
        order += "makeCurrent"
        delegate.makeCurrent()
    }

    override fun present() {
        order += "present"
        delegate.present()
    }

    override suspend fun awaitNextFrame() {
        order += "awaitNextFrame"
        delegate.awaitNextFrame()
    }
}

/** A [GLService] that appends the render commands to [order], sharing it with the driver double. */
private class OrderingGLService(
    private val delegate: GLService,
    private val order: MutableList<String>,
) : GLService by delegate {
    override fun viewport(
        x: GLint,
        y: GLint,
        width: GLsizei,
        height: GLsizei,
    ) {
        order += "updateViewport"
        delegate.viewport(x, y, width, height)
    }

    override fun clear(mask: GLbitfield) {
        order += "clearBuffer"
        delegate.clear(mask)
    }

    override fun useProgram(program: GLProgram?) {
        order += "prepareDrawing"
        delegate.useProgram(program)
    }
}
