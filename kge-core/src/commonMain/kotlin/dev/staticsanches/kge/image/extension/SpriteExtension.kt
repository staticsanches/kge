package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.configuration.KGEConfiguration
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.service.SpriteService
import dev.staticsanches.kge.resource.applyClosingIfFailed

fun Sprite.Companion.create(
    width: Int,
    height: Int,
    sampleMode: Sprite.SampleMode = KGEConfiguration.defaultSpriteSampleMode,
    name: String? = null,
    color: Pixel? = KGEConfiguration.defaultSpritePixel,
): Sprite =
    SpriteService
        .create(width, height, sampleMode, name)
        .applyClosingIfFailed { if (color != null) clear(color) }

fun Sprite.duplicate(newName: String? = null): Sprite = SpriteService.duplicate(this, newName)

fun Sprite.toBase64PNG(): String = SpriteService.toBase64PNG(this)
