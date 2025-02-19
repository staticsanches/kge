package dev.staticsanches.kge.image

import dev.staticsanches.kge.rasterizer.Viewport
import java.nio.ByteBuffer

interface WithOptionalRGBAData : Viewport.Bounded {
    val rgbaData: ByteBuffer?
        get() = null
}

interface WithRGBAData : WithOptionalRGBAData {
    override val rgbaData: ByteBuffer
}
