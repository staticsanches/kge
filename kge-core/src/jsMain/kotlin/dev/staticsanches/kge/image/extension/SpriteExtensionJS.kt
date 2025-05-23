package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.Sprite.SampleMode
import dev.staticsanches.kge.image.service.SpriteService
import js.promise.Promise

fun Sprite.Companion.loadPNG(
    url: String,
    sampleMode: SampleMode = SampleMode.NORMAL,
    name: String? = null,
): Promise<Sprite> = SpriteService.loadPNG(url, sampleMode, name)

fun Sprite.Companion.loadPNGFromBase64(
    data: String,
    sampleMode: SampleMode = SampleMode.NORMAL,
    name: String? = null,
): Promise<Sprite> = SpriteService.loadPNGFromBase64(data, sampleMode, name)
