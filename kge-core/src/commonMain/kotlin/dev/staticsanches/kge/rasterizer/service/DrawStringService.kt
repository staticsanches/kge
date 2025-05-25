package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.MutablePixelMap
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.extension.create
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.math.vector.MutableInt2D
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.resource.applyAndCloseIfFailed
import kotlin.math.max

interface DrawStringService : KGEExtensibleService {
    fun createFontSheet(): Sprite

    fun getTextSize(
        text: String,
        tabSizeInSpaces: Int,
    ): Int2D

    fun drawString(
        position: Int2D,
        text: String,
        color: Pixel,
        scale: Int,
        tabSizeInSpaces: Int,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
        fontSheet: Sprite,
    )

    fun drawString(
        x: Int,
        y: Int,
        text: String,
        color: Pixel,
        scale: Int,
        tabSizeInSpaces: Int,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
        fontSheet: Sprite,
    )

    fun getTextSizeProp(
        text: String,
        tabSizeInSpaces: Int,
    ): Int2D

    fun drawStringProp(
        position: Int2D,
        text: String,
        color: Pixel,
        scale: Int,
        tabSizeInSpaces: Int,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
        fontSheet: Sprite,
    )

    fun drawStringProp(
        x: Int,
        y: Int,
        text: String,
        color: Pixel,
        scale: Int,
        tabSizeInSpaces: Int,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
        fontSheet: Sprite,
    )

    companion object : DrawStringService by KGEExtensibleService.getOptionalWithHigherPriority()
        ?: originalDrawStringServiceImplementation
}

val originalDrawStringServiceImplementation: DrawStringService
    get() = DefaultDrawStringService

private data object DefaultDrawStringService : DrawStringService {
    override fun createFontSheet(): Sprite =
        Sprite.create(128, 48, name = "KGE Font Sheet").applyAndCloseIfFailed { sprite ->
            var px = 0
            var py = 0
            for (b in 0..<1024 step 4) {
                val sym1 = FONT_SHEET_DATA[b + 0].code - 48
                val sym2 = FONT_SHEET_DATA[b + 1].code - 48
                val sym3 = FONT_SHEET_DATA[b + 2].code - 48
                val sym4 = FONT_SHEET_DATA[b + 3].code - 48
                val r = (sym1 shl 18) or (sym2 shl 12) or (sym3 shl 6) or sym4
                for (i in 0..<24) {
                    sprite[px, py] = if ((r and (1 shl i) != 0)) Colors.WHITE else Colors.BLANK
                    if (++py == 48) {
                        px++
                        py = 0
                    }
                }
            }
        }

    override fun getTextSize(
        text: String,
        tabSizeInSpaces: Int,
    ): Int2D {
        check(tabSizeInSpaces > 0) { "Invalid tab size: $tabSizeInSpaces" }

        val size = MutableInt2D(0, 1)
        val pos = MutableInt2D(0, 1)
        text.forEach { c ->
            if (c == '\n') {
                pos.y++
                pos.x = 0
            } else if (c == '\t') {
                pos.x += tabSizeInSpaces
            } else {
                pos.x++
            }
            size.x = max(size.x, pos.x)
            size.y = max(size.y, pos.y)
        }
        size *= 8
        return size.toImmutable()
    }

    override fun drawString(
        position: Int2D,
        text: String,
        color: Pixel,
        scale: Int,
        tabSizeInSpaces: Int,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
        fontSheet: Sprite,
    ) = drawString(position.x, position.y, text, color, scale, tabSizeInSpaces, target, pixelMode, fontSheet)

    override fun drawString(
        x: Int,
        y: Int,
        text: String,
        color: Pixel,
        scale: Int,
        tabSizeInSpaces: Int,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
        fontSheet: Sprite,
    ) = drawString(x, y, text, color, scale, tabSizeInSpaces, target, pixelMode, fontSheet) { monoSpacing }

    override fun getTextSizeProp(
        text: String,
        tabSizeInSpaces: Int,
    ): Int2D {
        check(tabSizeInSpaces > 0) { "Invalid tab size: $tabSizeInSpaces" }

        val size = MutableInt2D(0, 1)
        val pos = MutableInt2D(0, 1)
        text.forEach { c ->
            if (c == '\n') {
                pos.y++
                pos.x = 0
            } else if (c == '\t') {
                pos.x += tabSizeInSpaces * 8
            } else {
                pos.x += fontSpacing[c.code - 32].y
            }
            size.x = max(size.x, pos.x)
            size.y = max(size.y, pos.y)
        }
        size.y *= 8
        return size.toImmutable()
    }

    override fun drawStringProp(
        position: Int2D,
        text: String,
        color: Pixel,
        scale: Int,
        tabSizeInSpaces: Int,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
        fontSheet: Sprite,
    ) = drawStringProp(position.x, position.y, text, color, scale, tabSizeInSpaces, target, pixelMode, fontSheet)

    override fun drawStringProp(
        x: Int,
        y: Int,
        text: String,
        color: Pixel,
        scale: Int,
        tabSizeInSpaces: Int,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
        fontSheet: Sprite,
    ) = drawString(x, y, text, color, scale, tabSizeInSpaces, target, pixelMode, fontSheet) {
        fontSpacing[it.code - 32]
    }

    private inline fun drawString(
        x: Int,
        y: Int,
        text: String,
        color: Pixel,
        scale: Int,
        tabSizeInSpaces: Int,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
        fontSheet: Sprite,
        spacingByChar: (Char) -> Int2D,
    ) {
        if (scale <= 0) return

        check(tabSizeInSpaces > 0) { "Invalid tab size: $tabSizeInSpaces" }

        val resolvedPixelMode =
            if (pixelMode is Pixel.Mode.Custom || pixelMode is Pixel.Mode.Alpha) {
                pixelMode
            } else if (color.a != 255) {
                Pixel.Mode.Alpha()
            } else {
                Pixel.Mode.Mask
            }

        var sx = 0
        var sy = 0
        text.forEach { c ->
            if (c == '\n') {
                sx = 0
                sy += 8 * scale
            } else if (c == '\t') {
                sx += 8 * tabSizeInSpaces * scale
            } else {
                val (spacingX, spacingY) = spacingByChar(c)

                val ox = (c.code - 32) % 16
                val oy = (c.code - 32) / 16

                if (scale > 1) {
                    for (i in 0..<spacingY) {
                        for (j in 0..<8) {
                            if (fontSheet[i + ox * 8 + spacingX, j + oy * 8].r > 0) {
                                for (iScale in 0..<scale) {
                                    for (jScale in 0..<scale) {
                                        Rasterizer.draw(
                                            x + sx + (i * scale) + iScale,
                                            y + sy + (j * scale) + jScale,
                                            color, target, resolvedPixelMode,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    for (i in 0..<spacingY) {
                        for (j in 0..<8) {
                            if (fontSheet[i + ox * 8 + spacingX, j + oy * 8].r > 0) {
                                Rasterizer.draw(
                                    x + sx + i,
                                    y + sy + j,
                                    color, target, resolvedPixelMode,
                                )
                            }
                        }
                    }
                }
                sx += spacingY * scale
            }
        }
    }

    private val monoSpacing = Int2D(0, 8)

    private val fontSpacing =
        arrayOf(
            0x03, 0x25, 0x16, 0x08, 0x07, 0x08, 0x08, 0x04, 0x15, 0x15, 0x08, 0x07, 0x15, 0x07, 0x24, 0x08,
            0x08, 0x17, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x24, 0x15, 0x06, 0x07, 0x16, 0x17,
            0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x17, 0x08, 0x08, 0x17, 0x08, 0x08, 0x08,
            0x08, 0x08, 0x08, 0x08, 0x17, 0x08, 0x08, 0x08, 0x08, 0x17, 0x08, 0x15, 0x08, 0x15, 0x08, 0x08,
            0x24, 0x18, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17, 0x33, 0x17, 0x17, 0x33, 0x18, 0x17, 0x17,
            0x17, 0x17, 0x17, 0x17, 0x07, 0x17, 0x17, 0x18, 0x18, 0x17, 0x17, 0x07, 0x33, 0x07, 0x08, 0x00,
        ).map { Int2D(it shr 4, it and 15) }

    @Suppress("SpellCheckingInspection")
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

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
