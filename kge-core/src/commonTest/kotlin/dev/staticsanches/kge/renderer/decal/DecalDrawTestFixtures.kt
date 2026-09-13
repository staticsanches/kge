package dev.staticsanches.kge.renderer.decal

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

/** Asserts the two vertex lists match within a float tolerance. */
internal fun assertVerticesCloseTo(
    actual: List<Float2D>,
    expected: List<Float2D>,
    tolerance: Float = 1e-5f,
) {
    actual.size shouldBe expected.size
    expected.indices.forEach { index ->
        withClue("vertex $index: expected ${expected[index]}, got ${actual[index]}") {
            abs(actual[index].x - expected[index].x) shouldBeLessThanOrEqualTo tolerance
            abs(actual[index].y - expected[index].y) shouldBeLessThanOrEqualTo tolerance
        }
    }
}
