package dev.staticsanches.kge.benchmark

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.WindowInfo
import dev.staticsanches.kge.engine.layer.LayerStack
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.text.KGEFont
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val TEXT_SCENE_TEST_WIDTH = 240
private const val TEXT_SCENE_TEST_HEIGHT = 48

/** 240x48 at a 12px pitch and a 4px/2px margin queues 3 rows of 3 lines. */
private const val TEXT_SCENE_TEST_LINES = 9
private const val TEXT_SCENE_TEST_COLUMN_PITCH = 80

/** A font the recording host never touches; only the call's default argument reads it. */
private object UnusedTextFont : KGEFont {
    override val face: KGEFont.Face get() = error("unused")
    override val size: KGEFont.Size get() = error("unused")
    override val axisCoordinates: Map<KGEFont.Axis.Tag, KGEFont.Axis.Value> get() = error("unused")

    override fun measureText(
        text: String,
        tabSizeInSpaces: Int,
    ): Int2D = error("unused")

    override fun drawText(
        target: Pixmap.Mutable,
        x: Int,
        y: Int,
        text: String,
        color: Pixel,
        scale: Int,
        tabSizeInSpaces: Int,
        mode: Pixel.Mode,
    ) = error("unused")

    override fun drawTextDecal(
        position: Float2D,
        text: String,
        color: Pixel,
        scale: Float2D,
        tabSizeInSpaces: Int,
        screenSize: Int2D,
        decalMode: Decal.Mode,
        decalStructure: Decal.Structure,
        decalInstanceCollector: (DecalInstance) -> Unit,
    ) = error("unused")

    override fun close() = error("unused")
}

/** Records what [renderTextScene] queues, with no GL context and no draw target. */
@OptIn(KGESensitiveAPI::class)
private class RecordingTextSceneTarget : TextSceneTarget {
    var cleared: Pixel? = null
    val queued = mutableListOf<Pair<Float2D, String>>()

    override var textFont: KGEFont = UnusedTextFont

    override var tabSizeInSpaces: Int = 4

    override var drawTarget: Sprite? = null

    override fun setDrawTarget(
        index: Int,
        dirty: Boolean,
    ) = error("unused")

    override var pixelMode: Pixel.Mode
        get() = error("unused")
        set(value) = error("unused")

    override var decalMode: Decal.Mode
        get() = error("unused")
        set(value) = error("unused")

    override var decalStructure: Decal.Structure
        get() = error("unused")
        set(value) = error("unused")

    override var suspendTextureTransfer: Boolean
        get() = error("unused")
        set(value) = error("unused")

    override val window: WindowInfo
        get() = error("unused")

    override val layers: LayerStack
        get() = error("unused")

    override val resourceScope: ResourceScope
        get() = error("unused")

    override fun clear(pixel: Pixel) {
        cleared = pixel
    }

    override fun drawTextDecal(
        position: Float2D,
        text: String,
        color: Pixel,
        scale: Float2D,
        font: KGEFont,
    ) {
        queued += position to text
    }
}

private fun textSceneTarget(): RecordingTextSceneTarget {
    val target = RecordingTextSceneTarget()
    renderTextScene(target, TEXT_SCENE_TEST_WIDTH, TEXT_SCENE_TEST_HEIGHT)
    return target
}

class TextSceneTest :
    FunSpec({
        test("renderTextScene clears the target to the background before queuing") {
            textSceneTarget().cleared shouldBe SCENE_BACKGROUND
        }

        test("renderTextScene queues a text block row by row, left to right") {
            val target = textSceneTarget()

            target.queued.size shouldBe TEXT_SCENE_TEST_LINES
            target.queued.all { it.second == SCENE_TEXT } shouldBe true
            target.queued.first().first shouldBe Float2D(4f, 2f)
            target.queued[1].first shouldBe Float2D(4f + TEXT_SCENE_TEST_COLUMN_PITCH, 2f)
            target.queued[2].first shouldBe Float2D(4f + 2 * TEXT_SCENE_TEST_COLUMN_PITCH, 2f)
            target.queued[3].first shouldBe Float2D(4f, 14f)
        }

        test("renderTextScene queues the same block on every call") {
            val target = textSceneTarget()
            val first = target.queued.toList()

            renderTextScene(target, TEXT_SCENE_TEST_WIDTH, TEXT_SCENE_TEST_HEIGHT)

            target.queued.drop(first.size) shouldBe first
        }
    })
