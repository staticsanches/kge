package dev.staticsanches.kge.resource

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class KGEResourceContractTest :
    FunSpec({
        test("KGEResource is an AutoCloseable marker") {
            val resource =
                object : KGEResource {
                    override fun close() = Unit
                }

            val autoCloseable: AutoCloseable = resource
            autoCloseable.close()
        }

        test("a KGECleanAction invokes its payload once per call") {
            var calls = 0
            val action = KGECleanAction { calls++ }

            action()
            action()

            calls shouldBe 2
        }
    })
