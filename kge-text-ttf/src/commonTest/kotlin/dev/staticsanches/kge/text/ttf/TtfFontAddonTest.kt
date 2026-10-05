package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.DriverService
import dev.staticsanches.kge.engine.Engine
import dev.staticsanches.kge.engine.WindowConfig
import dev.staticsanches.kge.font.roboto.Roboto
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.renderer.gl.service.GLService
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.testsupport.engine.RecordingDriver
import dev.staticsanches.kge.testsupport.engine.installDriver
import dev.staticsanches.kge.testsupport.engine.installGl
import dev.staticsanches.kge.text.KGEFont
import dev.staticsanches.kge.text.fontPx
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlin.time.Duration

/** The screen every addon frame runs at. */
private val SCREEN_SIZE = Int2D(64, 48)

private const val SIZE_PX = 16
private const val TAB_SIZE = 4

/**
 * The loading addon over a headless engine host: the loads land in the run's
 * own scope, and the principal font drives every default text call.
 */
@OptIn(KGESensitiveAPI::class)
class TtfFontAddonTest :
    FunSpec({
        test("loadFont adopts both overloads' families into the run's scope and releases them with it") {
            val engine = AddonHost()
            lateinit var fromBytes: KGEFont.Family
            lateinit var fromBase64: KGEFont.Family

            engine.onUpdateBody = {
                fromBytes = loadFont(robotoFontBytes(), robotoItalicBytes())
                fromBase64 = loadFontBase64(Roboto.romanFont, Roboto.italicFont)

                fromBytes.name shouldBe "Roboto"
                fromBase64.name shouldBe fromBytes.name
                fromBase64.faces.map { it.name } shouldBe fromBytes.faces.map { it.name }
                fromBase64.faces.map { it.monospaced } shouldBe fromBytes.faces.map { it.monospaced }
                fromBase64.faces[0].axes shouldBe fromBytes.faces[0].axes
            }
            runAddonFrame(engine)

            // the run's scope released the family: only its inert values answer
            fromBytes.name shouldBe "Roboto"
            shouldThrow<IllegalStateException> { fromBytes.faces }
            shouldThrow<IllegalStateException> { fromBytes.defaultFace }
            shouldThrow<IllegalStateException> { fromBase64.faces }
        }

        test("assigning textFont changes the default calls while a final font argument stays local") {
            val engine = AddonHost()
            engine.onUpdateBody = {
                val roboto = loadFont(robotoFontBytes()).defaultFace.font(resourceScope, SIZE_PX.fontPx)
                val mono = loadFont(robotoMonoBytes()).defaultFace.font(resourceScope, SIZE_PX.fontPx)

                val robotoBox = measureText("A", font = roboto)
                val monoBox = measureText("A", font = mono)
                robotoBox shouldBe Int2D(11, 19)
                monoBox shouldBe Int2D(10, 22)
                (robotoBox != monoBox) shouldBe true

                val target = checkNotNull(drawTarget)

                textFont = roboto
                measureText("A") shouldBe robotoBox
                target.clear(Colors.TRANSPARENT)
                drawText(Int2D(0, 0), "A")
                val robotoInk = target.snapshot()

                textFont = mono
                measureText("A") shouldBe monoBox
                target.clear(Colors.TRANSPARENT)
                drawText(Int2D(0, 0), "A")
                val monoInk = target.snapshot()
                (monoInk != robotoInk) shouldBe true

                // the final argument overrides that one call and leaves the principal alone
                measureText("A", font = roboto) shouldBe robotoBox
                target.clear(Colors.TRANSPARENT)
                drawText(Int2D(0, 0), "A", font = roboto)
                target.snapshot() shouldBe robotoInk
                textFont shouldBeSameInstanceAs mono
                measureText("A") shouldBe monoBox
            }
            runAddonFrame(engine)
        }

        test("two configured fonts alternate measured and drawn, each matching its own path") {
            val engine = AddonHost()
            engine.onUpdateBody = {
                val roboto = loadFont(robotoFontBytes()).defaultFace.font(resourceScope, SIZE_PX.fontPx)
                val mono = loadFont(robotoMonoBytes()).defaultFace.font(resourceScope, SIZE_PX.fontPx)
                val target = checkNotNull(drawTarget)

                textFont = roboto
                val robotoOwn = roboto.measureText("A", tabSizeInSpaces)
                val monoOwn = mono.measureText("A", tabSizeInSpaces)
                robotoOwn shouldBe Int2D(11, 19)
                monoOwn shouldBe Int2D(10, 22)

                // the calls alternate; the principal is never switched between them
                measureText("A") shouldBe robotoOwn
                measureText("A", font = mono) shouldBe monoOwn
                measureText("A") shouldBe robotoOwn
                measureText("A", font = mono) shouldBe monoOwn
                textFont shouldBeSameInstanceAs roboto

                target.clear(Colors.TRANSPARENT)
                drawText(Int2D(0, 0), "A", font = mono)
                val monoInk = target.snapshot()
                target.clear(Colors.TRANSPARENT)
                drawText(Int2D(0, 0), "A")
                val robotoInk = target.snapshot()
                (monoInk != robotoInk) shouldBe true

                target.clear(Colors.TRANSPARENT)
                drawText(Int2D(0, 0), "A", font = mono)
                target.snapshot() shouldBe monoInk
                target.clear(Colors.TRANSPARENT)
                drawText(Int2D(0, 0), "A")
                target.snapshot() shouldBe robotoInk

                // the font's own path reproduces the addon's ink exactly
                target.clear(Colors.TRANSPARENT)
                mono.drawText(target, 0, 0, "A", Colors.WHITE, 1, tabSizeInSpaces, pixelMode)
                target.snapshot() shouldBe monoInk
            }
            runAddonFrame(engine)
        }

        test("the Int2D and raw-coordinate drawText overloads render identically") {
            val engine = AddonHost()
            engine.onUpdateBody = {
                textFont = loadFont(robotoFontBytes()).defaultFace.font(resourceScope, SIZE_PX.fontPx)
                val target = checkNotNull(drawTarget)

                target.clear(Colors.TRANSPARENT)
                drawText(Int2D(3, 4), "A\tB", color = Colors.WHITE, scale = 2)
                val positioned = target.snapshot()

                target.clear(Colors.TRANSPARENT)
                drawText(3, 4, "A\tB", color = Colors.WHITE, scale = 2)
                val raw = target.snapshot()

                raw shouldBe positioned
                (positioned.any { it.a > 0 }) shouldBe true
            }
            runAddonFrame(engine)
        }

        test("drawTextDecal queues on the target layer with the host's window, mode and structure") {
            val engine = AddonHost()
            lateinit var queue: MutableList<DecalInstance>
            engine.onUpdateBody = {
                val font = loadFont(robotoFontBytes()).defaultFace.font(resourceScope, SIZE_PX.fontPx)
                textFont = font
                decalMode = Decal.Mode.ADDITIVE
                decalStructure = Decal.Structure.STRIP

                drawTextDecal(Float2D(2f, 3f), "A")
                queue = layers.target.decalInstances
                val instance = queue.single()

                val atWindow = font.collectDecal(Float2D(2f, 3f), window.screenSize, decalMode, decalStructure)
                val atOther = font.collectDecal(Float2D(2f, 3f), Int2D(30, 24), decalMode, decalStructure)

                instance.mode shouldBe Decal.Mode.ADDITIVE
                instance.structure shouldBe Decal.Structure.STRIP
                assertSameInstance(instance, atWindow.single())
                // the viewport is not inert: another one quantises the quad differently
                (instance.vertices.x(0) != atOther.single().vertices.x(0)) shouldBe true
            }
            runAddonFrame(engine)

            // the render step drained the very list the addon queued on
            queue shouldBe emptyList()
        }

        test("drawText with no draw target is a no-op and returns before consulting the font") {
            ResourceScope().use { scope ->
                val font =
                    KGETtfFontService
                        .createResources(scope, robotoFontBytes())
                        .defaultFace
                        .font(scope, SIZE_PX.fontPx)
                // the probe: a consultation of this font now fails fast
                font.close()
                shouldThrow<IllegalStateException> { font.measureText("A", TAB_SIZE) }

                val engine = AddonHost()
                engine.drawTarget shouldBe null

                engine.drawText(Int2D(0, 0), "A", font = font)
                engine.drawText(0, 0, "A", font = font)
            }
        }
    })

/** The addon over a headless engine; the callback body runs on the engine thread. */
private class AddonHost :
    Engine(WindowConfig(screenWidth = SCREEN_SIZE.x, screenHeight = SCREEN_SIZE.y)),
    TtfFontAddon {
    var onUpdateBody: suspend AddonHost.() -> Unit = {}

    override suspend fun onUserUpdate(elapsed: Duration): Boolean {
        onUpdateBody()
        return false
    }
}

/** Runs [engine] for one frame over the recording GL and driver, then restores every override. */
@OptIn(KGESensitiveAPI::class)
private suspend fun runAddonFrame(engine: AddonHost) {
    installGl()
    installDriver(RecordingDriver(scriptedFramebufferSize = engine.window.screenSize))
    try {
        engine.start()
    } finally {
        GLService.override(GLService.original)
        DriverService.override(DriverService.original)
    }
}

/** The surface's cells in reading order. */
private fun Pixmap.snapshot(): List<Pixel> {
    val pixels = mutableListOf<Pixel>()
    for (y in 0 until height) {
        for (x in 0 until width) {
            pixels += get(x, y)
        }
    }
    return pixels
}

/** Draws "A" through [this] at an explicit viewport and returns the instances in walk order. */
private fun KGEFont.collectDecal(
    position: Float2D,
    screenSize: Int2D,
    decalMode: Decal.Mode,
    decalStructure: Decal.Structure,
): List<DecalInstance> {
    val collected = mutableListOf<DecalInstance>()
    drawTextDecal(
        position,
        "A",
        Colors.WHITE,
        Float2D(1f, 1f),
        TAB_SIZE,
        screenSize,
        decalMode,
        decalStructure,
        collected::add,
    )
    return collected
}

/** The caller's parameters and the full vertex set, not just the anchor. */
private fun assertSameInstance(
    actual: DecalInstance,
    expected: DecalInstance,
) {
    actual.decal shouldBe expected.decal
    actual.mode shouldBe expected.mode
    actual.structure shouldBe expected.structure
    actual.vertexCount shouldBe expected.vertexCount
    for (index in 0 until expected.vertexCount) {
        actual.vertices.x(index) shouldBe expected.vertices.x(index)
        actual.vertices.y(index) shouldBe expected.vertices.y(index)
        actual.vertices.u(index) shouldBe expected.vertices.u(index)
        actual.vertices.v(index) shouldBe expected.vertices.v(index)
        actual.vertices.tint(index) shouldBe expected.vertices.tint(index)
    }
}
