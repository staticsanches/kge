package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.resource.ResourceScope

/** The TrueType text capability: it adopts a loaded font into a caller scope. */
interface TtfTextService : KGEOverridable {
    /** Adopts [font] into [scope]: the scope owns and closes it. */
    fun createResources(
        scope: ResourceScope,
        font: Font,
    )

    /** The pixel box of [text] at [sizePx] with tab stops of [tabSizeInSpaces] spaces. */
    fun getTextSize(
        font: Font,
        text: String,
        sizePx: Int,
        tabSizeInSpaces: Int,
    ): Int2D

    /**
     * Draws [text] into [target] from the ([x], [y]) line-box top-left: the tint
     * carries each glyph's coverage, and only [Pixel.Mode.Custom] changes the blend.
     */
    fun drawString(
        font: Font,
        target: Pixmap.Mutable,
        x: Int,
        y: Int,
        text: String,
        sizePx: Int,
        color: Pixel,
        scale: Int,
        tabSizeInSpaces: Int,
        mode: Pixel.Mode,
    )

    /** The [Int2D] form of [drawString]. */
    fun drawString(
        font: Font,
        target: Pixmap.Mutable,
        position: Int2D,
        text: String,
        sizePx: Int,
        color: Pixel,
        scale: Int,
        tabSizeInSpaces: Int,
        mode: Pixel.Mode,
    ): Unit = drawString(font, target, position.x, position.y, text, sizePx, color, scale, tabSizeInSpaces, mode)

    /**
     * Queues one partial-decal instance per ink glyph of [text] from [position], tinted by
     * [color] and scaled by [scale]; the carried decal belongs to [font] and must not be updated.
     */
    fun drawStringDecal(
        font: Font,
        position: Float2D,
        text: String,
        sizePx: Int,
        color: Pixel,
        scale: Float2D,
        tabSizeInSpaces: Int,
        screenSize: Int2D,
        decalMode: Decal.Mode,
        decalStructure: Decal.Structure,
        decalInstanceCollector: (DecalInstance) -> Unit,
    )

    @OptIn(KGESensitiveAPI::class)
    companion object :
        KGEOverridable.Proxy<TtfTextService>(TtfTextService::class, TtfTextServiceDefault),
        TtfTextService {
        override fun createResources(
            scope: ResourceScope,
            font: Font,
        ) = delegate.createResources(scope, font)

        override fun getTextSize(
            font: Font,
            text: String,
            sizePx: Int,
            tabSizeInSpaces: Int,
        ) = delegate.getTextSize(font, text, sizePx, tabSizeInSpaces)

        override fun drawString(
            font: Font,
            target: Pixmap.Mutable,
            x: Int,
            y: Int,
            text: String,
            sizePx: Int,
            color: Pixel,
            scale: Int,
            tabSizeInSpaces: Int,
            mode: Pixel.Mode,
        ) = delegate.drawString(font, target, x, y, text, sizePx, color, scale, tabSizeInSpaces, mode)

        override fun drawString(
            font: Font,
            target: Pixmap.Mutable,
            position: Int2D,
            text: String,
            sizePx: Int,
            color: Pixel,
            scale: Int,
            tabSizeInSpaces: Int,
            mode: Pixel.Mode,
        ) = delegate.drawString(font, target, position, text, sizePx, color, scale, tabSizeInSpaces, mode)

        override fun drawStringDecal(
            font: Font,
            position: Float2D,
            text: String,
            sizePx: Int,
            color: Pixel,
            scale: Float2D,
            tabSizeInSpaces: Int,
            screenSize: Int2D,
            decalMode: Decal.Mode,
            decalStructure: Decal.Structure,
            decalInstanceCollector: (DecalInstance) -> Unit,
        ) = delegate.drawStringDecal(
            font,
            position,
            text,
            sizePx,
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

private object TtfTextServiceDefault : TtfTextService {
    override fun createResources(
        scope: ResourceScope,
        font: Font,
    ) {
        scope.register(FontKey(), font)
    }

    override fun getTextSize(
        font: Font,
        text: String,
        sizePx: Int,
        tabSizeInSpaces: Int,
    ): Int2D = measureText(font, text, sizePx, tabSizeInSpaces)

    override fun drawString(
        font: Font,
        target: Pixmap.Mutable,
        x: Int,
        y: Int,
        text: String,
        sizePx: Int,
        color: Pixel,
        scale: Int,
        tabSizeInSpaces: Int,
        mode: Pixel.Mode,
    ) = drawText(font, target, x, y, text, sizePx, color, scale, tabSizeInSpaces, mode)

    override fun drawStringDecal(
        font: Font,
        position: Float2D,
        text: String,
        sizePx: Int,
        color: Pixel,
        scale: Float2D,
        tabSizeInSpaces: Int,
        screenSize: Int2D,
        decalMode: Decal.Mode,
        decalStructure: Decal.Structure,
        decalInstanceCollector: (DecalInstance) -> Unit,
    ) = drawStringDecalText(
        font,
        position,
        text,
        sizePx,
        color,
        scale,
        tabSizeInSpaces,
        screenSize,
        decalMode,
        decalStructure,
        decalInstanceCollector,
    )
}

/** A fresh key per adoption; identity matching lets one scope own several fonts. */
private class FontKey : ResourceScope.Key<Font>
