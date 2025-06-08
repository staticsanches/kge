import dev.staticsanches.kge.engine.KotlinGameEngine
import dev.staticsanches.kge.engine.state.input.KeyboardKey
import dev.staticsanches.kge.engine.state.input.KeyboardKeyAction
import dev.staticsanches.kge.engine.state.input.KeyboardModifiers
import dev.staticsanches.kge.engine.state.input.ReleaseAction
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Decal
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.extension.loadPNG
import dev.staticsanches.kge.math.vector.Float2D.Companion.by
import dev.staticsanches.kge.utils.invokeForAll
import web.dom.document
import web.uievents.KeyboardEvent
import kotlin.random.Random

fun main() {
    FirstExample().run(document.getElementById("canvas-holder")!!) {}
}

class FirstExample : KotlinGameEngine() {
    private lateinit var sprite: Sprite
    private lateinit var decal: Decal

    private var generateNoise = true

    override suspend fun onUserCreate() {
        sprite = Sprite.loadPNG("xmas_5x5.png").await()
        decal = Decal(sprite)
    }

    override suspend fun onUserDestroy(): Boolean {
        invokeForAll(sprite, decal) { it.close() }
        return super.onUserDestroy()
    }

    override suspend fun onUserUpdate(): Boolean {
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
        drawStringPropDecal(5f by 20f, "Press 'N' to toggle noise", scale = .5f by .5f, color = Colors.RED)
        drawDecal(20f by 40f, decal, scale = 7f by 7f)

        return true
    }

    override suspend fun onKeyboardEvent(
        key: KeyboardKey,
        newAction: KeyboardKeyAction,
        newModifiers: KeyboardModifiers,
        event: KeyboardEvent,
    ) {
        if (key == KeyboardKey.KeyN && newAction == ReleaseAction) generateNoise = !generateNoise
    }

    companion object {
        private fun randomComponent(): Int = Random.nextInt(0, 256)
    }
}
