package dev.staticsanches.kge.engine

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** The engine's driver role: the platform driver is available only while the engine runs. */
@OptIn(KGESensitiveAPI::class)
class EngineDriverTest :
    FunSpec({
        test("the driver fails fast before start") {
            val engine = ScriptedEngine()

            shouldThrow<IllegalStateException> { engine.driver }
        }

        test("the driver is the run's driver inside a callback and gone after teardown") {
            val driver = RecordingDriver()
            installDriver(driver)
            installGl()
            var observed: Driver? = null
            val engine =
                ScriptedEngine(
                    onCreate = { e ->
                        observed = e.driver
                        true
                    },
                    onUpdate = { _, _ -> false },
                )

            engine.start()

            observed shouldBe driver
            shouldThrow<IllegalStateException> { engine.driver }
        }
    })
