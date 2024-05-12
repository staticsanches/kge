package dev.staticsanches.kge.engine

import dev.staticsanches.kge.engine.window.Window
import dev.staticsanches.kge.image.Pixel
import kotlin.random.Random

class FirstExample : KotlinGameEngine("First Example") {
    context(Window)
    override fun onUserUpdate(): Boolean {
        (0..<screenSize.x).forEach { x ->
            (0..<screenSize.y).forEach { y ->
                draw(x, y, Pixel.rgba(randomComponent(), randomComponent(), randomComponent()))
            }
        }
        return true
    }

    companion object {
        private fun randomComponent(): Int = Random.nextInt(0, 256)

        @JvmStatic
        fun main(args: Array<String>) {
            FirstExample().start {
                resizable = true
            }
        }
    }
}
