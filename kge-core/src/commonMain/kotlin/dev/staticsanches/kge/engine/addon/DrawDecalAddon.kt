@file:Suppress("unused")

package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Decal
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.rasterizer.Rasterizer

interface DrawDecalAddon : WindowDependentAddon {
    fun drawDecal(
        position: Float2D,
        decal: Decal,
        scale: Float2D = Float2D.oneByOne,
        tint: Pixel = Colors.WHITE,
    ) = Rasterizer.drawDecal(
        position = position,
        decal = decal,
        scale = scale,
        tint = tint,
        invertedScreenSize = invertedScreenSize,
        decalMode = decalMode,
        decalStructure = decalStructure,
        decalInstanceCollector = targetLayer.decalInstances::add,
    )
}
