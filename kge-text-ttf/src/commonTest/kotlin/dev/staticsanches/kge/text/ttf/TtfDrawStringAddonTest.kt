package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.DriverService
import dev.staticsanches.kge.engine.Engine
import dev.staticsanches.kge.engine.WindowConfig
import dev.staticsanches.kge.font.roboto.Roboto
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.service.GLService
import dev.staticsanches.kge.resource.applyClosingIfFailed
import dev.staticsanches.kge.testsupport.engine.RecordingDriver
import dev.staticsanches.kge.testsupport.engine.installDriver
import dev.staticsanches.kge.testsupport.engine.installGl
import dev.staticsanches.kge.testsupport.gl.RecordingGLService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlin.time.Duration

/**
 * The addon over a headless [Engine] host: the CPU forms forward the host's
 * target, tab size and pixel mode, a null target is a no-op, and the decal form
 * queues on the engine's layer for the render step to flush.
 */
@OptIn(KGESensitiveAPI::class)
class TtfDrawStringAddonTest :
    FunSpec({
        test("the tab size default is olc's four spaces") {
            AddonEngine().tabSizeInSpaces shouldBe 4
        }

        test("getTextSize measures with the host's tab size") {
            Font.load(Roboto.variableFont).use { font ->
                // a one-space stop is 3.96875: A ends at 10.4375, so B starts at 11.90625
                AddonEngine(tabSizeInSpaces = 1).getTextSize(font, "A\tB", 16) shouldBe Int2D(22, 19)
                AddonEngine().getTextSize(font, "A\tB", 16) shouldBe Int2D(26, 19)
            }
        }

        test("a null target is a no-op and both forms forward the host's settings") {
            val font = Font.load(Roboto.variableFont)
            val recorded = mutableListOf<RecordedDraw>()
            TtfTextService.override(RecordingTtfTextService(recorded))
            try {
                val host = AddonEngine(tabSizeInSpaces = 1)
                val rawColor = Pixel.rgba(10, 20, 30, 40)
                val positionedColor = Pixel.rgba(50, 60, 70, 80)

                // before the loop the host owns no draw target
                host.drawString(font, 0, 0, "A", 16, color = rawColor, scale = 3)
                host.drawString(font, Int2D(2, 3), "A", 16, color = positionedColor, scale = 2)
                recorded shouldBe emptyList()

                val target = emptyTarget()
                try {
                    host.onUpdateBody = { e ->
                        e.drawTarget = target
                        e.drawString(font, 0, 0, "A", 16, color = rawColor, scale = 3)
                        e.drawString(font, Int2D(2, 3), "A", 16, color = positionedColor, scale = 2)
                    }
                    withAddonFrame(font, host) {
                        recorded.size shouldBe 2
                        recorded[0].target shouldBeSameInstanceAs target
                        recorded[0].x shouldBe 0
                        recorded[0].y shouldBe 0
                        recorded[0].text shouldBe "A"
                        recorded[0].sizePx shouldBe 16
                        recorded[0].color shouldBe rawColor
                        recorded[0].scale shouldBe 3
                        recorded[0].tabSizeInSpaces shouldBe 1
                        recorded[0].mode shouldBeSameInstanceAs host.pixelMode
                        recorded[1].x shouldBe 2
                        recorded[1].y shouldBe 3
                        recorded[1].color shouldBe positionedColor
                        recorded[1].scale shouldBe 2
                    }
                } finally {
                    target.close()
                }
            } finally {
                // kge-core resets overrides between its own tests only, so this
                // module restores the engine default itself.
                TtfTextService.override(TtfTextService.original)
            }
        }

        test("the host's pixel mode drives a real draw") {
            val font = Font.load(Roboto.variableFont)
            val shaped = font.shape("A", 16).glyphs.single()
            val placed = font.glyph(16, shaped.glyphId) as AtlasGlyph.Placed
            val host = AddonEngine()
            val target = emptyTarget()
            try {
                host.onUpdateBody = { e ->
                    e.pixelMode =
                        object : Pixel.Mode.Custom {
                            override fun apply(
                                x: Int,
                                y: Int,
                                newPixel: Pixel,
                                oldPixel: Pixel,
                            ): Pixel = Colors.RED
                        }
                    e.drawTarget = target
                    e.drawString(font, 0, 0, "A", 16)
                }
                withAddonFrame(font, host) {
                    // the mode is tapped for the ink cells only; the rest keeps the cleared target
                    val chart = font.atlas(16)!!.charts[placed.chartIndex]
                    var painted = 0
                    var blank = 0
                    for (y in 0 until target.height) {
                        for (x in 0 until target.width) {
                            val inside = x < placed.size.x && y >= 3 && y < 3 + placed.size.y
                            val coverage =
                                if (inside) chart.get(placed.source.x + x, placed.source.y + y - 3).a else 0
                            if (coverage > 0) {
                                target.get(x, y) shouldBe Colors.RED
                                painted++
                            } else {
                                target.get(x, y) shouldBe Colors.TRANSPARENT
                                if (inside) blank++
                            }
                        }
                    }
                    painted + blank shouldBe placed.size.x * placed.size.y
                    (blank > 0) shouldBe true
                }
            } finally {
                target.close()
            }
        }

        test("drawStringDecal queues one instance per ink glyph with the window viewport and the host's modes") {
            val font = Font.load(Roboto.variableFont)
            val engine = AddonEngine()
            var queued: List<DecalInstance> = emptyList()
            engine.onUpdateBody = { e ->
                e.decalMode = Decal.Mode.ADDITIVE
                e.decalStructure = Decal.Structure.STRIP
                e.drawStringDecal(font, Float2D(2f, 3f), "A", 16)
                // captured before the render step flushes the layer's queue
                val layer = e.layers.target
                queued = layer.decalInstances.toList()
            }
            withAddonFrame(font, engine) {
                queued.size shouldBe 1
                val instance = queued.single()
                instance.mode shouldBe Decal.Mode.ADDITIVE
                instance.structure shouldBe Decal.Structure.STRIP
                assertSameGeometry(
                    instance,
                    expectedInstance(
                        font,
                        "A",
                        Float2D(2f, 5.84375f),
                        screenSize = engine.window.screenSize,
                        decalMode = Decal.Mode.ADDITIVE,
                        decalStructure = Decal.Structure.STRIP,
                    ),
                )
                // the viewport is the window's: another one quantises the quad differently
                val otherViewport =
                    expectedInstance(
                        font,
                        "A",
                        Float2D(2f, 5.84375f),
                        screenSize = Int2D(320, 240),
                        decalMode = Decal.Mode.ADDITIVE,
                        decalStructure = Decal.Structure.STRIP,
                    )
                (instance.vertices.x(0) != otherViewport.vertices.x(0)) shouldBe true
            }
        }

        test("the render step draws one call per queued instance and empties the queue") {
            val font = Font.load(Roboto.variableFont)
            val engine = AddonEngine()
            var queued = 0
            var queueEmptied = false
            engine.onUpdateBody = { e ->
                e.drawStringDecal(font, Float2D(2f, 3f), "AV", 16)
                queued = e.layers.target.decalInstances.size
            }
            engine.onDestroyBody = { e -> queueEmptied = e.layers[0].decalInstances.isEmpty() }
            withAddonFrame(font, engine) { gl ->
                queued shouldBe 2
                val draws = gl.calls.filter { it.name == "drawArrays" }
                draws.first().arguments shouldBe listOf(GL.TRIANGLE_STRIP, 0, 4)
                draws.drop(1).size shouldBe queued
                queueEmptied shouldBe true
            }
        }

        test("omitting the color and scale queues the same instance as the explicit white and unit scale") {
            val font = Font.load(Roboto.variableFont)
            val engine = AddonEngine()
            var queued: List<DecalInstance> = emptyList()
            engine.onUpdateBody = { e ->
                e.drawStringDecal(font, Float2D(2f, 3f), "A", 16)
                e.drawStringDecal(font, Float2D(2f, 3f), "A", 16, Colors.WHITE, Float2D(1f, 1f))
                val layer = e.layers.target
                queued = layer.decalInstances.toList()
            }
            withAddonFrame(font, engine) {
                queued.size shouldBe 2
                assertSameGeometry(queued[0], queued[1])
                queued[0].vertices.tint(0) shouldBe Colors.WHITE
            }
        }

        test("the addon's decal form forwards a non-default color, scale and sizePx") {
            val font = Font.load(Roboto.variableFont)
            val engine = AddonEngine()
            val tint = Pixel.rgba(10, 20, 30, 40)
            val scale = Float2D(2f, 3f)
            var queued: List<DecalInstance> = emptyList()
            engine.onUpdateBody = { e ->
                e.drawStringDecal(font, Float2D(2f, 3f), "A", 32, color = tint, scale = scale)
                val layer = e.layers.target
                queued = layer.decalInstances.toList()
            }
            withAddonFrame(font, engine) {
                val instance = queued.single()
                instance.vertices.tint(0) shouldBe tint
                // round D: "A" at 32 px is 21x23 at bearing (0, -23); round C scales the
                // 16 px ascender 14.84375 by two, so y = 3 + (29.6875 - 23) * 3 = 23.0625
                assertSameGeometry(
                    instance,
                    expectedInstance(
                        font,
                        "A",
                        Float2D(2f, 23.0625f),
                        sizePx = 32,
                        scale = scale,
                        color = tint,
                        screenSize = engine.window.screenSize,
                    ),
                )
            }
        }

        test("the addon's queue is the layer's list the render step drains") {
            val font = Font.load(Roboto.variableFont)
            val engine = AddonEngine()
            lateinit var queue: MutableList<DecalInstance>
            var queuedVertexCount = 0
            engine.onUpdateBody = { e ->
                e.drawStringDecal(font, Float2D(2f, 3f), "A", 16)
                queue = e.layers.target.decalInstances
                queuedVertexCount = queue.single().vertexCount
            }
            withAddonFrame(font, engine) { gl ->
                // the addon's instance reached the render step, which drained that very list
                queue shouldBe emptyList()
                gl.calls
                    .filter { it.name == "drawArrays" }
                    .drop(1)
                    .single()
                    .arguments shouldBe listOf(GL.TRIANGLE_FAN, 0, queuedVertexCount)
            }
        }
    })

/** A cleared addon surface; a failed construction closes it. */
private fun emptyTarget(): Sprite =
    SpriteService
        .create(32, 24, Pixmap.SampleMode.NORMAL, "addon test")
        .applyClosingIfFailed { clear(Colors.TRANSPARENT) }

/** The addon over a headless engine; `window` and `layers` are the engine's own. */
private class AddonEngine(
    screenSize: Int2D = Int2D(30, 24),
    override var tabSizeInSpaces: Int = 4,
) : Engine(WindowConfig(screenWidth = screenSize.x, screenHeight = screenSize.y)),
    TtfDrawStringAddon {
    var onUpdateBody: (AddonEngine) -> Unit = {}

    var onDestroyBody: (AddonEngine) -> Unit = {}

    override suspend fun onUserUpdate(elapsed: Duration): Boolean {
        onUpdateBody(this)
        return false
    }

    override suspend fun onUserDestroy(): Boolean {
        onDestroyBody(this)
        return true
    }
}

/**
 * Runs [engine] for one frame over the recording GL and driver, then closes
 * [font] while that recorder is still installed and restores every process-wide
 * override.
 */
@OptIn(KGESensitiveAPI::class)
private suspend fun withAddonFrame(
    font: Font,
    engine: AddonEngine,
    assert: (RecordingGLService) -> Unit,
) {
    val gl = installGl()
    installDriver(RecordingDriver(scriptedFramebufferSize = engine.window.screenSize))
    try {
        engine.start()
        GLService.override(gl)
        assert(gl)
    } finally {
        try {
            GLService.override(gl)
            font.close()
        } finally {
            GLService.override(GLService.original)
            DriverService.override(DriverService.original)
            TtfTextService.override(TtfTextService.original)
        }
    }
}

/** Records the service calls the addon makes and forwards everything else. */
private class RecordingTtfTextService(
    private val recorded: MutableList<RecordedDraw>,
) : TtfTextService by TtfTextService.original {
    override fun drawString(
        font: Font,
        target: Pixmap.Mutable,
        x: Int,
        y: Int,
        text: String,
        sizePx: Int,
        color: Pixel,
        scale: Int,
        tabSizeInSpaces: Int,
        mode: Pixel.Mode,
    ) {
        recorded += RecordedDraw(target, x, y, text, sizePx, color, scale, tabSizeInSpaces, mode)
    }
}

private class RecordedDraw(
    val target: Pixmap.Mutable,
    val x: Int,
    val y: Int,
    val text: String,
    val sizePx: Int,
    val color: Pixel,
    val scale: Int,
    val tabSizeInSpaces: Int,
    val mode: Pixel.Mode,
)
