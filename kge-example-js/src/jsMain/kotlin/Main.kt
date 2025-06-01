import dev.staticsanches.kge.engine.KotlinGameEngine
import dev.staticsanches.kge.image.Decal
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.extension.loadPNG
import dev.staticsanches.kge.math.vector.Float2D.Companion.by
import dev.staticsanches.kge.utils.invokeForAll
import web.dom.document
import kotlin.random.Random

fun main() {
    FirstExample().run(document.getElementById("canvas-holder")!!) {}
}

class FirstExample : KotlinGameEngine() {
    private lateinit var sprite: Sprite
    private lateinit var decal: Decal

    override suspend fun onUserCreate() {
        sprite = Sprite.loadPNG("/xmas_5x5.png").await()
        decal = Decal(sprite)
    }

    override suspend fun onUserDestroy(): Boolean {
        invokeForAll(sprite, decal) { it.close() }
        return super.onUserDestroy()
    }

    override suspend fun onUserUpdate(): Boolean {
        (0..<screenSize.x).forEach { x ->
            (0..<screenSize.y).forEach { y ->
                draw(x, y, Pixel.rgba(randomComponent(), randomComponent(), randomComponent()).inv())
            }
        }

        drawStringDecal(5f by 10f, "KGE - Kotlin Game Engine", scale = .5f by .5f)
        drawDecal(20f by 20f, decal, scale = 10f by 10f)

        return true
        return true
    }

    companion object {
        private fun randomComponent(): Int = Random.nextInt(0, 256)
    }
}
