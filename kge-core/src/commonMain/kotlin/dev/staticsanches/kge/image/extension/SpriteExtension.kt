@file:Suppress("unused")

package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.service.SpriteService

fun Sprite.Companion.create(
    width: Int,
    height: Int,
    sampleMode: Sprite.SampleMode = Sprite.SampleMode.NORMAL,
    name: String? = null,
): Sprite = SpriteService.create(width, height, sampleMode, name)

fun Sprite.duplicate(newName: String? = null): Sprite = SpriteService.duplicate(this, newName)
