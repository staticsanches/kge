package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.Driver
import dev.staticsanches.kge.engine.GlfwWindow
import dev.staticsanches.kge.engine.HasDriver
import dev.staticsanches.kge.engine.RecordingDriver
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** The JVM window addon: defaults delegating the window calls to the driver's window capability. */
@OptIn(KGESensitiveAPI::class)
class WindowManipulationAddonTest :
    FunSpec({
        test("the addon delegates title/show/hide and round-trips the close flag") {
            val driver = FakeGlfwDriver()
            val host = FakeWindowHost(driver)

            host.changeWindowTitle("KGE")
            host.showWindow()
            host.hideWindow()
            host.windowShouldClose shouldBe false
            host.windowShouldClose = true

            driver.recorded shouldBe listOf("setTitle:KGE", "show", "hide")
            driver.closeRequested shouldBe true
            host.windowShouldClose shouldBe true
        }

        test("a driver without the window capability fails fast with a clear message") {
            val host = FakeWindowHost(RecordingDriver())

            shouldThrow<IllegalStateException> { host.changeWindowTitle("KGE") }
                .message shouldBe "WindowManipulationAddon requires the engine's GLFW driver"
        }
    })

private class FakeGlfwDriver :
    Driver by RecordingDriver(),
    GlfwWindow {
    override var closeRequested: Boolean = false
    val recorded = mutableListOf<String>()

    override fun setTitle(title: String) {
        recorded += "setTitle:$title"
    }

    override fun show() {
        recorded += "show"
    }

    override fun hide() {
        recorded += "hide"
    }
}

private class FakeWindowHost(
    override val driver: Driver,
) : HasDriver,
    WindowManipulationAddon
