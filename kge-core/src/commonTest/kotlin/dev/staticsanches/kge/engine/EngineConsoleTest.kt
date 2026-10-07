package dev.staticsanches.kge.engine

import dev.staticsanches.kge.engine.input.KeyboardKey
import dev.staticsanches.kge.engine.input.TextInputEvent
import dev.staticsanches.kge.engine.input.escape
import dev.staticsanches.kge.engine.input.k
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.testsupport.engine.RecordingDriver
import dev.staticsanches.kge.testsupport.engine.installDriver
import dev.staticsanches.kge.testsupport.engine.installGl
import dev.staticsanches.kge.time.FakeTimeService
import dev.staticsanches.kge.time.TimeService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The engine's text-entry and console step: the showing flag and its exit key,
 * the completion path while hidden, and the untouched input and draw target.
 */
class EngineConsoleTest :
    FunSpec({
        test("show sets isShowing and the exit key's press edge clears it") {
            val driver = RecordingDriver(scriptedFramebufferSize = Int2D(320, 240))
            installDriver(driver)
            installGl()
            val states = mutableListOf<Boolean>()
            var frames = 0
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        frames++
                        if (frames == 1) {
                            e.console.show(KeyboardKey.escape)
                            driver.input.setKeyDown(KeyboardKey.escape, true)
                        }
                        states += e.console.isShowing
                        frames < 3
                    },
                )

            engine.start()

            states shouldBe listOf(true, true, false)
        }

        test("the exit key's press that opened the console does not close it") {
            val driver = RecordingDriver(scriptedFramebufferSize = Int2D(320, 240))
            installDriver(driver)
            installGl()
            driver.input.setKeyDown(KeyboardKey.escape, true)
            val states = mutableListOf<Boolean>()
            var frames = 0
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        frames++
                        if (frames == 1) e.console.show(KeyboardKey.escape)
                        states += e.console.isShowing
                        frames < 2
                    },
                )

            engine.start()

            states shouldBe listOf(true, true)
        }

        test("the console's key handling does not consume the game's input state") {
            val driver = RecordingDriver(scriptedFramebufferSize = Int2D(320, 240))
            installDriver(driver)
            installGl()
            val seen = mutableListOf<Pair<Boolean, Boolean>>()
            var frames = 0
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        frames++
                        if (frames == 1) {
                            e.console.show(KeyboardKey.escape)
                            driver.input.setKeyDown(KeyboardKey.k, true)
                        }
                        if (frames == 2) driver.input.setKeyDown(KeyboardKey.k, false)
                        val state = e.input.key(KeyboardKey.k)
                        seen += state.pressed to state.released
                        frames < 3
                    },
                )

            engine.start()

            seen shouldBe listOf(false to false, true to false, false to true)
        }

        test("Enter while the console is hidden completes the text entry and disables it") {
            val driver = RecordingDriver(scriptedFramebufferSize = Int2D(320, 240))
            installDriver(driver)
            installGl()
            val completed = mutableListOf<String>()
            var enabledAfter = true
            var frames = 0
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        frames++
                        if (frames == 1) {
                            e.textEntry.enable("abc")
                            driver.input.pressedEdit(TextInputEvent.Edit.ENTER)
                        }
                        if (frames == 2) enabledAfter = e.textEntry.isEnabled
                        frames < 2
                    },
                    onComplete = { completed += it },
                )

            engine.start()

            completed shouldBe listOf("abc")
            enabledAfter shouldBe false
        }

        test("the console never changes the selected draw target") {
            installDriver(RecordingDriver(scriptedFramebufferSize = Int2D(320, 240)))
            installGl()
            lateinit var expected: Sprite
            lateinit var selected: Sprite
            var targetIndex = -1
            var frames = 0
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        frames++
                        if (frames == 1) {
                            e.layers.createLayer()
                            e.setDrawTarget(1)
                            expected = e.layers[1].target
                            e.console.show(KeyboardKey.escape)
                        }
                        frames < 2
                    },
                    onDestroy = { e ->
                        targetIndex = e.layers.targetIndex
                        selected = e.drawTarget!!
                        true
                    },
                )

            engine.start()

            targetIndex shouldBe 1
            selected shouldBeSameInstanceAs expected
        }

        test("with suspendTime the elapsed handed to onUserUpdate is zero while showing") {
            installDriver(RecordingDriver(scriptedFramebufferSize = Int2D(320, 240)))
            installGl()
            TimeService.override(FakeTimeService(16.milliseconds))
            var frames = 0
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        frames++
                        when (frames) {
                            2 -> e.console.show(KeyboardKey.escape)
                            4 -> e.console.hide()
                        }
                        frames < 6
                    },
                )

            engine.start()

            engine.updateElapsed shouldBe
                listOf(
                    0.milliseconds,
                    16.milliseconds,
                    0.milliseconds,
                    0.milliseconds,
                    16.milliseconds,
                    16.milliseconds,
                )
        }

        test("Enter while showing runs the command and records history only when the hook returns true") {
            val driver = RecordingDriver(scriptedFramebufferSize = Int2D(320, 240))
            installDriver(driver)
            installGl()
            val commands = mutableListOf<String>()
            var clearedAfterSubmit = false
            var loadedByUp = ""
            var frames = 0
            val recording =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        frames++
                        when (frames) {
                            1 -> {
                                e.console.show(KeyboardKey.escape)
                                e.textEntry.enable("go")
                                driver.input.pressedEdit(TextInputEvent.Edit.ENTER)
                            }

                            2 -> {
                                clearedAfterSubmit = e.textEntry.text.isEmpty()
                                driver.input.pressedEdit(TextInputEvent.Edit.UP)
                            }
                        }
                        if (frames == 3) loadedByUp = e.textEntry.text
                        frames < 3
                    },
                    onCommand = {
                        commands += it
                        true
                    },
                )

            recording.start()

            commands shouldBe listOf("go")
            clearedAfterSubmit shouldBe true
            loadedByUp shouldBe "go"

            installDriver(RecordingDriver(scriptedFramebufferSize = Int2D(320, 240)))
            installGl()
            var defaultFrames = 0
            var defaultLoadedByUp = "unset"
            val inert =
                DefaultHookEngine { e ->
                    defaultFrames++
                    when (defaultFrames) {
                        1 -> {
                            e.console.show(KeyboardKey.escape)
                            e.textEntry.enable("go")
                            e.driver.input.pressedEdit(TextInputEvent.Edit.ENTER)
                        }

                        2 -> e.driver.input.pressedEdit(TextInputEvent.Edit.UP)
                    }
                    if (defaultFrames == 3) defaultLoadedByUp = e.textEntry.text
                    defaultFrames < 3
                }

            inert.start()

            defaultLoadedByUp shouldBe ""
        }

        test("showing and hiding across frames leaves the draw target and layer selection unchanged") {
            installDriver(RecordingDriver(scriptedFramebufferSize = Int2D(320, 240)))
            installGl()
            lateinit var expected: Sprite
            lateinit var selected: Sprite
            var targetIndex = -1
            var frames = 0
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        frames++
                        when (frames) {
                            1 -> {
                                e.layers.createLayer()
                                e.setDrawTarget(1)
                                expected = e.layers[1].target
                                e.console.show(KeyboardKey.escape)
                            }

                            2 -> e.console.hide()
                            3 -> e.console.show(KeyboardKey.escape)
                        }
                        frames < 3
                    },
                    onDestroy = { e ->
                        targetIndex = e.layers.targetIndex
                        selected = e.drawTarget!!
                        true
                    },
                )

            engine.start()

            targetIndex shouldBe 1
            selected shouldBeSameInstanceAs expected
        }
    })

/** An engine that overrides nothing but the update, so the hooks keep their engine defaults. */
private class DefaultHookEngine(
    private val update: suspend (DefaultHookEngine) -> Boolean,
) : Engine(WindowConfig(screenWidth = 320, screenHeight = 240)) {
    override suspend fun onUserUpdate(elapsed: Duration): Boolean = update(this)
}
