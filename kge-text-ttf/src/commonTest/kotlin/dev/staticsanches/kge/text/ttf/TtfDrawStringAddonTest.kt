package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.font.roboto.Roboto
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.resource.applyClosingIfFailed
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

/**
 * The addon over a GL-free host: both draw forms forward the host's target,
 * tab size and pixel mode, and a null target is a no-op.
 */
@OptIn(KGESensitiveAPI::class)
class TtfDrawStringAddonTest :
    FunSpec({
        test("the tab size default is olc's four spaces") {
            TestAddonHost().tabSizeInSpaces shouldBe 4
        }

        test("getTextSize measures with the host's tab size") {
            Font.load(Roboto.variableFont).use { font ->
                // a one-space stop is 3.96875: A ends at 10.4375, so B starts at 11.90625
                TestAddonHost(tabSizeInSpaces = 1).getTextSize(font, "A\tB", 16) shouldBe Int2D(22, 19)
                TestAddonHost().getTextSize(font, "A\tB", 16) shouldBe Int2D(26, 19)
            }
        }

        test("a null target is a no-op and both forms forward the host's settings") {
            val font = Font.load(Roboto.variableFont)
            val recorded = mutableListOf<RecordedDraw>()
            TtfTextService.override(RecordingTtfTextService(recorded))
            try {
                val host = TestAddonHost(tabSizeInSpaces = 1)
                val rawColor = Pixel.rgba(10, 20, 30, 40)
                val positionedColor = Pixel.rgba(50, 60, 70, 80)

                host.drawString(font, 0, 0, "A", 16, color = rawColor, scale = 3)
                host.drawString(font, Int2D(2, 3), "A", 16, color = positionedColor, scale = 2)
                recorded shouldBe emptyList()

                val target = emptyTarget()
                try {
                    host.drawTarget = target
                    host.drawString(font, 0, 0, "A", 16, color = rawColor, scale = 3)
                    host.drawString(font, Int2D(2, 3), "A", 16, color = positionedColor, scale = 2)

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
                } finally {
                    target.close()
                }
            } finally {
                // kge-core resets overrides between its own tests only, so this
                // module restores the engine default itself.
                TtfTextService.override(TtfTextService.original)
                font.close()
            }
        }

        test("the host's pixel mode drives a real draw") {
            Font.load(Roboto.variableFont).use { font ->
                val shaped = font.shape("A", 16).glyphs.single()
                val placed = font.glyph(16, shaped.glyphId) as AtlasGlyph.Placed
                val host = TestAddonHost()
                host.pixelMode =
                    object : Pixel.Mode.Custom {
                        override fun apply(
                            x: Int,
                            y: Int,
                            newPixel: Pixel,
                            oldPixel: Pixel,
                        ): Pixel = Colors.RED
                    }

                val target = emptyTarget()
                try {
                    host.drawTarget = target
                    host.drawString(font, 0, 0, "A", 16)

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
                } finally {
                    target.close()
                }
            }
        }
    })

/** A cleared addon surface; a failed construction closes it. */
private fun emptyTarget(): Sprite =
    SpriteService
        .create(32, 24, Pixmap.SampleMode.NORMAL, "addon test")
        .applyClosingIfFailed { clear(Colors.TRANSPARENT) }

/** The addon over a host with no window, no layers and no GL context. */
private class TestAddonHost(
    override var tabSizeInSpaces: Int = 4,
) : TtfDrawStringAddon {
    override var drawTarget: Sprite? = null

    override var pixelMode: Pixel.Mode = Pixel.Mode.Normal

    override var decalMode: Decal.Mode = Decal.Mode.NORMAL

    override var decalStructure: Decal.Structure = Decal.Structure.FAN

    override var suspendTextureTransfer: Boolean = false

    override fun setDrawTarget(
        index: Int,
        dirty: Boolean,
    ) = error("the addon test selects no layer")
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
