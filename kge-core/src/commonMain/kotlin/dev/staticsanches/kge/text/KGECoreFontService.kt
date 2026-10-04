package dev.staticsanches.kge.text

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.renderer.decal.service.DrawPartialDecalService
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.resource.applyClosingIfFailed
import dev.staticsanches.kge.resource.closeAll
import dev.staticsanches.kge.resource.letClosingIfFailed
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/** The built-in family's two named designs; [monospaced] is [KGEFont.Family.defaultFace]. */
interface KGECoreFontFamily : KGEFont.Family {
    /** The fixed-pitch design, also the default. */
    val monospaced: KGEFont.Face

    /** The proportional design, the other member of [KGEFont.Family.faces]. */
    val proportional: KGEFont.Face
}

/** The always-available built-in bitmap font, adopted into a caller scope. */
interface KGECoreFontService : KGEOverridable {
    /** Builds the built-in family into [scope], which owns and closes it. */
    @KGESensitiveAPI
    fun createResources(scope: ResourceScope): KGECoreFontFamily

    companion object :
        KGEOverridable.Proxy<KGECoreFontService>(KGECoreFontService::class, Default),
        KGECoreFontService {
        override fun createResources(scope: ResourceScope): KGECoreFontFamily = delegate.createResources(scope)
    }
}

private const val FONT_SHEET_WIDTH = 128
private const val FONT_SHEET_HEIGHT = 48
private const val FONT_SHEET_NAME = "KGE bitmap font"
private const val FONT_CELL = 8

/** Platform-independent default: olc v2.30's `olc_ConstructFontSheet`. */
private object Default : KGECoreFontService {
    override fun createResources(scope: ResourceScope): KGECoreFontFamily =
        SpriteService
            .create(FONT_SHEET_WIDTH, FONT_SHEET_HEIGHT, Pixmap.SampleMode.NORMAL, FONT_SHEET_NAME)
            .applyClosingIfFailed { paintFontSheet() }
            .letClosingIfFailed { sheet ->
                Decal(sheet, Decal.Filter.NEAREST, Decal.Wrap.CLAMP_TO_EDGE)
                    .letClosingIfFailed { decal ->
                        CoreFontFamily(sheet, decal)
                            .letClosingIfFailed { family -> scope.register(CoreFontFamilyKey(), family) }
                    }
            }
}

/** The built-in family: one sheet and decal shared by two faces, which it owns. */
private class CoreFontFamily(
    private val sheet: Sprite,
    private val decal: Decal,
) : KGECoreFontFamily {
    private var closed = false
    private val monoFace = CoreFace(this, name = "Monospaced", monospaced = true)
    private val propFace = CoreFace(this, name = "Proportional", monospaced = false)
    private val allFaces = persistentListOf<KGEFont.Face>(monoFace, propFace)

    override val name: String = FONT_SHEET_NAME

    override val faces: List<KGEFont.Face>
        get() = checkOpen().let { allFaces }

    override val defaultFace: KGEFont.Face
        get() = checkOpen().let { monoFace }

    override val monospaced: KGEFont.Face
        get() = checkOpen().let { monoFace }

    override val proportional: KGEFont.Face
        get() = checkOpen().let { propFace }

    fun checkOpen() {
        check(!closed) { "The core font family is closed" }
    }

    fun sheetForDraw(): Sprite = checkOpen().let { sheet }

    fun decalForDraw(): Decal = checkOpen().let { decal }

    override fun close() {
        if (closed) return
        closed = true
        listOf(decal, sheet).closeAll()
    }
}

/** One selectable core design; the family owns it and callers never close it. */
private class CoreFace(
    private val owner: CoreFontFamily,
    override val name: String,
    override val monospaced: Boolean,
) : KGEFont.Face {
    private val proportional: Boolean = !monospaced

    override val family: KGEFont.Family
        get() = owner.checkOpen().let { owner }

    override val axes: Map<KGEFont.Axis.Tag, KGEFont.Axis> = persistentMapOf()

    override suspend fun font(
        scope: ResourceScope,
        size: KGEFont.Size,
        axes: Map<KGEFont.Axis.Tag, KGEFont.Axis.Value>,
    ): KGEFont {
        owner.checkOpen()
        require(axes.isEmpty()) { "The built-in bitmap font has no variation axes" }
        require(size.px % FONT_CELL == 0) {
            "The built-in bitmap font cell is $FONT_CELL px; size must be a positive multiple, was ${size.px}"
        }
        return CoreFont(owner, this, size, proportional)
            .letClosingIfFailed { font -> scope.register(CoreFontKey(), font) }
    }
}

/** A configured core font: a face and size over the family's shared payload. */
private class CoreFont(
    private val owner: CoreFontFamily,
    private val configuredFace: KGEFont.Face,
    private val configuredSize: KGEFont.Size,
    private val proportional: Boolean,
) : KGEFont {
    private var closed = false
    private val baseScale = configuredSize.px / FONT_CELL

    override val face: KGEFont.Face
        get() = checkOpen().let { configuredFace }

    override val size: KGEFont.Size = configuredSize

    override val axisCoordinates: Map<KGEFont.Axis.Tag, KGEFont.Axis.Value> = persistentMapOf()

    private fun checkOpen() {
        check(!closed) { "The configured font is closed" }
        owner.checkOpen()
    }

    private fun spacingOf(c: Char): Int2D = if (proportional) fontSpacing[c.code - 32] else monoSpacing

    override fun measureText(
        text: String,
        tabSizeInSpaces: Int,
    ): Int2D {
        checkOpen()
        check(tabSizeInSpaces > 0) { "Invalid tab size: $tabSizeInSpaces" }
        if (text.isEmpty()) return Int2D(0, 0)
        var widest = 0
        var tallest = 1
        var x = 0
        var y = 1
        text.forEach { c ->
            when (c) {
                '\n' -> {
                    y++
                    x = 0
                }

                '\t' -> x += if (proportional) tabSizeInSpaces * FONT_CELL else tabSizeInSpaces
                else -> x += if (proportional) fontSpacing[c.code - 32].y else 1
            }
            if (x > widest) widest = x
            if (y > tallest) tallest = y
        }
        return if (proportional) {
            Int2D(widest * baseScale, tallest * FONT_CELL * baseScale)
        } else {
            Int2D(widest * FONT_CELL * baseScale, tallest * FONT_CELL * baseScale)
        }
    }

    override fun drawText(
        target: Pixmap.Mutable,
        x: Int,
        y: Int,
        text: String,
        color: Pixel,
        scale: Int,
        tabSizeInSpaces: Int,
        mode: Pixel.Mode,
    ) {
        checkOpen()
        if (scale <= 0) return
        check(tabSizeInSpaces > 0) { "Invalid tab size: $tabSizeInSpaces" }

        val resolvedMode =
            if (mode is Pixel.Mode.Custom) {
                mode
            } else if (color.a != 255) {
                Pixel.Mode.Alpha()
            } else {
                Pixel.Mode.Mask
            }

        val sheet = owner.sheetForDraw()
        val effectiveScale = baseScale * scale
        var sx = 0
        var sy = 0
        text.forEach { c ->
            when (c) {
                '\n' -> {
                    sx = 0
                    sy += FONT_CELL * effectiveScale
                }

                '\t' -> sx += FONT_CELL * tabSizeInSpaces * effectiveScale
                else -> {
                    val spacing = spacingOf(c)
                    val ox = (c.code - 32) % 16
                    val oy = (c.code - 32) / 16
                    if (effectiveScale > 1) {
                        for (i in 0 until spacing.y) {
                            for (j in 0 until FONT_CELL) {
                                if (sheet.get(i + ox * FONT_CELL + spacing.x, j + oy * FONT_CELL).r > 0) {
                                    for (iScale in 0 until effectiveScale) {
                                        for (jScale in 0 until effectiveScale) {
                                            Rasterizer.draw(
                                                target,
                                                x + sx + i * effectiveScale + iScale,
                                                y + sy + j * effectiveScale + jScale,
                                                color,
                                                resolvedMode,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        for (i in 0 until spacing.y) {
                            for (j in 0 until FONT_CELL) {
                                if (sheet.get(i + ox * FONT_CELL + spacing.x, j + oy * FONT_CELL).r > 0) {
                                    Rasterizer.draw(target, x + sx + i, y + sy + j, color, resolvedMode)
                                }
                            }
                        }
                    }
                    sx += spacing.y * effectiveScale
                }
            }
        }
    }

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
    ) {
        checkOpen()
        val decal = owner.decalForDraw()
        val effectiveScale = Float2D(scale.x * baseScale, scale.y * baseScale)
        var sx = 0f
        var sy = 0f
        text.forEach { c ->
            when (c) {
                '\n' -> {
                    sx = 0f
                    sy += FONT_CELL * effectiveScale.y
                }

                '\t' -> sx += FONT_CELL * tabSizeInSpaces * effectiveScale.x
                else -> {
                    val spacing = spacingOf(c)
                    val ox = (c.code - 32) % 16
                    val oy = (c.code - 32) / 16
                    val source = Float2D((FONT_CELL * ox + spacing.x).toFloat(), (FONT_CELL * oy).toFloat())
                    decalInstanceCollector(
                        DrawPartialDecalService.drawPartialDecal(
                            position = Float2D(position.x + sx, position.y + sy),
                            decal = decal,
                            sourcePosition = source,
                            sourceSize = Float2D(spacing.y.toFloat(), FONT_CELL.toFloat()),
                            scale = effectiveScale,
                            tint = color,
                            mode = decalMode,
                            structure = decalStructure,
                            viewport = screenSize,
                        ),
                    )
                    sx += spacing.y * effectiveScale.x
                }
            }
        }
    }

    override fun close() {
        closed = true
    }
}

private class CoreFontFamilyKey : ResourceScope.Key<CoreFontFamily>

private class CoreFontKey : ResourceScope.Key<CoreFont>

/** The mono glyph: no source-column offset, a whole 8-column cell. */
private val monoSpacing = Int2D(0, FONT_CELL)

/**
 * olc v2.30 `vFontSpacing`: the `(sourceColumn, advance)` pair of each
 * character 32..127, one packed byte each as `sourceColumn shl 4 or advance`.
 */
private val fontSpacing =
    intArrayOf(
        0x03, 0x25, 0x16, 0x08, 0x07, 0x08, 0x08, 0x04, 0x15, 0x15, 0x08, 0x07, 0x15, 0x07, 0x24, 0x08,
        0x08, 0x17, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x24, 0x15, 0x06, 0x07, 0x16, 0x17,
        0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x17, 0x08, 0x08, 0x17, 0x08, 0x08, 0x08,
        0x08, 0x08, 0x08, 0x08, 0x17, 0x08, 0x08, 0x08, 0x08, 0x17, 0x08, 0x15, 0x08, 0x15, 0x08, 0x08,
        0x24, 0x18, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17, 0x33, 0x17, 0x17, 0x33, 0x18, 0x17, 0x17,
        0x17, 0x17, 0x17, 0x17, 0x07, 0x17, 0x17, 0x18, 0x18, 0x17, 0x17, 0x07, 0x33, 0x07, 0x08, 0x00,
    ).map { Int2D(it shr 4, it and 15) }

private const val FONT_SHEET_DATA =
    "?Q`0001oOch0o01o@F40o0<AGD4090LAGD<090@A7ch0?00O7Q`0600>00000000" +
        "O000000nOT0063Qo4d8>?7a14Gno94AA4gno94AaOT0>o3`oO400o7QN00000400" +
        "Of80001oOg<7O7moBGT7O7lABET024@aBEd714AiOdl717a_=TH013Q>00000000" +
        "720D000V?V5oB3Q_HdUoE7a9@DdDE4A9@DmoE4A;Hg]oM4Aj8S4D84@`00000000" +
        "OaPT1000Oa`^13P1@AI[?g`1@A=[OdAoHgljA4Ao?WlBA7l1710007l100000000" +
        "ObM6000oOfMV?3QoBDD`O7a0BDDH@5A0BDD<@5A0BGeVO5ao@CQR?5Po00000000" +
        "Oc``000?Ogij70PO2D]??0Ph2DUM@7i`2DTg@7lh2GUj?0TO0C1870T?00000000" +
        "70<4001o?P<7?1QoHg43O;`h@GT0@:@LB@d0>:@hN@L0@?aoN@<0O7ao0000?000" +
        "OcH0001SOglLA7mg24TnK7ln24US>0PL24U140PnOgl0>7QgOcH0K71S0000A000" +
        "00H00000@Dm1S007@DUSg00?OdTnH7YhOfTL<7Yh@Cl0700?@Ah0300700000000" +
        "<008001QL00ZA41a@6HnI<1i@FHLM81M@@0LG81?O`0nC?Y7?`0ZA7Y300080000" +
        "O`082000Oh0827mo6>Hn?Wmo?6HnMb11MP08@C11H`08@FP0@@0004@000000000" +
        "00P00001Oab00003OcKP0006@6=PMgl<@440MglH@000000`@000001P00000000" +
        "Ob@8@@00Ob@8@Ga13R@8Mga172@8?PAo3R@827QoOb@820@0O`0007`0000007P0" +
        "O`000P08Od400g`<3V=P0G`673IP0`@3>1`00P@6O`P00g`<O`000GP800000000" +
        "?P9PL020O`<`N3R0@E4HC7b0@ET<ATB0@@l6C4B0O`H3N7b0?P01L3R000000020"

/** Paints each payload bit as opaque white or fully transparent, column-major. */
private fun Sprite.paintFontSheet() {
    var px = 0
    var py = 0
    for (b in 0 until FONT_SHEET_DATA.length step 4) {
        val r =
            ((FONT_SHEET_DATA[b].code - 48) shl 18) or
                ((FONT_SHEET_DATA[b + 1].code - 48) shl 12) or
                ((FONT_SHEET_DATA[b + 2].code - 48) shl 6) or
                (FONT_SHEET_DATA[b + 3].code - 48)
        for (i in 0 until 24) {
            uncheckedSet(px, py, if ((r and (1 shl i)) != 0) Colors.WHITE else Colors.TRANSPARENT)
            if (++py == FONT_SHEET_HEIGHT) {
                px++
                py = 0
            }
        }
    }
}
