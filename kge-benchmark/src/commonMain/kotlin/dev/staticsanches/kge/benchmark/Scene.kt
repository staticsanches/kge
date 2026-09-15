package dev.staticsanches.kge.benchmark

import dev.staticsanches.kge.engine.addon.ClearAddon
import dev.staticsanches.kge.engine.addon.DrawCircleAddon
import dev.staticsanches.kge.engine.addon.DrawLineAddon
import dev.staticsanches.kge.engine.addon.DrawRectAddon
import dev.staticsanches.kge.engine.addon.DrawSpriteAddon
import dev.staticsanches.kge.engine.addon.DrawTriangleAddon
import dev.staticsanches.kge.engine.addon.FillCircleAddon
import dev.staticsanches.kge.engine.addon.FillRectAddon
import dev.staticsanches.kge.engine.addon.FillTriangleAddon
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite

/** The draw surface [renderScene] paints on: the addons it exercises. */
internal interface SceneTarget :
    ClearAddon,
    DrawLineAddon,
    DrawRectAddon,
    FillRectAddon,
    DrawCircleAddon,
    FillCircleAddon,
    DrawTriangleAddon,
    FillTriangleAddon,
    DrawSpriteAddon

/** The side of the sprite every rendered frame blits, in pixels. */
internal const val SCENE_SPRITE_SIZE = 32

/** The side of one grid cell, in pixels; the element count follows the screen. */
private const val SCENE_CELL = 48

/** The gap kept between a cell's edges and the shape drawn in it. */
private const val CELL_MARGIN = 4

/** The background [renderScene] clears the target to before drawing. */
internal val SCENE_BACKGROUND: Pixel = Colors.BLACK

private const val SCENE_OPS = 8

private val scenePalette =
    listOf(
        Colors.WHITE,
        Colors.RED,
        Colors.GREEN,
        Colors.BLUE,
        Colors.YELLOW,
        Colors.CYAN,
        Colors.MAGENTA,
        Colors.ORANGE,
    )

/**
 * Paints one frame of the rendering workload as a deterministic grid: the
 * target is cleared to [SCENE_BACKGROUND], then each [SCENE_CELL]-sized cell
 * draws one operation, cycling filled and outlined rectangles, circles and
 * triangles, a diagonal line and a [sprite] blit. Every shape stays inside its
 * cell; the element count follows the screen size and the pattern is identical
 * frame to frame.
 */
internal fun renderScene(
    target: SceneTarget,
    width: Int,
    height: Int,
    sprite: Sprite,
) {
    target.clear(SCENE_BACKGROUND)
    val columns = (width / SCENE_CELL).coerceAtLeast(1)
    val rows = (height / SCENE_CELL).coerceAtLeast(1)
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            val index = row * columns + column
            val x0 = column * SCENE_CELL + CELL_MARGIN
            val y0 = row * SCENE_CELL + CELL_MARGIN
            val x1 = (column + 1) * SCENE_CELL - 1 - CELL_MARGIN
            val y1 = (row + 1) * SCENE_CELL - 1 - CELL_MARGIN
            val centerX = (x0 + x1) / 2
            val centerY = (y0 + y1) / 2
            val radius = minOf(x1 - x0, y1 - y0) / 2
            val color = scenePalette[index % scenePalette.size]
            when (index % SCENE_OPS) {
                0 -> target.fillRect(x0, y0, x1, y1, color)
                1 -> target.drawRect(x0, y0, x1, y1, color)
                2 -> target.drawLine(x0, y0, x1, y1, color)
                3 -> target.fillCircle(centerX, centerY, radius, color = color)
                4 -> target.drawCircle(centerX, centerY, radius, color = color)
                5 -> target.fillTriangle(centerX, y0, x0, y1, x1, y1, color)
                6 -> target.drawTriangle(centerX, y0, x0, y1, x1, y1, color)
                else -> target.drawSprite(x0, y0, sprite, (x1 - x0 + 1) / sprite.width)
            }
        }
    }
}
