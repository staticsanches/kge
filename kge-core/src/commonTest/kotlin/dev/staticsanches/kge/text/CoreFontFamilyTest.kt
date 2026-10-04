package dev.staticsanches.kge.text

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.testsupport.engine.installGl
import dev.staticsanches.kge.testsupport.golden.canvas
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

private const val TAB_SIZE = 4

/**
 * The core family's two faces over one owned payload, and the lease lifecycle:
 * idempotent close, fail-fast use, fresh keys, independent siblings.
 */
@OptIn(KGESensitiveAPI::class)
class CoreFontFamilyTest :
    FunSpec({
        fun newFamily(scope: ResourceScope): KGECoreFontFamily = KGECoreFontService.createResources(scope)

        fun openScope(): ResourceScope {
            installGl()
            return ResourceScope()
        }

        test("the family exposes the two built-in faces and defaults to the monospaced one") {
            openScope().use { scope ->
                val family = newFamily(scope)
                family.faces.size shouldBe 2
                family.faces.toSet().size shouldBe 2
                val mono = family.defaultFace
                family.faces.any { it === mono } shouldBe true
                val prop = family.faces.single { it !== mono }
                mono.axes shouldBe emptyMap()
                prop.axes shouldBe emptyMap()
                (mono.family === family) shouldBe true
                (prop.family === family) shouldBe true

                mono.font(scope, 8.fontPx).measureText("Ai", TAB_SIZE) shouldBe Int2D(16, 8)
                prop.font(scope, 8.fontPx).measureText("Ai", TAB_SIZE) shouldBe Int2D(11, 8)
            }
        }

        test("the core family and its faces report their names") {
            openScope().use { scope ->
                val family = newFamily(scope)
                family.name shouldBe "KGE bitmap font"
                family.faces.map { it.name } shouldBe listOf("Monospaced", "Proportional")
                family.defaultFace.name shouldBe "Monospaced"
            }
        }

        test("only the monospaced core face reports itself monospaced") {
            openScope().use { scope ->
                val family = newFamily(scope)
                family.defaultFace.monospaced shouldBe true
                family.faces.single { !it.monospaced }.name shouldBe "Proportional"
            }
        }

        test("the core family names both faces and defaults to monospaced") {
            openScope().use { scope ->
                val family = newFamily(scope)
                family.monospaced shouldBeSameInstanceAs family.defaultFace
                family.proportional shouldNotBeSameInstanceAs family.monospaced
                family.proportional.monospaced shouldBe false
                family.faces.toSet() shouldBe setOf(family.monospaced, family.proportional)
            }
        }

        test("the seam still accepts a foreign implementation of the core family") {
            installGl()
            val foreign = ForeignCoreFontFamily()
            KGECoreFontService.override(
                object : KGECoreFontService by KGECoreFontService.original {
                    override fun createResources(scope: ResourceScope): KGECoreFontFamily = foreign
                },
            )
            try {
                ResourceScope().use { scope ->
                    KGECoreFontService.createResources(scope) shouldBeSameInstanceAs foreign
                }
            } finally {
                KGECoreFontService.override(KGECoreFontService.original)
            }
        }

        test("the family allocates one texture, released once when the scope closes") {
            val gl = installGl()
            ResourceScope().use { scope ->
                newFamily(scope)
                gl.calls.count { it.name == "createTexture" } shouldBe 1

                gl.clear()
                scope.close()

                gl.calls.count { it.name == "deleteTexture" } shouldBe 1
            }
        }

        test("configured fonts share the family payload and allocate no texture") {
            val gl = installGl()
            ResourceScope().use { scope ->
                val family = newFamily(scope)
                gl.calls.count { it.name == "createTexture" } shouldBe 1
                val mono = family.defaultFace.font(scope, 8.fontPx)
                val prop = family.proportional.font(scope, 16.fontPx)

                gl.calls.count { it.name == "createTexture" } shouldBe 1
                mono.size.px shouldBe 8
                prop.size.px shouldBe 16
                (mono.family === family) shouldBe true
                (prop.family === family) shouldBe true
                gl.calls.count { it.name == "createTexture" } shouldBe 1
            }
        }

        test("two families and two leases coexist in one scope, each released once") {
            val gl = installGl()
            ResourceScope().use { scope ->
                val first = newFamily(scope)
                val second = newFamily(scope)
                val firstLease = first.defaultFace.font(scope, 8.fontPx)
                val secondLease = second.defaultFace.font(scope, 16.fontPx)

                gl.calls.count { it.name == "createTexture" } shouldBe 2
                firstLease.measureText("Ai", TAB_SIZE) shouldBe Int2D(16, 8)
                secondLease.measureText("Ai", TAB_SIZE) shouldBe Int2D(32, 16)
                (firstLease.family === first) shouldBe true
                (secondLease.family === second) shouldBe true

                gl.clear()
                scope.close()

                gl.calls.count { it.name == "deleteTexture" } shouldBe 2
            }
        }

        test("a size that is not a positive multiple of 8 is rejected") {
            openScope().use { scope ->
                val face = newFamily(scope).defaultFace
                for (px in listOf(0, -8, 9, 12)) {
                    shouldThrow<IllegalArgumentException> { face.font(scope, px.fontPx) }
                }
            }
        }

        test("a non-empty axis map is rejected") {
            openScope().use { scope ->
                val face = newFamily(scope).defaultFace
                shouldThrow<IllegalArgumentException> {
                    face.font(scope, 8.fontPx, mapOf(KGEFont.Axis.Tag.Weight to KGEFont.Axis.Value.of(65536)))
                }
            }
        }

        test("close is idempotent on a lease and on the family") {
            val gl = installGl()
            ResourceScope().use { scope ->
                val family = newFamily(scope)
                val font = family.defaultFace.font(scope, 8.fontPx)

                gl.clear()
                font.close()
                font.close()
                family.close()
                family.close()

                gl.calls.count { it.name == "deleteTexture" } shouldBe 1

                gl.clear()
                scope.close()
                gl.calls.count { it.name == "deleteTexture" } shouldBe 0
            }
        }

        test("the consumable operations fail fast after the lease is closed, the inert values answer") {
            openScope().use { scope ->
                val family = newFamily(scope)
                val font = family.defaultFace.font(scope, 8.fontPx)
                font.close()

                font.size.px shouldBe 8
                font.axisCoordinates shouldBe emptyMap()
                shouldThrow<IllegalStateException> { font.face }
                shouldThrow<IllegalStateException> { font.family }
                shouldThrow<IllegalStateException> { font.measureText("A", TAB_SIZE) }
                shouldThrow<IllegalStateException> {
                    canvas(8, 8).use { font.drawText(it, 0, 0, "A", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal) }
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

                family.faces.size shouldBe 2
            }
        }

        test("the consumable operations fail fast after the family is closed, the inert values answer") {
            openScope().use { scope ->
                val family = newFamily(scope)
                val mono = family.defaultFace
                val font = mono.font(scope, 8.fontPx)
                family.close()

                family.name shouldBe "KGE bitmap font"
                mono.name shouldBe "Monospaced"
                mono.monospaced shouldBe true
                mono.axes shouldBe emptyMap()
                font.size.px shouldBe 8
                font.axisCoordinates shouldBe emptyMap()
                shouldThrow<IllegalStateException> { family.faces }
                shouldThrow<IllegalStateException> { family.defaultFace }
                shouldThrow<IllegalStateException> { family.monospaced }
                shouldThrow<IllegalStateException> { family.proportional }
                shouldThrow<IllegalStateException> { mono.family }
                shouldThrow<IllegalStateException> { mono.font(scope, 8.fontPx) }
                shouldThrow<IllegalStateException> { font.face }
                shouldThrow<IllegalStateException> { font.family }
                shouldThrow<IllegalStateException> { font.measureText("A", TAB_SIZE) }
                shouldThrow<IllegalStateException> {
                    canvas(8, 8).use { font.drawText(it, 0, 0, "A", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal) }
                }
            }
        }

        test("a lease's own close leaves a sibling lease usable") {
            openScope().use { scope ->
                val family = newFamily(scope)
                val mono = family.defaultFace
                val closed = mono.font(scope, 8.fontPx)
                val sibling = mono.font(scope, 8.fontPx)

                closed.close()

                sibling.size.px shouldBe 8
                (sibling.face === mono) shouldBe true
                sibling.axisCoordinates shouldBe emptyMap()
                (sibling.family === family) shouldBe true
            }
        }
    })

/** A foreign core family: enough surface for the seam to hand it back unchanged. */
private class ForeignCoreFontFamily : KGECoreFontFamily {
    private val only: KGEFont.Face = ForeignCoreFace(this)

    override val name: String = "Foreign"
    override val faces: List<KGEFont.Face> = listOf(only)
    override val defaultFace: KGEFont.Face = only
    override val monospaced: KGEFont.Face = only
    override val proportional: KGEFont.Face = only

    override fun close() = Unit
}

private class ForeignCoreFace(
    override val family: KGEFont.Family,
) : KGEFont.Face {
    override val name: String = "Foreign"
    override val monospaced: Boolean = false
    override val axes: Map<KGEFont.Axis.Tag, KGEFont.Axis> = emptyMap()

    override suspend fun font(
        scope: ResourceScope,
        size: KGEFont.Size,
        axes: Map<KGEFont.Axis.Tag, KGEFont.Axis.Value>,
    ): KGEFont = error("The foreign test family configures no fonts")
}
