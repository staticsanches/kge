package dev.staticsanches.kge.rasterizer

import dev.staticsanches.kge.rasterizer.service.DefaultDrawLineService
import dev.staticsanches.kge.rasterizer.service.DefaultDrawService
import dev.staticsanches.kge.rasterizer.service.DrawLineService
import dev.staticsanches.kge.rasterizer.service.DrawService
import dev.staticsanches.kge.spi.KGESPIExtensible

data object Rasterizer :
    DrawService by KGESPIExtensible.getOptionalWithHigherPriority() ?: DefaultDrawService,
    DrawLineService by KGESPIExtensible.getOptionalWithHigherPriority() ?: DefaultDrawLineService {
    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
