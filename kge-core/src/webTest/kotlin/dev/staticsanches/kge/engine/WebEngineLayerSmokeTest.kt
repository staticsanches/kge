package dev.staticsanches.kge.engine

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.renderer.gl.glContext
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val SMOKE_FRAMES = 3
private val SMOKE_COLOR = Colors.RED

/**
 * The real-GL layer smoke for the web platform default: a canvas with a WebGL2
 * context through the production driver composites layer 0 after its target was
 * filled with a raster primitive. The upload path being consumed and the
 * primitive surviving in the CPU target prove the layer path executed on a real
 * context. Runs on both browser targets (js + wasmJs).
 */
class WebEngineLayerSmokeTest :
    FunSpec({
        test("the real web loop composites a filled layer") {
            var contextDuringRun = false
            var painted = false
            var uploadConsumed = false
            var frames = 0
            val engine =
                ScriptedEngine(
                    config = WindowConfig(screenWidth = 64, screenHeight = 48),
                    onCreate = { e ->
                        e.layers[0].show = true
                        e.layers[0].update = true
                        Rasterizer.fillRect(e.layers[0].target, 4, 4, 20, 20, SMOKE_COLOR, Pixel.Mode.Normal)
                        true
                    },
                    onUpdate = { e, _ ->
                        contextDuringRun = glContext != null
                        if (++frames >= 2) {
                            uploadConsumed = !e.layers[0].update
                            painted = e.layers[0].target.get(4, 4) == SMOKE_COLOR
                        }
                        frames < SMOKE_FRAMES
                    },
                )

            engine.start()

            engine.frame.frameCount shouldBe SMOKE_FRAMES
            contextDuringRun shouldBe true
            uploadConsumed shouldBe true
            painted shouldBe true
            glContext shouldBe null
        }
    })
