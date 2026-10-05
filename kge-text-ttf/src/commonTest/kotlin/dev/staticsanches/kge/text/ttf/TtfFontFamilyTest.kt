package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.font.roboto.Roboto
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.text.KGEFont
import dev.staticsanches.kge.text.axisValue
import dev.staticsanches.kge.text.fontPx
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

/**
 * The loaded TrueType family: payload order is face order, identity comes from
 * the name table, the load is atomic and the family owns one payload per face.
 */
@OptIn(KGESensitiveAPI::class)
class TtfFontFamilyTest :
    FunSpec({
        test("two payloads load as one family in payload order") {
            ResourceScope().use { scope ->
                val family = KGETtfFontService.createResources(scope, robotoFontBytes(), robotoItalicBytes())

                family.name shouldBe "Roboto"
                family.faces.map { it.name } shouldBe listOf("Regular", "Italic")
                family.defaultFace shouldBeSameInstanceAs family.faces[0]
                family.faces.forEach { face ->
                    face.monospaced shouldBe false
                    face.family shouldBeSameInstanceAs family
                }
            }
        }

        test("the face axes iterate in fvar order with the payload's ranges") {
            ResourceScope().use { scope ->
                val family = KGETtfFontService.createResources(scope, robotoFontBytes(), robotoItalicBytes())
                val axes = family.faces[0].axes

                axes.keys.toList() shouldBe listOf(KGEFont.Axis.Tag.Weight, KGEFont.Axis.Tag.Width)

                val weight = axes.getValue(KGEFont.Axis.Tag.Weight)
                weight.name shouldBe "Weight"
                weight.min shouldBe 100.axisValue
                weight.default shouldBe 400.axisValue
                weight.max shouldBe 900.axisValue
                weight.hidden shouldBe false

                val width = axes.getValue(KGEFont.Axis.Tag.Width)
                width.name shouldBe "Width"
                width.min shouldBe 75.axisValue
                width.default shouldBe 100.axisValue
                width.max shouldBe 100.axisValue
                width.hidden shouldBe false

                family.faces[1].axes shouldBe axes
            }
        }

        test("a loaded family's faces are not a mutable list") {
            ResourceScope().use { scope ->
                val family = KGETtfFontService.createResources(scope, robotoFontBytes(), robotoItalicBytes())

                (family.faces is MutableList<*>) shouldBe false
            }
        }

        test("a loaded face's axes are not a mutable map") {
            ResourceScope().use { scope ->
                val family = KGETtfFontService.createResources(scope, robotoFontBytes(), robotoItalicBytes())

                (family.defaultFace.axes is MutableMap<*, *>) shouldBe false
            }
        }

        test("a configured font's axis coordinates are not a mutable map") {
            ResourceScope().use { scope ->
                val family = KGETtfFontService.createResources(scope, robotoFontBytes(), robotoItalicBytes())
                val font = family.defaultFace.font(scope, 16.fontPx)

                (font.axisCoordinates is MutableMap<*, *>) shouldBe false
            }
        }

        test("Roboto Mono reports one weight axis and both faces monospaced") {
            ResourceScope().use { scope ->
                val family = KGETtfFontService.createResources(scope, robotoMonoBytes(), robotoMonoItalicBytes())

                family.name shouldBe "Roboto Mono"
                family.faces.map { it.name } shouldBe listOf("Regular", "Italic")
                family.faces.forEach { it.monospaced shouldBe true }

                val axes = family.faces[0].axes
                axes.keys.toList() shouldBe listOf(KGEFont.Axis.Tag.Weight)
                val weight = axes.getValue(KGEFont.Axis.Tag.Weight)
                weight.min shouldBe 100.axisValue
                weight.default shouldBe 400.axisValue
                weight.max shouldBe 700.axisValue
                weight.hidden shouldBe false
            }
        }

        test("reversing the payload order moves the default face") {
            ResourceScope().use { scope ->
                val family = KGETtfFontService.createResources(scope, robotoItalicBytes(), robotoFontBytes())

                family.faces.map { it.name } shouldBe listOf("Italic", "Regular")
                family.defaultFace.name shouldBe "Italic"
                family.defaultFace shouldBeSameInstanceAs family.faces[0]
            }
        }

        test("the base64 overload loads the same family as the bytes overload") {
            ResourceScope().use { scope ->
                val fromBytes = KGETtfFontService.createResources(scope, robotoFontBytes(), robotoItalicBytes())
                val fromBase64 = KGETtfFontService.createResources(scope, Roboto.romanFont, Roboto.italicFont)

                fromBase64.name shouldBe fromBytes.name
                fromBase64.faces.map { it.name } shouldBe fromBytes.faces.map { it.name }
                fromBase64.faces.map { it.monospaced } shouldBe fromBytes.faces.map { it.monospaced }
                fromBase64.faces[0].axes shouldBe fromBytes.faces[0].axes
            }
        }

        test("two loaded families coexist in one scope") {
            ResourceScope().use { scope ->
                val roman = KGETtfFontService.createResources(scope, robotoFontBytes())
                val italic = KGETtfFontService.createResources(scope, robotoItalicBytes())

                roman.faces.map { it.name } shouldBe listOf("Regular")
                italic.faces.map { it.name } shouldBe listOf("Italic")
                roman.defaultFace shouldBeSameInstanceAs roman.faces[0]
                italic.defaultFace shouldBeSameInstanceAs italic.faces[0]
            }
        }

        test("payloads from different families are rejected") {
            ResourceScope().use { scope ->
                shouldThrow<IllegalArgumentException> {
                    KGETtfFontService.createResources(scope, robotoFontBytes(), robotoMonoBytes())
                }
            }
        }

        test("two payloads with the same subfamily name are rejected") {
            ResourceScope().use { scope ->
                shouldThrow<IllegalArgumentException> {
                    KGETtfFontService.createResources(scope, robotoFontBytes(), robotoFontBytes())
                }
            }
        }

        test("a payload that is not an sfnt face is rejected") {
            ResourceScope().use { scope ->
                shouldThrow<IllegalArgumentException> {
                    KGETtfFontService.createResources(scope, robotoFontBytes(), ByteArray(128) { (it * 31).toByte() })
                }
            }
        }

        test("an empty payload list is rejected") {
            ResourceScope().use { scope ->
                shouldThrow<IllegalArgumentException> {
                    KGETtfFontService.createResources(scope, *emptyArray<ByteArray>())
                }
            }
        }

        test("a rejected load releases every engine buffer it allocated") {
            val allocations = mutableListOf<ResourceWrapper<ByteBuffer>>()
            BufferService.override(recordingAllocations(allocations))
            try {
                ResourceScope().use { scope ->
                    shouldThrow<IllegalArgumentException> {
                        KGETtfFontService.createResources(
                            scope,
                            robotoFontBytes(),
                            ByteArray(128) { (it * 31).toByte() },
                        )
                    }
                    shouldThrow<IllegalArgumentException> {
                        KGETtfFontService.createResources(scope, robotoFontBytes(), robotoFontBytes())
                    }
                }

                allocations.forEach { it.cleaned shouldBe true }
            } finally {
                // kge-core resets overrides between its own tests only, so this
                // module restores the engine default itself.
                BufferService.override(BufferService.original)
            }
        }

        test("closing the scope makes the family's handles fail fast") {
            val scope = ResourceScope()
            val family = KGETtfFontService.createResources(scope, robotoFontBytes(), robotoItalicBytes())

            scope.close()

            family.name shouldBe "Roboto"
            shouldThrow<IllegalStateException> { family.faces }
            shouldThrow<IllegalStateException> { family.defaultFace }
        }

        test("the family close is idempotent and its inert values survive it") {
            ResourceScope().use { scope ->
                val family = KGETtfFontService.createResources(scope, robotoFontBytes(), robotoItalicBytes())
                val face = family.defaultFace

                family.close()
                family.close()

                family.name shouldBe "Roboto"
                face.name shouldBe "Regular"
                face.monospaced shouldBe false
                face.axes.keys.toList() shouldBe listOf(KGEFont.Axis.Tag.Weight, KGEFont.Axis.Tag.Width)
                shouldThrow<IllegalStateException> { family.faces }
                shouldThrow<IllegalStateException> { family.defaultFace }
                shouldThrow<IllegalStateException> { face.family }
                shouldThrow<IllegalStateException> { face.font(scope, 16.fontPx) }
            }
        }

        test("closing the family invalidates an outliving lease") {
            ResourceScope().use { scope ->
                val family = KGETtfFontService.createResources(scope, robotoFontBytes(), robotoItalicBytes())
                val font = family.defaultFace.font(scope, 16.fontPx)

                family.close()
                family.close()

                font.size.px shouldBe 16
                font.axisCoordinates.getValue(KGEFont.Axis.Tag.Weight) shouldBe 400.axisValue

                shouldThrow<IllegalStateException> { font.face }
                shouldThrow<IllegalStateException> { font.family }
                shouldThrow<IllegalStateException> { font.measureText("A", 4) }
                SpriteService.create(32, 24, Pixmap.SampleMode.NORMAL, "outliving lease").use { target ->
                    shouldThrow<IllegalStateException> {
                        font.drawText(target, 0, 0, "A", Colors.WHITE, 1, 4, Pixel.Mode.Normal)
                    }
                }
                shouldThrow<IllegalStateException> {
                    font.drawTextDecal(
                        Float2D(0f, 0f),
                        "A",
                        Colors.WHITE,
                        Float2D(1f, 1f),
                        4,
                        Int2D(32, 24),
                        Decal.Mode.NORMAL,
                        Decal.Structure.FAN,
                    ) {}
                }

                font.close()
                font.close()
            }
        }
    })

/** Records every engine buffer the module allocates while the override is active. */
private fun recordingAllocations(allocations: MutableList<ResourceWrapper<ByteBuffer>>): BufferService =
    object : BufferService {
        override fun allocate(
            sizeInBytes: Int,
            name: String?,
        ): ResourceWrapper<ByteBuffer> = BufferService.original.allocate(sizeInBytes, name).also { allocations += it }
    }
