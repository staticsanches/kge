package dev.staticsanches.kge.golden

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.resource.applyClosingIfFailed

fun canvas(
    width: Int,
    height: Int,
): Sprite =
    SpriteService
        .create(width, height, Pixmap.SampleMode.NORMAL, null)
        .applyClosingIfFailed { clear(Colors.TRANSPARENT) }
