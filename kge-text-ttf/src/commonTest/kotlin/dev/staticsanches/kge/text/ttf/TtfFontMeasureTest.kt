package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.renderer.gl.service.GLService
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.testsupport.engine.installGl
import dev.staticsanches.kge.testsupport.golden.canvas
import dev.staticsanches.kge.text.KGEFont
import dev.staticsanches.kge.text.axisValue
import dev.staticsanches.kge.text.fontPx
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val TAB_SIZE = 4
private const val SIZE_PX = 16

/**
 * The configured TrueType font: the canonical axis map, the pinned measurement
 * boxes, the per-lease lifecycle and the two draws over the same face.
 */
@OptIn(KGESensitiveAPI::class)
class TtfFontMeasureTest :
    FunSpec({
        suspend fun robotoFace(scope: ResourceScope): KGEFont.Face =
            KGETtfFontService.createResources(scope, robotoFontBytes(), robotoItalicBytes()).defaultFace

        suspend fun configured(
            scope: ResourceScope,
            axes: Map<KGEFont.Axis.Tag, KGEFont.Axis.Value> = emptyMap(),
        ): KGEFont = robotoFace(scope).font(scope, SIZE_PX.fontPx, axes)

        test("the canonical function fills the defaults in ascending tag order") {
            val canonical = canonicalAxisCoordinates(ROBOTO_AXES, mapOf(KGEFont.Axis.Tag.Weight to 900.axisValue))

            canonical.keys.toList() shouldBe listOf(KGEFont.Axis.Tag.Width, KGEFont.Axis.Tag.Weight)
            canonical.getValue(KGEFont.Axis.Tag.Weight) shouldBe 900.axisValue
            canonical.getValue(KGEFont.Axis.Tag.Width) shouldBe 100.axisValue
        }

        test("an unknown tag or an out-of-range value is rejected") {
            shouldThrow<IllegalArgumentException> {
                canonicalAxisCoordinates(ROBOTO_AXES, mapOf(KGEFont.Axis.Tag.Slant to 0.axisValue))
            }
            shouldThrow<IllegalArgumentException> {
                canonicalAxisCoordinates(ROBOTO_AXES, mapOf(KGEFont.Axis.Tag.Weight to 99.axisValue))
            }
            shouldThrow<IllegalArgumentException> {
                canonicalAxisCoordinates(ROBOTO_AXES, mapOf(KGEFont.Axis.Tag.Weight to 901.axisValue))
            }
            shouldThrow<IllegalArgumentException> {
                canonicalAxisCoordinates(ROBOTO_AXES, mapOf(KGEFont.Axis.Tag.Width to 101.axisValue))
            }

            // the range's own endpoints are inside it
            canonicalAxisCoordinates(ROBOTO_AXES, mapOf(KGEFont.Axis.Tag.Weight to 100.axisValue))
                .getValue(KGEFont.Axis.Tag.Weight) shouldBe 100.axisValue
            canonicalAxisCoordinates(ROBOTO_AXES, mapOf(KGEFont.Axis.Tag.Weight to 900.axisValue))
                .getValue(KGEFont.Axis.Tag.Weight) shouldBe 900.axisValue
        }

        test("an axisless descriptor map accepts only an empty request") {
            canonicalAxisCoordinates(emptyList(), emptyMap()) shouldBe emptyMap()
            shouldThrow<IllegalArgumentException> {
                canonicalAxisCoordinates(emptyList(), mapOf(KGEFont.Axis.Tag.Weight to 400.axisValue))
            }
        }

        test("the configured font's canonical map fills the defaults in ascending tag order") {
            ResourceScope().use { scope ->
                val font = configured(scope, mapOf(KGEFont.Axis.Tag.Weight to 900.axisValue))

                font.axisCoordinates.keys.toList() shouldBe listOf(KGEFont.Axis.Tag.Width, KGEFont.Axis.Tag.Weight)
                font.axisCoordinates.getValue(KGEFont.Axis.Tag.Weight) shouldBe 900.axisValue
                font.axisCoordinates.getValue(KGEFont.Axis.Tag.Width) shouldBe 100.axisValue
            }
        }

        test("a differently ordered request produces an equal canonical map") {
            ResourceScope().use { scope ->
                val face = robotoFace(scope)
                val first =
                    face.font(
                        scope,
                        SIZE_PX.fontPx,
                        mapOf(KGEFont.Axis.Tag.Weight to 900.axisValue, KGEFont.Axis.Tag.Width to 75.axisValue),
                    )
                val second =
                    face.font(
                        scope,
                        SIZE_PX.fontPx,
                        mapOf(KGEFont.Axis.Tag.Width to 75.axisValue, KGEFont.Axis.Tag.Weight to 900.axisValue),
                    )

                first.axisCoordinates shouldBe second.axisCoordinates
                first.axisCoordinates.keys.toList() shouldBe listOf(KGEFont.Axis.Tag.Width, KGEFont.Axis.Tag.Weight)
            }
        }

        test("a request outside the descriptor map or its range is rejected") {
            ResourceScope().use { scope ->
                val face = robotoFace(scope)

                shouldThrow<IllegalArgumentException> {
                    face.font(scope, SIZE_PX.fontPx, mapOf(KGEFont.Axis.Tag.Slant to 0.axisValue))
                }
                shouldThrow<IllegalArgumentException> {
                    face.font(scope, SIZE_PX.fontPx, mapOf(KGEFont.Axis.Tag.Weight to 901.axisValue))
                }
                shouldThrow<IllegalArgumentException> {
                    face.font(scope, SIZE_PX.fontPx, mapOf(KGEFont.Axis.Tag.Width to 74.axisValue))
                }
            }
        }

        test("the empty string measures (0, 0)") {
            ResourceScope().use { scope ->
                configured(scope).measureText("", TAB_SIZE) shouldBe Int2D(0, 0)
            }
        }

        test("the pinned 16 px boxes reproduce through the configured font") {
            ResourceScope().use { scope ->
                val font = configured(scope)

                font.measureText("A", TAB_SIZE) shouldBe Int2D(11, 19)
                font.measureText(" ", TAB_SIZE) shouldBe Int2D(4, 19)
                font.measureText("AAAA", TAB_SIZE) shouldBe Int2D(42, 19)
                font.measureText("A\nB", TAB_SIZE) shouldBe Int2D(11, 38)
                font.measureText("A\tB", TAB_SIZE) shouldBe Int2D(26, 19)
                font.measureText("AAAA\tB", TAB_SIZE) shouldBe Int2D(58, 19)
            }
        }

        test("a wght 900 lease measures wider than its 400 twin") {
            ResourceScope().use { scope ->
                val face = robotoFace(scope)
                val regular = face.font(scope, SIZE_PX.fontPx, mapOf(KGEFont.Axis.Tag.Weight to 400.axisValue))
                val bold = face.font(scope, SIZE_PX.fontPx, mapOf(KGEFont.Axis.Tag.Weight to 900.axisValue))

                regular.measureText("AAAA", TAB_SIZE) shouldBe Int2D(42, 19)
                (bold.measureText("AAAA", TAB_SIZE).x > regular.measureText("AAAA", TAB_SIZE).x) shouldBe true
            }
        }

        test("a closed lease keeps its inert values and fails its handles") {
            ResourceScope().use { scope ->
                val font = configured(scope, mapOf(KGEFont.Axis.Tag.Weight to 900.axisValue))

                font.close()

                font.size.px shouldBe SIZE_PX
                font.axisCoordinates.getValue(KGEFont.Axis.Tag.Weight) shouldBe 900.axisValue
                shouldThrow<IllegalStateException> { font.face }
                shouldThrow<IllegalStateException> { font.family }
                shouldThrow<IllegalStateException> { font.measureText("A", TAB_SIZE) }
                canvas(8, 8).use { target ->
                    shouldThrow<IllegalStateException> {
                        font.drawText(target, 0, 0, "A", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                    }
                }
                shouldThrow<IllegalStateException> {
                    font.drawTextDecal(
                        Float2D(0f, 0f),
                        "A",
                        Colors.WHITE,
                        Float2D(1f, 1f),
                        TAB_SIZE,
                        Int2D(8, 8),
                        Decal.Mode.NORMAL,
                        Decal.Structure.FAN,
                    ) {}
                }
            }
        }

        test("closing one lease leaves an equal lease usable") {
            ResourceScope().use { scope ->
                val face = robotoFace(scope)
                val closed = face.font(scope, SIZE_PX.fontPx)
                val sibling = face.font(scope, SIZE_PX.fontPx)

                closed.close()

                sibling.size.px shouldBe SIZE_PX
                (sibling.face === face) shouldBe true
                sibling.measureText("A", TAB_SIZE) shouldBe Int2D(11, 19)
            }
        }

        test("drawText paints ink on the pixels measureText promises") {
            ResourceScope().use { scope ->
                val font = configured(scope)
                val box = font.measureText("A", TAB_SIZE)

                canvas(box.x + 4, box.y + 4).use { target ->
                    font.drawText(target, 0, 0, "A", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)

                    var ink = 0
                    var maxX = -1
                    var maxY = -1
                    for (y in 0 until target.height) {
                        for (x in 0 until target.width) {
                            val alpha = target.get(x, y).a
                            if (alpha > 0) {
                                ink += alpha
                                if (x > maxX) maxX = x
                                if (y > maxY) maxY = y
                            }
                        }
                    }
                    // the legacy suite's own pin for "A" at 16 px
                    ink shouldBe 9983
                    (maxX < box.x) shouldBe true
                    (maxY < box.y) shouldBe true
                }
            }
        }

        test("drawTextDecal queues one instance per ink glyph") {
            installGl()
            try {
                ResourceScope().use { scope ->
                    val font = configured(scope)
                    val collected = mutableListOf<DecalInstance>()

                    font.drawTextDecal(
                        Float2D(2f, 3f),
                        "AV",
                        Colors.WHITE,
                        Float2D(1f, 1f),
                        TAB_SIZE,
                        Int2D(30, 24),
                        Decal.Mode.NORMAL,
                        Decal.Structure.FAN,
                        collected::add,
                    )

                    collected.size shouldBe 2
                }
            } finally {
                GLService.override(GLService.original)
            }
        }

        test("the 32 px box and the widest line on either side") {
            ResourceScope().use { scope ->
                val face = robotoFace(scope)

                face.font(scope, 32.fontPx).measureText("A", TAB_SIZE) shouldBe Int2D(21, 38)

                val font = face.font(scope, SIZE_PX.fontPx)
                font.measureText("AAAA\nA", TAB_SIZE) shouldBe Int2D(42, 38)
                font.measureText("A\nAAAA", TAB_SIZE) shouldBe Int2D(42, 38)
            }
        }

        test("a trailing space advances the pen and kerning narrows the pair") {
            ResourceScope().use { scope ->
                val font = configured(scope)

                font.measureText("A ", TAB_SIZE) shouldBe Int2D(15, 19)
                font.measureText("AV", TAB_SIZE) shouldBe Int2D(20, 19)
                font.measureText("AA", TAB_SIZE) shouldBe Int2D(21, 19)
            }
        }

        test("blank and empty draws paint nothing while a trailing space changes no pixel") {
            ResourceScope().use { scope ->
                val font = configured(scope)

                canvas(32, 24).use { blank ->
                    font.drawText(blank, 0, 0, " ", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                    font.drawText(blank, 0, 0, "", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)

                    blank.alphaSum() shouldBe 0
                }

                canvas(32, 24).use { spaced ->
                    canvas(32, 24).use { plain ->
                        font.drawText(spaced, 0, 0, "A ", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                        font.drawText(plain, 0, 0, "A", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)

                        spaced.pixels() shouldBe plain.pixels()
                        spaced.alphaSum() shouldBe 9983
                    }
                }
            }
        }

        test("a non-positive tab size fails fast on measure and on draw") {
            ResourceScope().use { scope ->
                val font = configured(scope)

                for (tabSize in listOf(0, -1)) {
                    shouldThrow<IllegalStateException> { font.measureText("A", tabSize) }
                    canvas(32, 24).use { target ->
                        shouldThrow<IllegalStateException> {
                            font.drawText(target, 0, 0, "A", Colors.WHITE, 1, tabSize, Pixel.Mode.Normal)
                        }
                    }
                }
            }
        }

        test("measuring never rasterizes") {
            val gl = installGl()
            try {
                ResourceScope().use { scope ->
                    val font = configured(scope)
                    val allocations = mutableListOf<ResourceWrapper<ByteBuffer>>()
                    BufferService.override(recordingAllocations(allocations))
                    try {
                        gl.clear()

                        font.measureText("AV", TAB_SIZE) shouldBe Int2D(20, 19)

                        gl.calls shouldBe emptyList()
                        allocations shouldBe emptyList()
                    } finally {
                        // kge-core resets overrides between its own tests only, so this
                        // module restores the engine default itself.
                        BufferService.override(BufferService.original)
                    }
                }
            } finally {
                GLService.override(GLService.original)
            }
        }
    })

/** The surface's cells in reading order. */
private fun Pixmap.pixels(): List<Pixel> {
    val pixels = mutableListOf<Pixel>()
    for (y in 0 until height) {
        for (x in 0 until width) {
            pixels += get(x, y)
        }
    }
    return pixels
}

private fun Pixmap.alphaSum(): Int = pixels().sumOf { it.a }

/** Records every engine buffer the module allocates while the override is active. */
private fun recordingAllocations(allocations: MutableList<ResourceWrapper<ByteBuffer>>): BufferService =
    object : BufferService {
        override fun allocate(
            sizeInBytes: Int,
            name: String?,
        ): ResourceWrapper<ByteBuffer> = BufferService.original.allocate(sizeInBytes, name).also { allocations += it }
    }

/** Roboto's descriptors in `fvar` record order, as the reader reports them. */
private val ROBOTO_AXES: List<KGEFont.Axis> =
    listOf(
        KGEFont.Axis(
            tag = KGEFont.Axis.Tag.Weight,
            name = "Weight",
            min = 100.axisValue,
            default = 400.axisValue,
            max = 900.axisValue,
            hidden = false,
        ),
        KGEFont.Axis(
            tag = KGEFont.Axis.Tag.Width,
            name = "Width",
            min = 75.axisValue,
            default = 100.axisValue,
            max = 100.axisValue,
            hidden = false,
        ),
    )
