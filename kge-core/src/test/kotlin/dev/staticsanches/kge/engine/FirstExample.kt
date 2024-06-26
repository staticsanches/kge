package dev.staticsanches.kge.engine

import dev.staticsanches.kge.configuration.Configuration
import dev.staticsanches.kge.engine.window.Window
import dev.staticsanches.kge.image.Decal
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.by
import dev.staticsanches.kge.utils.invokeForAll
import kotlin.random.Random

class FirstExample : KotlinGameEngine("First Example") {
    private lateinit var sprite: Sprite
    private lateinit var decal: Decal

    context(Window)
    override fun onUserCreate() {
        sprite = Sprite.load(FirstExample::class.java.getResource("/xmas_5x5.png")!!)
        decal = Decal(sprite)
    }

    context(Window)
    override fun onUserDestroy(): Boolean {
        invokeForAll(decal, sprite) { it.close() }
        return super.onUserDestroy()
    }

    context(Window)
    override fun onUserUpdate(): Boolean {
        (0..<screenSize.x).forEach { x ->
            (0..<screenSize.y).forEach { y ->
                draw(x, y, Pixel.rgba(randomComponent(), randomComponent(), randomComponent()))
            }
        }

        pixelMode = Pixel.Mode.Alpha()
        drawSprite(30, 30, sprite, 4u)
        pixelMode = Pixel.Mode.Normal

        drawDecal(10 by 10, decal, 4f by 4f)

        return true
    }

    companion object {
        private fun randomComponent(): Int = Random.nextInt(0, 256)

        @JvmStatic
        fun main(args: Array<String>) {
            Configuration.useOpenGL11 = false
            FirstExample().start {
                resizable = true
            }
        }
    }
}
