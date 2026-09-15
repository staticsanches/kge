package dev.staticsanches.kge.engine

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.renderer.decal.Decal
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

/** The engine's draw target and draw mode roles. */
class EngineDrawTargetTest :
    FunSpec({
        test("draw modes default to normal, fan and an active transfer") {
            installDriver(RecordingDriver())
            installGl()
            val engine = ScriptedEngine()

            engine.pixelMode shouldBe Pixel.Mode.Normal
            engine.decalMode shouldBe Decal.Mode.NORMAL
            engine.decalStructure shouldBe Decal.Structure.FAN
            engine.suspendTextureTransfer shouldBe false
        }

        test("draw modes are mutable") {
            installDriver(RecordingDriver())
            installGl()
            val engine = ScriptedEngine()

            engine.pixelMode = Pixel.Mode.Mask
            engine.decalMode = Decal.Mode.ADDITIVE
            engine.decalStructure = Decal.Structure.STRIP
            engine.suspendTextureTransfer = true

            engine.pixelMode shouldBe Pixel.Mode.Mask
            engine.decalMode shouldBe Decal.Mode.ADDITIVE
            engine.decalStructure shouldBe Decal.Structure.STRIP
            engine.suspendTextureTransfer shouldBe true
        }

        test("setDrawTarget and drawTarget fail fast before start") {
            val engine = ScriptedEngine()

            shouldThrow<IllegalStateException> { engine.setDrawTarget(0) }
            shouldThrow<IllegalStateException> { engine.drawTarget = null }
        }

        test("drawTarget is initialized to layer 0 after start") {
            installDriver(RecordingDriver())
            installGl()
            lateinit var expected: Sprite
            val engine =
                ScriptedEngine(
                    onUpdate = { _, _ -> false },
                    onDestroy = { e ->
                        expected = e.layers[0].target
                        true
                    },
                )

            engine.start()

            engine.drawTarget shouldBeSameInstanceAs expected
        }

        test("a non-null drawTarget assignment leaves the target layer selection unchanged") {
            installDriver(RecordingDriver())
            installGl()
            lateinit var selection: Sprite
            lateinit var expected: Sprite
            var targetIndex = -1
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        e.layers.createLayer()
                        e.setDrawTarget(1)
                        expected = e.layers[0].target
                        e.drawTarget = expected
                        selection = e.drawTarget!!
                        targetIndex = e.layers.targetIndex
                        false
                    },
                )

            engine.start()

            selection shouldBeSameInstanceAs expected
            targetIndex shouldBe 1
        }

        test("drawTarget = null selects layer 0 and resets targetIndex") {
            installDriver(RecordingDriver())
            installGl()
            lateinit var selection: Sprite
            lateinit var expected: Sprite
            var targetIndex = -1
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        e.layers.createLayer()
                        e.setDrawTarget(1)
                        expected = e.layers[0].target
                        e.drawTarget = null
                        selection = e.drawTarget!!
                        targetIndex = e.layers.targetIndex
                        false
                    },
                )

            engine.start()

            selection shouldBeSameInstanceAs expected
            targetIndex shouldBe 0
        }

        test("setDrawTarget selects the layer and marks it dirty by default") {
            installDriver(RecordingDriver())
            installGl()
            lateinit var selection: Sprite
            lateinit var expected: Sprite
            var targetIndex = -1
            var dirty = false
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        e.layers.createLayer()
                        e.setDrawTarget(1)
                        expected = e.layers[1].target
                        selection = e.drawTarget!!
                        targetIndex = e.layers.targetIndex
                        dirty = e.layers[1].update
                        false
                    },
                )

            engine.start()

            selection shouldBeSameInstanceAs expected
            targetIndex shouldBe 1
            dirty shouldBe true
        }

        test("setDrawTarget keeps the stack target and drawTarget in agreement") {
            installDriver(RecordingDriver())
            installGl()
            lateinit var selectedLayerTarget: Sprite
            var targetIndex = -1
            lateinit var captured: Sprite
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        e.layers.createLayer()
                        e.setDrawTarget(1)
                        targetIndex = e.layers.targetIndex
                        selectedLayerTarget = e.layers.target.target
                        captured = e.drawTarget!!
                        false
                    },
                )

            engine.start()

            targetIndex shouldBe 1
            captured shouldBeSameInstanceAs selectedLayerTarget
        }

        test("setDrawTarget with an out-of-range index throws before mutating the selection") {
            installDriver(RecordingDriver())
            installGl()
            lateinit var previousTarget: Sprite
            lateinit var currentTarget: Sprite
            lateinit var selectedLayerTarget: Sprite
            var targetIndex = -1
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        e.layers.createLayer()
                        e.setDrawTarget(1)
                        previousTarget = e.drawTarget!!

                        shouldThrow<IndexOutOfBoundsException> { e.setDrawTarget(9) }

                        currentTarget = e.drawTarget!!
                        targetIndex = e.layers.targetIndex
                        selectedLayerTarget = e.layers[1].target
                        false
                    },
                )

            engine.start()

            targetIndex shouldBe 1
            currentTarget shouldBeSameInstanceAs previousTarget
            currentTarget shouldBeSameInstanceAs selectedLayerTarget
        }

        test("setDrawTarget with dirty false leaves the layer unmarked") {
            installDriver(RecordingDriver())
            installGl()
            lateinit var selection: Sprite
            lateinit var expected: Sprite
            var dirty = true
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        e.layers.createLayer()
                        e.setDrawTarget(1, dirty = false)
                        expected = e.layers[1].target
                        selection = e.drawTarget!!
                        dirty = e.layers[1].update
                        false
                    },
                )

            engine.start()

            selection shouldBeSameInstanceAs expected
            dirty shouldBe false
        }

        test("a layer other than the draw target keeps its own target") {
            installDriver(RecordingDriver())
            installGl()
            lateinit var layerOneTarget: Sprite
            lateinit var layerZeroTarget: Sprite
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        e.layers.createLayer()
                        layerOneTarget = e.layers[1].target
                        layerZeroTarget = e.layers[0].target
                        e.drawTarget = layerOneTarget
                        false
                    },
                )

            engine.start()

            engine.drawTarget shouldBeSameInstanceAs layerOneTarget
            engine.drawTarget shouldNotBeSameInstanceAs layerZeroTarget
        }
    })
