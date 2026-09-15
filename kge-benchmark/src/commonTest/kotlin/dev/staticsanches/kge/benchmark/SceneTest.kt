package dev.staticsanches.kge.benchmark

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.renderer.decal.Decal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SceneTest :
    FunSpec({
        test("renderScene clears the target to the background before drawing") {
            withSceneSprite { target, _, sprite ->
                target.clear(SENTINEL)
                renderScene(TestSceneTarget(target), SCENE_WIDTH, SCENE_HEIGHT, sprite)

                target.pixels().count { it == SENTINEL } shouldBe 0
            }
        }

        test("renderScene draws the grid over the cleared background") {
            withSceneSprite { target, _, sprite ->
                renderScene(TestSceneTarget(target), SCENE_WIDTH, SCENE_HEIGHT, sprite)

                val pixels = target.pixels()
                pixels.any { it != SCENE_BACKGROUND } shouldBe true
                pixels.any { it == SCENE_BACKGROUND } shouldBe true
            }
        }

        test("renderScene is deterministic for the same screen") {
            withSceneSprite { first, second, sprite ->
                renderScene(TestSceneTarget(first), SCENE_WIDTH, SCENE_HEIGHT, sprite)
                renderScene(TestSceneTarget(second), SCENE_WIDTH, SCENE_HEIGHT, sprite)

                first.pixels() shouldBe second.pixels()
            }
        }
    })

/** Large enough that the bounded elements do not cover every cell. */
private const val SCENE_WIDTH = 1024
private const val SCENE_HEIGHT = 768

/** A value no palette entry produces, so a remaining cell proves the clear was skipped. */
private val SENTINEL = Pixel.rgba(0x123456FFu)

private class TestSceneTarget(
    override var drawTarget: Sprite?,
) : SceneTarget {
    override var pixelMode: Pixel.Mode = Pixel.Mode.Normal
    override var decalMode: Decal.Mode = Decal.Mode.NORMAL
    override var decalStructure: Decal.Structure = Decal.Structure.FAN
    override var suspendTextureTransfer: Boolean = false

    override fun setDrawTarget(
        index: Int,
        dirty: Boolean,
    ) = Unit
}

private fun Sprite.pixels(): List<Pixel> {
    val result = ArrayList<Pixel>(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            result += uncheckedGet(x, y)
        }
    }
    return result
}

private fun withSceneSprite(block: (first: Sprite, second: Sprite, sprite: Sprite) -> Unit) {
    SpriteService.create(SCENE_WIDTH, SCENE_HEIGHT, Pixmap.SampleMode.NORMAL, null).use { first ->
        SpriteService.create(SCENE_WIDTH, SCENE_HEIGHT, Pixmap.SampleMode.NORMAL, null).use { second ->
            SpriteService.create(SCENE_SPRITE_SIZE, SCENE_SPRITE_SIZE, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                block(first, second, sprite)
            }
        }
    }
}
