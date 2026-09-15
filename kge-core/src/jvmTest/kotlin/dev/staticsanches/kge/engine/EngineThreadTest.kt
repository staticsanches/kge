package dev.staticsanches.kge.engine

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EngineThreadTest :
    FunSpec({
        test("requireEngineThread fails fast off the engine thread") {
            installDriver(RecordingDriver())
            installGl()
            val engine = ScriptedEngine(onUpdate = { _, _ -> false })

            engine.start()

            var thrown = false
            val other = Thread { thrown = runCatching { engine.requireEngineThread() }.isFailure }
            other.start()
            other.join()

            thrown shouldBe true
        }

        test("setDrawTarget and drawTarget fail fast off the engine thread") {
            installDriver(RecordingDriver())
            installGl()
            var guarded = false
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        val other =
                            Thread {
                                val setGuard = runCatching { e.setDrawTarget(0) }.exceptionOrNull()
                                val assignGuard = runCatching { e.drawTarget = null }.exceptionOrNull()
                                guarded = setGuard is IllegalStateException && assignGuard is IllegalStateException
                            }
                        other.start()
                        other.join()
                        false
                    },
                )

            engine.start()

            guarded shouldBe true
        }
    })
