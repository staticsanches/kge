package dev.staticsanches.kge.engine

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.renderer.device.GlfwTestDevice
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.lwjgl.system.Platform

private const val SMOKE_FRAMES = 3
private val SMOKE_COLOR = Colors.RED

private val isMacOs: Boolean = Platform.get() == Platform.MACOSX

/** The macOS path skips: AppKit requires GLFW on the process first thread. */
private val glfwAvailable: Boolean =
    if (isMacOs) {
        false
    } else {
        GlfwTestDevice.detect()?.let { probe ->
            probe.close()
            true
        } ?: false
    }

/**
 * The real-GL layer smoke for the JVM platform default: a hidden GLFW window
 * through the production driver composites layer 0 after its target was filled
 * with a raster primitive. The upload path being consumed and the primitive
 * surviving in the CPU target prove the layer path executed on a real GL
 * context. Skips when the platform cannot provide a context and on macOS.
 */
class EngineLayerSmokeTest :
    FunSpec({
        test("the real JVM loop composites a filled layer")
            .config(enabled = glfwAvailable) {
                val service = HiddenGlfwDriverService()
                DriverService.override(service)
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
                            if (++frames >= 2) {
                                uploadConsumed = !e.layers[0].update
                                painted = e.layers[0].target.get(4, 4) == SMOKE_COLOR
                            }
                            frames < SMOKE_FRAMES
                        },
                    )

                engine.start()

                engine.frame.frameCount shouldBe SMOKE_FRAMES
                uploadConsumed shouldBe true
                painted shouldBe true
                service.closed shouldBe true
            }
    })
