package dev.staticsanches.kge.configuration

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.IntColorComponent
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite

data object KGEConfiguration {
    var defaultSpritePixel: Pixel? = Colors.BLACK
    var defaultSpriteSampleMode: Sprite.SampleMode = Sprite.SampleMode.NORMAL

    var defaultPixelAlpha: IntColorComponent = 0xFF

    var defaultTabSizeInSpaces: Int = 4

    /**
     * Defines the maximum number of vertices supported.
     * Each vertex contains 28 bytes of information:
     * - 4 (float) position coordinates (x, y, z, w)
     * - 2 (float) tex coordinates (u, v)
     * - 1 (int) tint value
     *
     * @see dev.staticsanches.kge.renderer.DecalInstance.VerticesInfo
     */
    var maxNumberOfVertices: Int = 65_536 // ~ 1.8 MiB
        set(value) {
            check(value > 4) { "At least 4 vertices must be supported" }
            field = value
        }
}
