package dev.staticsanches.kge.text

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
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.resource.applyClosingIfFailed
import dev.staticsanches.kge.resource.closeAll
import dev.staticsanches.kge.resource.letClosingIfFailed

/**
 * The bitmap-font capability.
 *
 * Stateless: [createResources] puts the font into the caller's scope, and every
 * draw takes that scope and the surface it writes; the engine owns whatever it
 * creates. The built-in default is olc v2.30's bitmap font — a 128x48 sheet of
 * 8x8 glyphs, characters 32..126, 16 per row.
 */
interface DrawStringService : KGEOverridable {
    /**
     * Builds the bitmap font — the glyph sheet and its decal — into [scope],
     * which owns and closes it; called once by the engine at startup with a
     * current context. The decal samples NEAREST and clamps to the edge, olc's
     * font-sheet defaults.
     */
    fun createResources(scope: ResourceScope)

    /**
     * The pixel size of [text] in the mono font: the widest line by the tallest
     * line, in 8x8 cells. A newline resets the line and a tab advances by
     * [tabSizeInSpaces] cells, which must be positive.
     */
    fun getTextSize(
        text: String,
        tabSizeInSpaces: Int,
    ): Int2D

    /**
     * The pixel size of [text] in the proportional font: the widest line by the
     * tallest line, the width summed from the per-character advances. A newline
     * resets the line and a tab advances by [tabSizeInSpaces] cells, which must
     * be positive.
     */
    fun getTextSizeProp(
        text: String,
        tabSizeInSpaces: Int,
    ): Int2D

    /**
     * Draws [text] into [target] with the font registered in [scope] by
     * [createResources], the first character at ([x], [y]). A character is the
     * 8x8 sheet cell at `((c - 32) % 16, (c - 32) / 16)`, painted as a
     * `scale x scale` block wherever that cell's red channel is set. A newline
     * resets x and advances y by `8 * scale`; a tab advances x by
     * `8 * tabSizeInSpaces * scale`.
     *
     * Only [Pixel.Mode.Custom] is kept; otherwise an opaque [color] draws in
     * [Pixel.Mode.Mask] and a translucent one in [Pixel.Mode.Alpha]. A
     * non-positive [scale] is a no-op and [tabSizeInSpaces] must be positive.
     * A [scope] without the registered font fails fast.
     */
    fun drawString(
        scope: ResourceScope,
        target: Pixmap.Mutable,
        x: Int,
        y: Int,
        text: String,
        color: Pixel,
        scale: Int,
        tabSizeInSpaces: Int,
        mode: Pixel.Mode,
    )

    /** The [Int2D] form of [drawString]. */
    fun drawString(
        scope: ResourceScope,
        target: Pixmap.Mutable,
        position: Int2D,
        text: String,
        color: Pixel,
        scale: Int,
        tabSizeInSpaces: Int,
        mode: Pixel.Mode,
    ): Unit = drawString(scope, target, position.x, position.y, text, color, scale, tabSizeInSpaces, mode)

    /**
     * Draws [text] into [target] with the proportional font registered in
     * [scope], under [drawString]'s position, newline/tab, mode and scale
     * rules: each character paints and advances by its own cell width instead
     * of a fixed 8 pixels.
     */
    fun drawStringProp(
        scope: ResourceScope,
        target: Pixmap.Mutable,
        x: Int,
        y: Int,
        text: String,
        color: Pixel,
        scale: Int,
        tabSizeInSpaces: Int,
        mode: Pixel.Mode,
    )

    /** The [Int2D] form of [drawStringProp]. */
    fun drawStringProp(
        scope: ResourceScope,
        target: Pixmap.Mutable,
        position: Int2D,
        text: String,
        color: Pixel,
        scale: Int,
        tabSizeInSpaces: Int,
        mode: Pixel.Mode,
    ): Unit = drawStringProp(scope, target, position.x, position.y, text, color, scale, tabSizeInSpaces, mode)

    /**
     * Builds one partial-cell [DecalInstance] per character of [text] from the
     * font registered in [scope] and passes each to [decalInstanceCollector] for
     * the caller to draw or queue. The first cell is anchored at [position] and
     * scaled by [scale]; [color] is the instance tint, [decalMode] and
     * [decalStructure] carry over unchanged, and [screenSize] is the drawable
     * size the geometry is built against. Newlines and tabs advance as in
     * [drawString]. A [scope] without the registered font fails fast.
     */
    fun drawStringDecal(
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
    )

    /**
     * The proportional form of [drawStringDecal]: each character's cell is its
     * own width and advances by it rather than a fixed 8 pixels.
     */
    fun drawStringPropDecal(
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
    )

    companion object :
        KGEOverridable.Proxy<DrawStringService>(DrawStringService::class, DrawStringServiceDefault),
        DrawStringService {
        override fun createResources(scope: ResourceScope) = delegate.createResources(scope)

        override fun getTextSize(
            text: String,
            tabSizeInSpaces: Int,
        ): Int2D = delegate.getTextSize(text, tabSizeInSpaces)

        override fun getTextSizeProp(
            text: String,
            tabSizeInSpaces: Int,
        ): Int2D = delegate.getTextSizeProp(text, tabSizeInSpaces)

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
        ) = delegate.drawString(scope, target, x, y, text, color, scale, tabSizeInSpaces, mode)

        override fun drawString(
            scope: ResourceScope,
            target: Pixmap.Mutable,
            position: Int2D,
            text: String,
            color: Pixel,
            scale: Int,
            tabSizeInSpaces: Int,
            mode: Pixel.Mode,
        ) = delegate.drawString(scope, target, position, text, color, scale, tabSizeInSpaces, mode)

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
        ) = delegate.drawStringProp(scope, target, x, y, text, color, scale, tabSizeInSpaces, mode)

        override fun drawStringProp(
            scope: ResourceScope,
            target: Pixmap.Mutable,
            position: Int2D,
            text: String,
            color: Pixel,
            scale: Int,
            tabSizeInSpaces: Int,
            mode: Pixel.Mode,
        ) = delegate.drawStringProp(scope, target, position, text, color, scale, tabSizeInSpaces, mode)

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
        ) = delegate.drawStringDecal(
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
        ) = delegate.drawStringPropDecal(
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
    }
}

private const val FONT_SHEET_WIDTH = 128
private const val FONT_SHEET_HEIGHT = 48
private const val FONT_SHEET_NAME = "KGE bitmap font"

/** Platform-independent default: olc v2.30's `olc_ConstructFontSheet`. */
private object DrawStringServiceDefault : DrawStringService {
    override fun createResources(scope: ResourceScope) {
        SpriteService
            .create(FONT_SHEET_WIDTH, FONT_SHEET_HEIGHT, Pixmap.SampleMode.NORMAL, FONT_SHEET_NAME)
            .applyClosingIfFailed { paintFontSheet() }
            .letClosingIfFailed { sheet ->
                Decal(sheet, Decal.Filter.NEAREST, Decal.Wrap.CLAMP_TO_EDGE)
                    .letClosingIfFailed { decal -> scope.register(FontKey, BitmapFont(sheet, decal)) }
            }
    }

    override fun getTextSize(
        text: String,
        tabSizeInSpaces: Int,
    ): Int2D {
        check(tabSizeInSpaces > 0) { "Invalid tab size: $tabSizeInSpaces" }
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

                '\t' -> x += tabSizeInSpaces
                else -> x++
            }
            if (x > widest) widest = x
            if (y > tallest) tallest = y
        }
        return Int2D(widest * 8, tallest * 8)
    }

    override fun getTextSizeProp(
        text: String,
        tabSizeInSpaces: Int,
    ): Int2D {
        check(tabSizeInSpaces > 0) { "Invalid tab size: $tabSizeInSpaces" }
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

                '\t' -> x += tabSizeInSpaces * 8
                else -> x += fontSpacing[c.code - 32].y
            }
            if (x > widest) widest = x
            if (y > tallest) tallest = y
        }
        return Int2D(widest, tallest * 8)
    }

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
    ) {
        val sheet = scope.get(FontKey).sheet
        drawText(target, x, y, sheet, text, color, scale, tabSizeInSpaces, mode) { monoSpacing }
    }

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
    ) {
        val sheet = scope.get(FontKey).sheet
        drawText(target, x, y, sheet, text, color, scale, tabSizeInSpaces, mode) { fontSpacing[it.code - 32] }
    }

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
        val decal = scope.get(FontKey).decal
        drawDecalText(
            position,
            decal,
            text,
            color,
            scale,
            tabSizeInSpaces,
            screenSize,
            decalMode,
            decalStructure,
            decalInstanceCollector,
        ) { monoSpacing }
    }

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
    ) {
        val decal = scope.get(FontKey).decal
        drawDecalText(
            position,
            decal,
            text,
            color,
            scale,
            tabSizeInSpaces,
            screenSize,
            decalMode,
            decalStructure,
            decalInstanceCollector,
        ) { fontSpacing[it.code - 32] }
    }

    /**
     * Walks [text] through [decal]'s cells, one `spacingByChar` entry per
     * character giving its source-column offset and its column count/advance.
     */
    private inline fun drawDecalText(
        position: Float2D,
        decal: Decal,
        text: String,
        color: Pixel,
        scale: Float2D,
        tabSizeInSpaces: Int,
        screenSize: Int2D,
        decalMode: Decal.Mode,
        decalStructure: Decal.Structure,
        decalInstanceCollector: (DecalInstance) -> Unit,
        spacingByChar: (Char) -> Int2D,
    ) {
        var sx = 0f
        var sy = 0f
        text.forEach { c ->
            when (c) {
                '\n' -> {
                    sx = 0f
                    sy += 8 * scale.y
                }

                '\t' -> sx += 8 * tabSizeInSpaces * scale.x

                else -> {
                    val spacing = spacingByChar(c)
                    val ox = (c.code - 32) % 16
                    val oy = (c.code - 32) / 16
                    val cell = Float2D((8 * ox + spacing.x).toFloat(), (8 * oy).toFloat())
                    decalInstanceCollector(
                        DrawPartialDecalService.drawPartialDecal(
                            position = Float2D(position.x + sx, position.y + sy),
                            decal = decal,
                            sourcePosition = cell,
                            sourceSize = Float2D(spacing.y.toFloat(), 8f),
                            scale = scale,
                            tint = color,
                            mode = decalMode,
                            structure = decalStructure,
                            viewport = screenSize,
                        ),
                    )
                    sx += spacing.y * scale.x
                }
            }
        }
    }

    /**
     * Walks [text] through [sheet]'s cells, one `spacingByChar` entry per
     * character giving its source-column offset and its column count/advance.
     */
    private inline fun drawText(
        target: Pixmap.Mutable,
        x: Int,
        y: Int,
        sheet: Sprite,
        text: String,
        color: Pixel,
        scale: Int,
        tabSizeInSpaces: Int,
        mode: Pixel.Mode,
        spacingByChar: (Char) -> Int2D,
    ) {
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

        var sx = 0
        var sy = 0
        text.forEach { c ->
            when (c) {
                '\n' -> {
                    sx = 0
                    sy += 8 * scale
                }

                '\t' -> sx += 8 * tabSizeInSpaces * scale

                else -> {
                    val spacing = spacingByChar(c)
                    val ox = (c.code - 32) % 16
                    val oy = (c.code - 32) / 16
                    if (scale > 1) {
                        for (i in 0 until spacing.y) {
                            for (j in 0 until 8) {
                                if (sheet.get(i + ox * 8 + spacing.x, j + oy * 8).r > 0) {
                                    for (iScale in 0 until scale) {
                                        for (jScale in 0 until scale) {
                                            Rasterizer.draw(
                                                target,
                                                x + sx + i * scale + iScale,
                                                y + sy + j * scale + jScale,
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
                            for (j in 0 until 8) {
                                if (sheet.get(i + ox * 8 + spacing.x, j + oy * 8).r > 0) {
                                    Rasterizer.draw(target, x + sx + i, y + sy + j, color, resolvedMode)
                                }
                            }
                        }
                    }
                    sx += spacing.y * scale
                }
            }
        }
    }

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

    /** The mono glyph: no source-column offset, a whole 8-column cell. */
    private val monoSpacing = Int2D(0, 8)

    /**
     * olc v2.30 `vFontSpacing`: the `(sourceColumn, advance)` pair of each
     * character 32..126, one packed byte each as `sourceColumn shl 4 or advance`.
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
}

/** The default font's sheet, for tests. */
internal fun fontSheet(scope: ResourceScope): Sprite = scope.get(FontKey).sheet

/** The default font's decal, for tests. */
internal fun fontDecal(scope: ResourceScope): Decal = scope.get(FontKey).decal

private object FontKey : ResourceScope.Key<BitmapFont>

/** The bitmap font: the glyph sheet and the decal uploaded from it, owned as one. */
private class BitmapFont(
    val sheet: Sprite,
    val decal: Decal,
) : KGEResource {
    override fun close() = listOf(decal, sheet).closeAll()
}
