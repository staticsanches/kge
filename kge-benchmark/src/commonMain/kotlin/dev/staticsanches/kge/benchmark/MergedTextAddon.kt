package dev.staticsanches.kge.benchmark

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.addon.ClearAddon
import dev.staticsanches.kge.engine.addon.TextAddon
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.renderer.decal.VerticesInfo
import dev.staticsanches.kge.text.KGEFont
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
 * The merged workload's text host: one triangle-list instance per run instead
 * of one per glyph; every other member forwards to [inner].
 */
@OptIn(KGESensitiveAPI::class)
internal class MergedTextAddon(
    private val inner: TextSceneTarget,
) : TextSceneTarget,
    ClearAddon by inner,
    TextAddon by inner {
    /** One cached decal and the font it was probed from, so one lease is retained. */
    private var fontDecal: Decal? = null
    private var fontDecalFont: KGEFont? = null

    override var drawTarget: Sprite?
        get() = inner.drawTarget
        set(value) {
            inner.drawTarget = value
        }

    override fun setDrawTarget(
        index: Int,
        dirty: Boolean,
    ) = inner.setDrawTarget(index, dirty)

    override fun drawTextDecal(
        position: Float2D,
        text: String,
        color: Pixel,
        scale: Float2D,
        font: KGEFont,
    ) {
        require(inner.decalStructure == Decal.Structure.LIST) {
            "the merged run serves triangle lists, was ${inner.decalStructure}"
        }
        val cached = fontDecal
        val decal = if (cached != null && fontDecalFont === font) cached else probeDecal(position, color, scale, font)
        val run =
            DecalInstance(
                decal = decal,
                mode = inner.decalMode,
                structure = Decal.Structure.LIST,
                vertices =
                    mergedRunVertices(
                        spriteWidth = decal.sprite.width,
                        spriteHeight = decal.sprite.height,
                        position = position,
                        text = text,
                        tint = color,
                        scale = scale,
                        tabSizeInSpaces = inner.tabSizeInSpaces,
                        screenSize = inner.window.screenSize,
                    ),
            )
        if (run.vertexCount > 0) {
            val instances = inner.layers.target.decalInstances
            instances.add(run)
        }
    }

    /**
     * Learns the font decal from one glyph through [font]; the probe's geometry
     * is discarded.
     */
    private fun probeDecal(
        position: Float2D,
        color: Pixel,
        scale: Float2D,
        font: KGEFont,
    ): Decal {
        var probe: DecalInstance? = null
        font.drawTextDecal(
            position,
            " ",
            color,
            scale,
            inner.tabSizeInSpaces,
            inner.window.screenSize,
            inner.decalMode,
            Decal.Structure.STRIP,
        ) { probe = it }
        return checkNotNull(probe?.decal) { "the font produced no decal" }.also {
            fontDecal = it
            fontDecalFont = font
        }
    }
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
