package dev.staticsanches.kge.renderer.decal

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.RecordingGLService
import dev.staticsanches.kge.renderer.gl.resource.Texture
import dev.staticsanches.kge.renderer.gl.service.GLService
import io.kotest.assertions.withClue
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import kotlin.math.abs

/**
 * Builds a real [Decal] over a [width]x[height] sprite. A recording [GLService]
 * fabricates the texture handle; both resources close when [block] returns. The
 * sprite dimensions drive the draw services' quantisation and UV scale.
 */
internal fun withTestDecal(
    width: Int = 8,
    height: Int = 4,
    block: (Decal) -> Unit,
) {
    GLService.override(RecordingGLService())
    Texture.create(width, height, GL.NEAREST, GL.CLAMP_TO_EDGE).use { texture ->
        SpriteService.create(width, height, Pixmap.SampleMode.NORMAL, null).use { sprite ->
            block(Decal(texture, sprite))
        }
    }
}

/** Asserts [vertices]' x/y pulls match [expected] within [tolerance]. */
internal fun assertVerticesCloseTo(
    vertices: VerticesInfo,
    expected: List<Float2D>,
    tolerance: Float = 1e-5f,
) {
    vertices.vertexCount shouldBe expected.size
    expected.indices.forEach { index ->
        withClue("vertex $index: expected ${expected[index]}, got (${vertices.x(index)}, ${vertices.y(index)})") {
            abs(vertices.x(index) - expected[index].x) shouldBeLessThanOrEqualTo tolerance
            abs(vertices.y(index) - expected[index].y) shouldBeLessThanOrEqualTo tolerance
        }
    }
}

/** Asserts [vertices]' u/v pulls match [expected] within [tolerance]. */
internal fun assertUvsCloseTo(
    vertices: VerticesInfo,
    expected: List<Float2D>,
    tolerance: Float = 1e-5f,
) {
    vertices.vertexCount shouldBe expected.size
    expected.indices.forEach { index ->
        withClue("uv $index: expected ${expected[index]}, got (${vertices.u(index)}, ${vertices.v(index)})") {
            abs(vertices.u(index) - expected[index].x) shouldBeLessThanOrEqualTo tolerance
            abs(vertices.v(index) - expected[index].y) shouldBeLessThanOrEqualTo tolerance
        }
    }
}

/** Asserts [vertices]' per-index tints match [expected]. */
internal fun assertTints(
    vertices: VerticesInfo,
    expected: List<Pixel>,
) {
    vertices.vertexCount shouldBe expected.size
    expected.indices.forEach { index ->
        withClue("tint $index: expected ${expected[index]}, got ${vertices.tint(index)}") {
            vertices.tint(index) shouldBe expected[index]
        }
    }
}

/** A list-backed [VerticesInfo] double for arbitrary vertex counts. */
internal fun verticesOf(
    pos: List<Float2D>,
    uv: List<Float2D>,
    tint: List<Pixel>,
): VerticesInfo = ListVerticesInfo(pos, uv, tint)

/** The zero-vertex double used by override-behavior tests. */
internal fun emptyVertices(): VerticesInfo = verticesOf(emptyList(), emptyList(), emptyList())

private class ListVerticesInfo(
    private val pos: List<Float2D>,
    private val uv: List<Float2D>,
    private val tint: List<Pixel>,
) : VerticesInfo {
    override val vertexCount: Int get() = pos.size

    override fun x(index: Int): Float = pos[index].x

    override fun y(index: Int): Float = pos[index].y

    override fun u(index: Int): Float = uv[index].x

    override fun v(index: Int): Float = uv[index].y

    override fun tint(index: Int): Pixel = tint[index]
}
