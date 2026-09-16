package dev.staticsanches.kge.golden

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.resource.applyClosingIfFailed
import io.kotest.core.spec.style.FunSpec

class GoldenSamplingTest :
    FunSpec({
        test("sampling/periodic matches the golden") {
            source3x3(Pixmap.SampleMode.PERIODIC).use { source ->
                canvas(5, 5).use { target ->
                    copyExtended(source, target)
                    target.shouldMatchGolden("sampling/periodic")
                }
            }
        }

        test("sampling/clamp matches the golden") {
            source3x3(Pixmap.SampleMode.CLAMP).use { source ->
                canvas(5, 5).use { target ->
                    copyExtended(source, target)
                    target.shouldMatchGolden("sampling/clamp")
                }
            }
        }
    })

/**
 * Copies a 3x3 source onto a 5x5 target by reading `(x - 1, y - 1)` through the
 * mode-aware [Pixmap.get], so every border cell reads out of range and follows
 * the source's [Pixmap.SampleMode].
 */
private fun copyExtended(
    source: Pixmap,
    target: Pixmap.Mutable,
) {
    for (y in 0 until target.height) {
        for (x in 0 until target.width) {
            target.set(x, y, source.get(x - 1, y - 1))
        }
    }
}

private fun source3x3(mode: Pixmap.SampleMode): Sprite =
    SpriteService
        .create(3, 3, mode, null)
        .applyClosingIfFailed {
            for (y in 0 until 3) {
                for (x in 0 until 3) {
                    set(x, y, Pixel.rgba(20 + 60 * x, 200 - 40 * y, 10 + 30 * (x + y), 255))
                }
            }
        }
