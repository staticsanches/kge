package dev.staticsanches.kge.engine

import dev.staticsanches.kge.renderer.device.GlfwTestDevice
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.lwjgl.system.Platform
import kotlin.time.Duration

private const val SMOKE_FRAMES = 3

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
 * The real-GL engine smoke for the JVM platform default: open a hidden GLFW
 * window through the production driver, run the production loop for a few
 * frames, and assert the loop advanced and the driver was torn down. Skips when
 * the platform cannot provide a context and on macOS.
 */
class EngineSmokeTest :
    FunSpec({
        test("the real JVM loop runs K frames and closes the driver")
            .config(enabled = glfwAvailable) {
                val service = HiddenGlfwDriverService()
                DriverService.override(service)
                val engine = CountingEngine(WindowConfig(screenWidth = 64, screenHeight = 48))

                engine.start()

                engine.frame.frameCount shouldBe SMOKE_FRAMES
                service.closed shouldBe true
            }
    })

/** Hands out the production driver with a hidden window and records its close. */
class HiddenGlfwDriverService : DriverService {
    var closed = false
        private set

    override fun create(config: WindowConfig): Driver {
        val delegate = GlfwDriverService.create(config, visible = false)
        return object : Driver by delegate {
            override fun close() {
                closed = true
                delegate.close()
            }
        }
    }
}

/** Ends the loop after [SMOKE_FRAMES] updates. */
private class CountingEngine(
    config: WindowConfig,
) : Engine(config) {
    var frames = 0
        private set

    override suspend fun onUserUpdate(elapsed: Duration): Boolean = ++frames < SMOKE_FRAMES
}
