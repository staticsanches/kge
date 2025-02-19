package dev.staticsanches.kge.engine

import dev.staticsanches.kge.configuration.Configuration
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.SpriteDecal
import dev.staticsanches.kge.math.vector.by
import dev.staticsanches.kge.utils.invokeForAll
import kotlin.random.Random

class FirstExample : KotlinGameEngine<FirstExample>("First Example") {
    private lateinit var sprite: Sprite
    private lateinit var decal: SpriteDecal

    override fun onUserCreate() {
        sprite = Sprite.load(FirstExample::class.java.getResource("/xmas_5x5.png")!!)
        decal = SpriteDecal(sprite)
    }

    override fun onUserDestroy(): Boolean {
        invokeForAll(decal, sprite) { it.close() }
        return super.onUserDestroy()
    }

    override fun onUserUpdate(): Boolean {
        (0..<screenSize.x).forEach { x ->
            (0..<screenSize.y).forEach { y ->
                draw(x, y, Pixel.rgba(randomComponent(), randomComponent(), randomComponent()).inv())
            }
        }

        pixelMode = Pixel.Mode.Alpha()
        repeat(1_000) {
            drawPartialDecal(10f by 50f, decal, 1 by 1, 2 by 3, 4f by 4f)
            drawPartialDecal(50f by 50f, decal, 1 by 1, 2 by 3, 4f by 4f)
            drawPartialDecal(70f by 70f, decal)
//            drawSprite(30, 30, sprite, 4u)
            drawDecal(10f by 10f, decal, 4f by 4f)
            drawDecal(60f by 10f, decal, 4f by 4f)
        }
        pixelMode = Pixel.Mode.Normal

        return true
    }

    companion object {
        private fun randomComponent(): Int = Random.nextInt(0, 256)

        @JvmStatic
        fun main(args: Array<String>) {
            Configuration.useOpenGL11 = false
            FirstExample().run {
                resizable = true
                vSync = false
            }
        }
    }
}
