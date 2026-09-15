package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.Driver
import dev.staticsanches.kge.engine.GlfwDriverService
import dev.staticsanches.kge.engine.HasDriver
import dev.staticsanches.kge.engine.WindowConfig
import dev.staticsanches.kge.renderer.device.GlfwTestDevice
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.lwjgl.system.Platform

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
 * The real-GL smoke for the JVM window addon: a hidden GLFW window through the
 * production driver accepts a title change and a close request. Skips when the
 * platform cannot provide a context and on macOS.
 */
@OptIn(KGESensitiveAPI::class)
class WindowManipulationAddonSmokeTest :
    FunSpec({
        test("the hidden GLFW window accepts a title change and a close request")
            .config(enabled = glfwAvailable) {
                val config = WindowConfig(screenWidth = 64, screenHeight = 48)
                GlfwDriverService.create(config, visible = false).use { driver ->
                    val host = SmokeWindowHost(driver)

                    host.changeWindowTitle("KGE smoke")
                    driver.isClosing() shouldBe false
                    host.windowShouldClose = true
                    driver.isClosing() shouldBe true
                }
            }
    })

private class SmokeWindowHost(
    override val driver: Driver,
) : HasDriver,
    WindowManipulationAddon
