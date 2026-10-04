package dev.staticsanches.kge.engine

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.renderer.decal.service.DrawPartialDecalService
import dev.staticsanches.kge.testsupport.engine.RecordingDriver
import dev.staticsanches.kge.testsupport.engine.installDriver
import dev.staticsanches.kge.testsupport.engine.installGl
import dev.staticsanches.kge.text.KGEFont
import dev.staticsanches.kge.text.fontPx
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The engine as a [TextAddon]: a live principal font and tab size during the
 * run, both configurable from a callback and both released at teardown.
 */
class EngineTextFontTest :
    FunSpec({
        fun installEngineDecalRecorder(): RecordingEngineDecalService {
            val recorder = RecordingEngineDecalService(DrawPartialDecalService.original)
            DrawPartialDecalService.override(recorder)
            return recorder
        }

        test("textFont is live during the run and tabSizeInSpaces starts at 4") {
            installDriver(RecordingDriver())
            installGl()
            var measured = Int2D.ZERO
            var tabSize = 0
            var defaultFace = false
            val engine =
                ScriptedEngine(
                    onCreate = { e ->
                        measured = e.measureText("AB")
                        tabSize = e.tabSizeInSpaces
                        defaultFace = e.textFont.face === e.textFont.family.defaultFace
                        true
                    },
                    onUpdate = { _, _ -> false },
                )

            engine.start()

            measured shouldBe Int2D(16, 8)
            tabSize shouldBe 4
            defaultFace shouldBe true
        }

        test("assigning textFont changes subsequent default-argument calls") {
            installDriver(RecordingDriver())
            installGl()
            var before = Int2D.ZERO
            var after = Int2D.ZERO
            val engine =
                ScriptedEngine(
                    onCreate = { e ->
                        before = e.measureText("Ai")
                        val family = e.textFont.family
                        e.textFont = family.faces.single { it !== family.defaultFace }.font(e.resourceScope, 8.fontPx)
                        after = e.measureText("Ai")
                        true
                    },
                    onUpdate = { _, _ -> false },
                )

            engine.start()

            before shouldBe Int2D(16, 8)
            after shouldBe Int2D(11, 8)
        }

        test("tabSizeInSpaces rejects non-positive values and reaches measure, draw and decal") {
            installDriver(RecordingDriver())
            installGl()
            val recorder = installEngineDecalRecorder()
            var measuredAt4 = Int2D.ZERO
            var measuredAt2 = Int2D.ZERO
            var wideAt4 = false
            var wideAt2 = true
            var decalAt4: Float2D? = null
            var decalAt2: Float2D? = null
            val engine =
                ScriptedEngine(
                    onCreate = { e ->
                        measuredAt4 = e.measureText("A\tB")
                        e.tabSizeInSpaces = 2
                        measuredAt2 = e.measureText("A\tB")
                        shouldThrow<IllegalArgumentException> { e.tabSizeInSpaces = 0 }
                        shouldThrow<IllegalArgumentException> { e.tabSizeInSpaces = -1 }

                        e.tabSizeInSpaces = 4
                        val target = e.drawTarget!!
                        target.clear(Colors.TRANSPARENT)
                        e.drawText(Int2D(0, 0), "A\tB")
                        wideAt4 = engineTextPainted(target).any { it.first >= 32 }

                        e.tabSizeInSpaces = 2
                        target.clear(Colors.TRANSPARENT)
                        e.drawText(Int2D(0, 0), "A\tB")
                        wideAt2 = engineTextPainted(target).any { it.first >= 32 }

                        e.tabSizeInSpaces = 4
                        e.drawTextDecal(Float2D(0f, 0f), "A\tB")
                        decalAt4 = recorder.calls[1].position
                        recorder.calls.clear()

                        e.tabSizeInSpaces = 2
                        e.drawTextDecal(Float2D(0f, 0f), "A\tB")
                        decalAt2 = recorder.calls[1].position
                        true
                    },
                    onUpdate = { _, _ -> false },
                )

            engine.start()

            measuredAt4 shouldBe Int2D(48, 8)
            measuredAt2 shouldBe Int2D(32, 8)
            wideAt4 shouldBe true
            wideAt2 shouldBe false
            decalAt4 shouldBe Float2D(40f, 0f)
            decalAt2 shouldBe Float2D(24f, 0f)
        }

        test("textFont fails fast before and after the run") {
            val engine = ScriptedEngine(onUpdate = { _, _ -> false })

            shouldThrow<IllegalStateException> { engine.textFont }

            installDriver(RecordingDriver())
            installGl()
            engine.start()

            shouldThrow<IllegalStateException> { engine.textFont }
        }

        test("coreFontFamily is the principal font's family and reaches both designs") {
            installDriver(RecordingDriver())
            val gl = installGl()
            var sameFamily = false
            var principalIsDefault = false
            var proportionalIsOther = false
            var proportionalWidth = Int2D.ZERO
            var texturesAdded = -1
            val engine =
                ScriptedEngine(
                    onCreate = { e ->
                        val texturesBefore = gl.calls.count { it.name == "createTexture" }
                        val family = e.coreFontFamily
                        sameFamily = e.textFont.family === family
                        principalIsDefault = e.textFont.face === family.defaultFace
                        proportionalIsOther = family.proportional !== family.monospaced
                        proportionalWidth =
                            family.proportional.font(e.resourceScope, 8.fontPx).measureText("Ai", 4)
                        texturesAdded = gl.calls.count { it.name == "createTexture" } - texturesBefore
                        true
                    },
                    onUpdate = { _, _ -> false },
                )

            engine.start()

            sameFamily shouldBe true
            principalIsDefault shouldBe true
            proportionalIsOther shouldBe true
            proportionalWidth shouldBe Int2D(11, 8)
            // Reaching the other design through the engine's handle reuses the
            // family's payload instead of building a second family.
            texturesAdded shouldBe 0
        }

        test("coreFontFamily fails fast before and after the run") {
            val engine = ScriptedEngine(onUpdate = { _, _ -> false })

            shouldThrow<IllegalStateException> { engine.coreFontFamily }

            installDriver(RecordingDriver())
            installGl()
            engine.start()

            shouldThrow<IllegalStateException> { engine.coreFontFamily }
        }

        test("resourceScope fails fast before and after the run") {
            val engine = ScriptedEngine(onUpdate = { _, _ -> false })

            shouldThrow<IllegalStateException> { engine.resourceScope }

            installDriver(RecordingDriver())
            installGl()
            engine.start()

            shouldThrow<IllegalStateException> { engine.resourceScope }
        }

        test("the principal font is released when the scope closes at teardown") {
            installDriver(RecordingDriver())
            installGl()
            var principal: KGEFont? = null
            val engine =
                ScriptedEngine(
                    onCreate = { e ->
                        principal = e.textFont
                        true
                    },
                    onUpdate = { _, _ -> false },
                )

            engine.start()

            shouldThrow<IllegalStateException> { principal!!.measureText("A", 4) }
        }
    })

private fun engineTextPainted(sprite: Sprite): Set<Pair<Int, Int>> =
    (0 until sprite.height)
        .flatMap { y -> (0 until sprite.width).map { x -> x to y } }
        .filter { (x, y) -> sprite.get(x, y) != Colors.TRANSPARENT }
        .toSet()

private class EngineDecalCall(
    val position: Float2D,
)

private class RecordingEngineDecalService(
    private val delegate: DrawPartialDecalService,
) : DrawPartialDecalService {
    val calls = mutableListOf<EngineDecalCall>()

    override fun drawPartialDecal(
        position: Float2D,
        decal: Decal,
        sourcePosition: Float2D,
        sourceSize: Float2D,
        scale: Float2D,
        tint: Pixel,
        mode: Decal.Mode,
        structure: Decal.Structure,
        viewport: Int2D,
    ): DecalInstance {
        calls += EngineDecalCall(position)
        return delegate.drawPartialDecal(
            position,
            decal,
            sourcePosition,
            sourceSize,
            scale,
            tint,
            mode,
            structure,
            viewport,
        )
    }
}
