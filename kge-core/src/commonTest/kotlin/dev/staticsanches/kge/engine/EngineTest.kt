package dev.staticsanches.kge.engine

import dev.staticsanches.kge.renderer.Renderer
import dev.staticsanches.kge.renderer.device.GpuDevice
import dev.staticsanches.kge.renderer.internal.rendererDefault
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.time.FakeTimeService
import dev.staticsanches.kge.time.TimeService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldEndWith
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

class EngineTest :
    FunSpec({
        test("startup creates the driver, makes the context current, builds resources, then creates the user") {
            val driver = RecordingDriver()
            val service = installDriver(driver)
            val gl = installGl()
            TimeService.override(FakeTimeService(16.milliseconds))
            var contextCurrentBeforeResources = false
            Renderer.override(
                object : Renderer by rendererDefault {
                    override fun createResources(
                        device: GpuDevice,
                        scope: ResourceScope,
                    ) {
                        contextCurrentBeforeResources = driver.calls.contains("makeCurrent")
                        rendererDefault.createResources(device, scope)
                    }
                },
            )
            var resourcesBuiltBeforeCreate = false
            val engine =
                ScriptedEngine(
                    onCreate = {
                        resourcesBuiltBeforeCreate = gl.calls.any { it.name == "createProgram" }
                        true
                    },
                    onUpdate = { _, _ -> false },
                )

            engine.start()

            service.createCount shouldBe 1
            driver.calls.first() shouldBe "makeCurrent"
            contextCurrentBeforeResources shouldBe true
            resourcesBuiltBeforeCreate shouldBe true
        }

        test("teardown closes the resource scope before the driver") {
            val driver = RecordingDriver()
            installDriver(driver)
            installGl()
            val engine = ScriptedEngine(onUpdate = { _, _ -> false })

            engine.start()

            // The built-in quad makes the context current while the scope
            // releases it, proving the scope closes before the driver.
            driver.calls shouldEndWith listOf("makeCurrent", "close")
        }

        test("onUserCreate false skips the loop and still tears down") {
            val driver = RecordingDriver()
            installDriver(driver)
            installGl()
            val engine =
                ScriptedEngine(
                    onCreate = { false },
                    onUpdate = { _, _ -> error("onUserUpdate must not run") },
                    onDestroy = { error("onUserDestroy must not run") },
                )

            engine.start()

            engine.createCount shouldBe 1
            engine.destroyCount shouldBe 0
            engine.updateElapsed shouldBe emptyList()
            driver.calls.last() shouldBe "close"
        }

        test("onUserUpdate false leaves the inner loop and reaches onUserDestroy") {
            installDriver(RecordingDriver())
            installGl()
            val engine = ScriptedEngine(onUpdate = { _, _ -> false })

            engine.start()

            engine.updateElapsed.size shouldBe 1
            engine.destroyCount shouldBe 1
        }

        test("onUserDestroy false restarts the loop without a second onUserCreate or a new window") {
            val service = installDriver(RecordingDriver())
            installGl()
            val engine =
                ScriptedEngine(
                    onUpdate = { _, _ -> false },
                    onDestroy = { it.destroyCount >= 2 },
                )

            engine.start()

            engine.createCount shouldBe 1
            engine.updateElapsed.size shouldBe 2
            engine.destroyCount shouldBe 2
            service.createCount shouldBe 1
        }

        test("stop terminates the run and onUserDestroy is still consulted") {
            installDriver(RecordingDriver())
            installGl()
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        e.stop()
                        true
                    },
                )

            engine.start()

            engine.updateElapsed.size shouldBe 1
            engine.destroyCount shouldBe 1
        }

        test("a driver close request exits the loop and onUserDestroy is consulted") {
            val driver = RecordingDriver()
            installDriver(driver)
            installGl()
            var destroys = 0
            val engine =
                ScriptedEngine(
                    onUpdate = { _, _ ->
                        driver.scriptedClosing = true
                        true
                    },
                    onDestroy = {
                        destroys++
                        // Bounds the loop so a missing close handling fails instead of hanging.
                        check(destroys <= 2) { "the loop must exit when the driver reports closing" }
                        true
                    },
                )

            engine.start()

            engine.frame.frameCount shouldBe 1
            destroys shouldBe 1
        }

        test("onUserDestroy vetoes a close request, cancelling it and continuing the loop") {
            val driver = RecordingDriver()
            installDriver(driver)
            installGl()
            var frames = 0
            var destroys = 0
            val engine =
                ScriptedEngine(
                    onUpdate = { _, _ ->
                        // A one-shot platform close request on the first frame.
                        if (++frames == 1) driver.scriptedClosing = true
                        frames < 2
                    },
                    onDestroy = {
                        destroys++
                        destroys >= 2
                    },
                )

            engine.start()

            frames shouldBe 2
            destroys shouldBe 2
            driver.calls shouldContain "cancelClose"
        }

        test("dispatcher and requireEngineThread fail fast before start") {
            val engine = ScriptedEngine()

            shouldThrow<IllegalStateException> { engine.dispatcher }
            shouldThrow<IllegalStateException> { engine.requireEngineThread() }
        }

        test("requireEngineThread passes inside a callback") {
            installDriver(RecordingDriver())
            installGl()
            var checked = false
            val engine =
                ScriptedEngine(
                    onCreate = { e ->
                        e.requireEngineThread()
                        checked = true
                        true
                    },
                    onUpdate = { _, _ -> false },
                )

            engine.start()

            checked shouldBe true
        }

        test("a hop through the engine dispatcher stays on the engine thread") {
            installDriver(RecordingDriver())
            installGl()
            var hopped = false
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        withContext(e.dispatcher) { e.requireEngineThread() }
                        hopped = true
                        false
                    },
                )

            engine.start()

            hopped shouldBe true
        }

        test("a createResources failure closes the scope and the driver") {
            val driver = RecordingDriver()
            installDriver(driver)
            val probe = ProbeResource()
            Renderer.override(
                object : Renderer by rendererDefault {
                    override fun createResources(
                        device: GpuDevice,
                        scope: ResourceScope,
                    ) {
                        scope.register(ProbeKey, probe)
                        error("resource construction failed")
                    }
                },
            )
            val engine = ScriptedEngine()

            shouldThrow<IllegalStateException> { engine.start() }

            probe.closed shouldBe true
            driver.calls.last() shouldBe "close"
        }

        test("a makeCurrent failure closes the driver") {
            val base = RecordingDriver()
            installDriver(
                object : Driver by base {
                    override fun makeCurrent() = error("context creation failed")
                },
            )
            installGl()
            val engine = ScriptedEngine()

            shouldThrow<IllegalStateException> { engine.start() }

            base.calls.last() shouldBe "close"
        }
    })

private class ProbeResource : KGEResource {
    var closed = false

    override fun close() {
        closed = true
    }
}

private object ProbeKey : ResourceScope.Key<ProbeResource>
