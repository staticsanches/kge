package dev.staticsanches.kge.text.ttf

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * The FreeType module is process-scoped: the package builds a new wasm instance
 * per `initFreeType()`, so the shared resolution must stay single-flight.
 */
class FreeTypeModuleTest :
    FunSpec({
        test("two sequential resolutions are the same instance") {
            freeTypeModule() shouldBeSameInstanceAs freeTypeModule()
        }

        test("two concurrent resolutions are the same instance") {
            coroutineScope {
                val first = async { freeTypeModule() }
                val second = async { freeTypeModule() }

                first.await() shouldBeSameInstanceAs second.await()
            }
        }
    })
