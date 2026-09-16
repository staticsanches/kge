package dev.staticsanches.kge.engine

import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.text.fontDecal
import dev.staticsanches.kge.text.fontSheet
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The engine's [HasResourceScope] exposure: the run's scope is readable inside
 * the callbacks and holds the bitmap font, which is released with the scope,
 * while reading the scope outside a run fails fast.
 */
class EngineTextResourcesTest :
    FunSpec({
        test("resourceScope is readable during the run and resolves the font sheet") {
            installDriver(RecordingDriver())
            installGl()
            var width = 0
            var height = 0
            val engine =
                ScriptedEngine(
                    onCreate = { e ->
                        val sheet = fontSheet(e.resourceScope)
                        width = sheet.width
                        height = sheet.height
                        true
                    },
                    onUpdate = { _, _ -> false },
                )

            engine.start()

            width shouldBe 128
            height shouldBe 48
        }

        test("the font is released when the scope closes at teardown") {
            installDriver(RecordingDriver())
            installGl()
            var sheet: Sprite? = null
            var decal: Decal? = null
            val engine =
                ScriptedEngine(
                    onCreate = { e ->
                        sheet = fontSheet(e.resourceScope)
                        decal = fontDecal(e.resourceScope)
                        true
                    },
                    onUpdate = { _, _ -> false },
                )

            engine.start()

            shouldThrow<IllegalStateException> { sheet!!.get(0, 0) }
            shouldThrow<IllegalStateException> { decal!!.update() }
        }

        test("resourceScope fails fast before and after the run") {
            val engine = ScriptedEngine(onUpdate = { _, _ -> false })

            shouldThrow<IllegalStateException> { engine.resourceScope }

            installDriver(RecordingDriver())
            installGl()
            engine.start()

            shouldThrow<IllegalStateException> { engine.resourceScope }
        }
    })
