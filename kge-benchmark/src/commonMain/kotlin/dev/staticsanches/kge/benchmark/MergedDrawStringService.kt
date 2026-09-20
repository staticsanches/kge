package dev.staticsanches.kge.benchmark

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.renderer.decal.VerticesInfo
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.text.DrawStringService
import kotlin.math.ceil
import kotlin.math.floor

/** Two triangles per glyph: the vertex count of a merged triangle-list run. */
private const val MERGED_VERTS_PER_GLYPH = 6

/** The 8x8 cell the font sheet packs, olc's layout. */
private const val GLYPH_CELL = 8f

/** A merged run's vertices, pulled one at a time by the renderer. */
internal class FlatVertices(
    override val vertexCount: Int,
    private val xs: FloatArray,
    private val ys: FloatArray,
    private val us: FloatArray,
    private val vs: FloatArray,
    private val flatTint: Pixel,
) : VerticesInfo {
    override fun x(index: Int): Float = xs[index]

    override fun y(index: Int): Float = ys[index]

    override fun u(index: Int): Float = us[index]

    override fun v(index: Int): Float = vs[index]

    override fun tint(index: Int): Pixel = flatTint
}

/**
 * The merged workload's [DrawStringService]: one triangle-list instance per mono
 * run instead of one per glyph; every other operation delegates to [original].
 */
internal class MergedDrawStringService(
    private val original: DrawStringService,
) : DrawStringService {
    private var fontDecal: Decal? = null

    override fun createResources(scope: ResourceScope) = original.createResources(scope)

    override fun getTextSize(
        text: String,
        tabSizeInSpaces: Int,
    ): Int2D = original.getTextSize(text, tabSizeInSpaces)

    override fun getTextSizeProp(
        text: String,
        tabSizeInSpaces: Int,
    ): Int2D = original.getTextSizeProp(text, tabSizeInSpaces)

    override fun drawString(
        scope: ResourceScope,
        target: Pixmap.Mutable,
        x: Int,
        y: Int,
        text: String,
        color: Pixel,
        scale: Int,
        tabSizeInSpaces: Int,
        mode: Pixel.Mode,
    ) = original.drawString(scope, target, x, y, text, color, scale, tabSizeInSpaces, mode)

    override fun drawStringProp(
        scope: ResourceScope,
        target: Pixmap.Mutable,
        x: Int,
        y: Int,
        text: String,
        color: Pixel,
        scale: Int,
        tabSizeInSpaces: Int,
        mode: Pixel.Mode,
    ) = original.drawStringProp(scope, target, x, y, text, color, scale, tabSizeInSpaces, mode)

    override fun drawStringPropDecal(
        scope: ResourceScope,
        position: Float2D,
        text: String,
        color: Pixel,
        scale: Float2D,
        tabSizeInSpaces: Int,
        screenSize: Int2D,
        decalMode: Decal.Mode,
        decalStructure: Decal.Structure,
        decalInstanceCollector: (DecalInstance) -> Unit,
    ) = original.drawStringPropDecal(
        scope,
        position,
        text,
        color,
        scale,
        tabSizeInSpaces,
        screenSize,
        decalMode,
        decalStructure,
        decalInstanceCollector,
    )

    override fun drawStringDecal(
        scope: ResourceScope,
        position: Float2D,
        text: String,
        color: Pixel,
        scale: Float2D,
        tabSizeInSpaces: Int,
        screenSize: Int2D,
        decalMode: Decal.Mode,
        decalStructure: Decal.Structure,
        decalInstanceCollector: (DecalInstance) -> Unit,
    ) {
        require(decalStructure == Decal.Structure.LIST) {
            "the merged run serves triangle lists, was $decalStructure"
        }
        val decal = fontDecal ?: probeDecal(scope, position, color, scale, tabSizeInSpaces, screenSize, decalMode)
        val run = mergedRun(decal, position, text, color, scale, tabSizeInSpaces, screenSize, decalMode)
        if (run.vertexCount > 0) decalInstanceCollector(run)
    }

    /**
     * Learns the font decal from one glyph through [original], because the font
     * holder is not reachable from here; the probe's geometry is discarded.
     */
    private fun probeDecal(
        scope: ResourceScope,
        position: Float2D,
        color: Pixel,
        scale: Float2D,
        tabSizeInSpaces: Int,
        screenSize: Int2D,
        decalMode: Decal.Mode,
    ): Decal {
        var probe: DecalInstance? = null
        original.drawStringDecal(
            scope,
            position,
            " ",
            color,
            scale,
            tabSizeInSpaces,
            screenSize,
            decalMode,
            Decal.Structure.STRIP,
        ) { probe = it }
        return checkNotNull(probe?.decal) { "the font service produced no decal" }.also { fontDecal = it }
    }

    /** Builds the whole run as one triangle-list instance. */
    private fun mergedRun(
        decal: Decal,
        position: Float2D,
        text: String,
        color: Pixel,
        scale: Float2D,
        tabSizeInSpaces: Int,
        screenSize: Int2D,
        decalMode: Decal.Mode,
    ): DecalInstance =
        DecalInstance(
            decal = decal,
            mode = decalMode,
            structure = Decal.Structure.LIST,
            vertices =
                mergedRunVertices(
                    spriteWidth = decal.sprite.width,
                    spriteHeight = decal.sprite.height,
                    position = position,
                    text = text,
                    tint = color,
                    scale = scale,
                    tabSizeInSpaces = tabSizeInSpaces,
                    screenSize = screenSize,
                ),
        )
}

/**
 * Builds [text]'s mono run as flat triangle-list vertices — the default
 * service's quantised geometry, six vertices per glyph.
 */
internal fun mergedRunVertices(
    spriteWidth: Int,
    spriteHeight: Int,
    position: Float2D,
    text: String,
    tint: Pixel,
    scale: Float2D,
    tabSizeInSpaces: Int,
    screenSize: Int2D,
): FlatVertices {
    val viewportW = screenSize.x.toFloat()
    val viewportH = screenSize.y.toFloat()
    val inverseX = 1f / viewportW
    val inverseY = 1f / viewportH
    val uvScaleX = 1f / spriteWidth.toFloat()
    val uvScaleY = 1f / spriteHeight.toFloat()

    val capacity = text.length * MERGED_VERTS_PER_GLYPH
    val xs = FloatArray(capacity)
    val ys = FloatArray(capacity)
    val us = FloatArray(capacity)
    val vs = FloatArray(capacity)
    var count = 0
    var sx = 0f
    var sy = 0f
    for (c in text) {
        when (c) {
            '\n' -> {
                sx = 0f
                sy += GLYPH_CELL * scale.y
            }

            '\t' -> sx += GLYPH_CELL * tabSizeInSpaces * scale.x

            else -> {
                val ox = (c.code - 32) % 16
                val oy = (c.code - 32) / 16
                val px = position.x + sx
                val py = position.y + sy
                val sourceX = GLYPH_CELL * ox
                val sourceY = GLYPH_CELL * oy

                val screenSpacePosX = px * inverseX * 2f - 1f
                val screenSpacePosY = -(py * inverseY * 2f - 1f)
                val screenSpaceDimX = (px + GLYPH_CELL * scale.x) * inverseX * 2f - 1f
                val screenSpaceDimY = -((py + GLYPH_CELL * scale.y) * inverseY * 2f - 1f)
                val qPosX = floor(screenSpacePosX * viewportW + 0.5f) / viewportW
                val qPosY = floor(screenSpacePosY * viewportH + 0.5f) / viewportH
                val qDimX = ceil(screenSpaceDimX * viewportW + 0.5f) / viewportW
                val qDimY = ceil(screenSpaceDimY * viewportH - 0.5f) / viewportH
                val u0 = (sourceX + 0.0001f) * uvScaleX
                val v0 = (sourceY + 0.0001f) * uvScaleY
                val u1 = (sourceX + GLYPH_CELL - 0.0001f) * uvScaleX
                val v1 = (sourceY + GLYPH_CELL - 0.0001f) * uvScaleY

                xs[count] = qPosX
                ys[count] = qPosY
                us[count] = u0
                vs[count] = v0
                count++
                xs[count] = qPosX
                ys[count] = qDimY
                us[count] = u0
                vs[count] = v1
                count++
                xs[count] = qDimX
                ys[count] = qDimY
                us[count] = u1
                vs[count] = v1
                count++
                xs[count] = qPosX
                ys[count] = qPosY
                us[count] = u0
                vs[count] = v0
                count++
                xs[count] = qDimX
                ys[count] = qDimY
                us[count] = u1
                vs[count] = v1
                count++
                xs[count] = qDimX
                ys[count] = qPosY
                us[count] = u1
                vs[count] = v0
                count++

                sx += GLYPH_CELL * scale.x
            }
        }
    }
    return FlatVertices(count, xs, ys, us, vs, tint)
}
