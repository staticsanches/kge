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
    })
