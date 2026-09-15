package dev.staticsanches.kge.engine

import dev.staticsanches.kge.image.Sprite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** The engine's layer stack: created during start and owned by the engine's resource scope. */
class EngineLayersTest :
    FunSpec({
        test("layers fails fast before start") {
            val engine = ScriptedEngine()

            shouldThrow<IllegalStateException> { engine.layers }
        }

        test("start creates layer 0 within the scope, which releases it on exit") {
            installDriver(RecordingDriver())
            installGl()
            var layerCount = -1
            var target: Sprite? = null
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        layerCount = e.layers.size
                        target = e.layers[0].target
                        false
                    },
                )

            engine.start()

            layerCount shouldBe 1
            shouldThrow<IllegalStateException> { engine.layers }
            shouldThrow<IllegalStateException> { target!!.get(0, 0) }
        }
    })
