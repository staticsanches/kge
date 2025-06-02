package dev.staticsanches.kge.example

import dev.staticsanches.kge.engine.KotlinGameEngine
import dev.staticsanches.kge.engine.state.input.KeyboardKey
import dev.staticsanches.kge.engine.state.input.KeyboardKeyAction
import dev.staticsanches.kge.engine.state.input.KeyboardModifiers
import dev.staticsanches.kge.engine.state.input.ReleaseAction
import dev.staticsanches.kge.image.Colors
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

    private var generateNoise = true

    override fun onUserCreate() {
        sprite = Sprite.loadPNG(FirstExample::class.java.getResource("/xmas_5x5.png")!!)
        decal = Decal(sprite)
    }

    override fun onUserDestroy(): Boolean {
        invokeForAll(decal, sprite) { it.close() }
        return super.onUserDestroy()
    }

    override fun onUserUpdate(): Boolean {
        if (generateNoise) {
            (0..<screenSize.x).forEach { x ->
                (0..<screenSize.y).forEach { y ->
                    draw(x, y, Pixel.rgba(randomComponent(), randomComponent(), randomComponent()).inv())
                }
            }
        } else {
            clear(Colors.BLACK)
        }

        drawStringPropDecal(5f by 10f, "fps: ${timeState.fps}", scale = .5f by .5f)
        drawStringDecal(5f by 20f, "Press 'N' to toggle noise", scale = .5f by .5f, color = Colors.RED)
        drawDecal(20f by 40f, decal, scale = 7f by 7f)

        return true
    }

    override fun onKeyEvent(
        key: KeyboardKey,
        newAction: KeyboardKeyAction,
        scancode: Int,
        newModifiers: KeyboardModifiers,
    ) {
        if (key == KeyboardKey.KEY_N && newAction == ReleaseAction) generateNoise = !generateNoise
    }

    companion object {
        private fun randomComponent(): Int = Random.nextInt(0, 256)

        @JvmStatic
        fun main(args: Array<String>) {
            FirstExample().run {
                resizable = true
            }
        }
    }
}
