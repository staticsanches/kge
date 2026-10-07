package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.renderer.decal.service.DrawPartialDecalService
import io.kotest.matchers.shouldBe

/** The anchor-built instance for [glyph]'s glyph [index] at [sizePx]; the fixture owns the carrier. */
suspend fun expectedInstance(
    glyph: String,
    destination: Float2D,
    index: Int = 0,
    sizePx: Int = 16,
    scale: Float2D = Float2D(1f, 1f),
    color: Pixel = Colors.WHITE,
    screenSize: Int2D = Int2D(30, 24),
    decalMode: Decal.Mode = Decal.Mode.NORMAL,
    decalStructure: Decal.Structure = Decal.Structure.FAN,
): DecalInstance =
    withRobotoAtlas(sizePx) { anchor ->
        val placed = anchor.placed(glyph, index)
        DrawPartialDecalService.drawPartialDecal(
            position = destination,
            decal = anchor.decal(glyph, index),
            sourcePosition = placed.source.toFloat(),
            sourceSize = placed.size.toFloat(),
            scale = scale,
            tint = color,
            mode = decalMode,
            structure = decalStructure,
            viewport = screenSize,
        )
    }

/** The full instance geometry, not just the quantised anchor. */
fun assertSameGeometry(
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
