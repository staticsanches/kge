package dev.staticsanches.kge.example

import dev.staticsanches.kge.engine.KotlinGameEngine
import dev.staticsanches.kge.image.Decal
import dev.staticsanches.kge.image.Decal.Companion.invoke
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.extension.loadPNG
import dev.staticsanches.kge.math.vector.Float2D.Companion.by
import dev.staticsanches.kge.utils.invokeForAll
import kotlin.random.Random

class FirstExample : KotlinGameEngine("First Example") {
    private lateinit var sprite: Sprite
    private lateinit var decal: Decal

    override fun onUserCreate() {
        sprite = Sprite.loadPNG(FirstExample::class.java.getResource("/xmas_5x5.png")!!)
        decal = Decal(sprite)
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

        drawStringDecal(5f by 10f, "KGE - Kotlin Game Engine", scale = .5f by .5f)
        drawDecal(20f by 20f, decal, scale = 10f by 10f)

        return true
    }

    companion object {
        private fun randomComponent(): Int = Random.nextInt(0, 256)

        @JvmStatic
        fun main(args: Array<String>) {
            FirstExample().run {
                resizable = true
                vSync = false
            }
        }
    }
}
