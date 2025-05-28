@file:Suppress("unused")

package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Decal
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.rasterizer.Rasterizer

interface DrawPartialDecalAddon : WindowDependentAddon {
    fun drawPartialDecal(
        position: Float2D,
        decal: Decal,
        sourcePosition: Float2D,
        sourceSize: Float2D,
        scale: Float2D = Float2D.oneByOne,
        tint: Pixel = Colors.WHITE,
    ) = Rasterizer.drawPartialDecal(
        position = position,
        decal = decal,
        sourcePosition = sourcePosition,
        sourceSize = sourceSize,
        scale = scale,
        tint = tint,
        screenSize = screenSize,
        invertedScreenSize = invertedScreenSize,
        decalMode = decalMode,
        decalStructure = decalStructure,
        decalInstanceCollector = targetLayer.decalInstances::add,
    )
}
