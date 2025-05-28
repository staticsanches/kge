@file:Suppress("unused")

package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer

interface DrawStringAddon : WindowDependentAddon {
    fun getTextSize(text: String): Int2D = Rasterizer.getTextSize(text, tabSizeInSpaces)

    fun drawString(
        position: Int2D,
        text: String,
        color: Pixel = Colors.WHITE,
        scale: Int = 1,
    ) {
        Rasterizer.drawString(
            position = position,
            text = text,
            color = color,
            scale = scale,
            tabSizeInSpaces = tabSizeInSpaces,
            target = drawTarget ?: return,
            pixelMode = pixelMode,
            fontSheet = fontSheet.sprite,
        )
    }

    fun drawString(
        x: Int,
        y: Int,
        text: String,
        color: Pixel = Colors.WHITE,
        scale: Int = 1,
    ) {
        Rasterizer.drawString(
            x = x, y = y,
            text = text,
            color = color,
            scale = scale,
            tabSizeInSpaces = tabSizeInSpaces,
            target = drawTarget ?: return,
            pixelMode = pixelMode,
            fontSheet = fontSheet.sprite,
        )
    }

    fun drawStringDecal(
        position: Float2D,
        text: String,
        color: Pixel = Colors.WHITE,
        scale: Float2D = Float2D.oneByOne,
    ) {
        Rasterizer.drawStringDecal(
            position = position,
            text = text,
            color = color,
            scale = scale,
            tabSizeInSpaces = tabSizeInSpaces,
            fontSheet = fontSheet,
            screenSize = screenSize,
            invertedScreenSize = invertedScreenSize,
            decalMode = decalMode,
            decalStructure = decalStructure,
            decalInstanceCollector = targetLayer.decalInstances::add,
        )
    }

    fun getTextSizeProp(text: String): Int2D = Rasterizer.getTextSizeProp(text, tabSizeInSpaces)

    fun drawStringProp(
        position: Int2D,
        text: String,
        color: Pixel = Colors.WHITE,
        scale: Int = 1,
    ) {
        Rasterizer.drawStringProp(
            position = position,
            text = text,
            color = color,
            scale = scale,
            tabSizeInSpaces = tabSizeInSpaces,
            target = drawTarget ?: return,
            pixelMode = pixelMode,
            fontSheet = fontSheet.sprite,
        )
    }

    fun drawStringProp(
        x: Int,
        y: Int,
        text: String,
        color: Pixel = Colors.WHITE,
        scale: Int = 1,
    ) {
        Rasterizer.drawStringProp(
            x = x, y = y,
            text = text,
            color = color,
            scale = scale,
            tabSizeInSpaces = tabSizeInSpaces,
            target = drawTarget ?: return,
            pixelMode = pixelMode,
            fontSheet = fontSheet.sprite,
        )
    }

    fun drawStringPropDecal(
        position: Float2D,
        text: String,
        color: Pixel = Colors.WHITE,
        scale: Float2D = Float2D.oneByOne,
    ) {
        Rasterizer.drawStringPropDecal(
            position = position,
            text = text,
            color = color,
            scale = scale,
            tabSizeInSpaces = tabSizeInSpaces,
            fontSheet = fontSheet,
            screenSize = screenSize,
            invertedScreenSize = invertedScreenSize,
            decalMode = decalMode,
            decalStructure = decalStructure,
            decalInstanceCollector = targetLayer.decalInstances::add,
        )
    }
}
