package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Decal
import dev.staticsanches.kge.image.PartialDecal
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.FloatOneByOne
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer

interface DrawPartialDecalAddon : WindowDependentAddon {
    fun drawPartialDecal(
        position: Float2D,
        partialDecal: PartialDecal,
        scale: Float2D = FloatOneByOne,
        tint: Pixel = Colors.WHITE,
    ) = Rasterizer.drawPartialDecal(
        position,
        partialDecal,
        scale,
        tint,
        screenSize,
        invertedScreenSize,
        decalMode,
        decalStructure,
        targetLayer,
    )

    fun drawPartialDecal(
        position: Float2D,
        decal: Decal,
        sourcePosition: Int2D,
        sourceSize: Int2D,
        scale: Float2D = FloatOneByOne,
        tint: Pixel = Colors.WHITE,
    ) = Rasterizer.drawPartialDecal(
        position,
        decal,
        sourcePosition,
        sourceSize,
        scale,
        tint,
        screenSize,
        invertedScreenSize,
        decalMode,
        decalStructure,
        targetLayer,
    )
}
