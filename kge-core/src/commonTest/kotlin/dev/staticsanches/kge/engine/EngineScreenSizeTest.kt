package dev.staticsanches.kge.engine

import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

/** The engine's screen resizing: olc's `SetScreenSize`. */
class EngineScreenSizeTest :
    FunSpec({
        test("setScreenSize rejects a non-positive width or height") {
            installDriver(RecordingDriver())
            installGl()
            val engine = ScriptedEngine()

            shouldThrow<IllegalArgumentException> { engine.setScreenSize(0, 240) }
                .message shouldBe "setScreenSize requires a positive size, was 0x240"
            shouldThrow<IllegalArgumentException> { engine.setScreenSize(320, -1) }
                .message shouldBe "setScreenSize requires a positive size, was 320x-1"
        }

        test("setScreenSize before start fails fast") {
            val engine = ScriptedEngine()

            shouldThrow<IllegalStateException> { engine.setScreenSize(160, 120) }
        }

        test("setScreenSize after the run fails fast without mutating the window") {
            installDriver(RecordingDriver())
            installGl()
            val engine = ScriptedEngine(onUpdate = { _, _ -> false })

            engine.start()

            val before = engine.window.screenSize
            shouldThrow<IllegalStateException> { engine.setScreenSize(160, 120) }
            engine.window.screenSize shouldBe before
        }

        test("setScreenSize updates the window, resizes every layer and resets the draw target to layer 0") {
            installDriver(RecordingDriver())
            installGl()
            lateinit var previousTargets: List<Sprite>
            lateinit var resizedTargets: List<Sprite>
            var screenSize: Int2D? = null
            var invertedScreenSize: Float2D? = null
            var updates: List<Boolean> = emptyList()
            var targetIndex = -1
            lateinit var selectedTarget: Sprite
            lateinit var drawTarget: Sprite
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        e.layers.createLayer()
                        e.setDrawTarget(1)
                        previousTargets = listOf(e.layers[0].target, e.layers[1].target)

                        e.setScreenSize(160, 120)

                        screenSize = e.window.screenSize
                        invertedScreenSize = e.window.invertedScreenSize
                        resizedTargets = listOf(e.layers[0].target, e.layers[1].target)
                        updates = listOf(e.layers[0].update, e.layers[1].update)
                        targetIndex = e.layers.targetIndex
                        selectedTarget = e.layers[0].target
                        drawTarget = e.drawTarget!!
                        false
                    },
                )

            engine.start()

            screenSize shouldBe Int2D(160, 120)
            invertedScreenSize shouldBe Float2D(1f / 160f, 1f / 120f)
            resizedTargets.forEach { target ->
                target.width shouldBe 160
                target.height shouldBe 120
            }
            resizedTargets.forEachIndexed { index, target ->
                target shouldNotBeSameInstanceAs previousTargets[index]
            }
            updates shouldBe listOf(true, true)
            targetIndex shouldBe 0
            drawTarget shouldBeSameInstanceAs selectedTarget
            previousTargets.forEach { target -> shouldThrow<IllegalStateException> { target.get(0, 0) } }
        }

        test("the next frame re-fits the viewport to the new screen size") {
            val gl = installGl()
            installDriver(RecordingDriver(scriptedFramebufferSize = Int2D(1000, 1000)))
            var frames = 0
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        when (++frames) {
                            1 -> e.setScreenSize(160, 160)
                            else -> gl.clear()
                        }
                        frames < 2
                    },
                )

            engine.start()

            val viewports = gl.calls.filter { it.name == "viewport" }
            viewports.size shouldBe 1
            viewports.single().arguments shouldBe listOf(0, 0, 1000, 1000)
        }
    })
