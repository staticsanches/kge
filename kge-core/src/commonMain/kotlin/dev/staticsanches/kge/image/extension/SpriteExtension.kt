package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.service.SpriteService
import dev.staticsanches.kge.resource.applyAndCloseIfFailed

fun Sprite.Companion.create(
    width: Int,
    height: Int,
    sampleMode: Sprite.SampleMode = Sprite.SampleMode.NORMAL,
    name: String? = null,
    color: Pixel? = null,
): Sprite =
    SpriteService
        .create(width, height, sampleMode, name)
        .applyAndCloseIfFailed {
            if (color != null) it.clear(color)
        }

fun Sprite.duplicate(newName: String? = null): Sprite = SpriteService.duplicate(this, newName)
