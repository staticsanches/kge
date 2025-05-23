package dev.staticsanches.kge.image.service

import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.image.Sprite

expect interface SpriteService : KGEExtensibleService {
    fun create(
        width: Int,
        height: Int,
        sampleMode: Sprite.SampleMode,
        name: String?,
    ): Sprite

    fun duplicate(
        original: Sprite,
        newName: String?,
    ): Sprite

    fun toBase64PNG(sprite: Sprite): String

    companion object : SpriteService {
        override fun create(
            width: Int,
            height: Int,
            sampleMode: Sprite.SampleMode,
            name: String?,
        ): Sprite

        override fun duplicate(
            original: Sprite,
            newName: String?,
        ): Sprite

        override fun toBase64PNG(sprite: Sprite): String

        override val servicePriority: Int
    }
}

expect val originalSpriteServiceImplementation: SpriteService
