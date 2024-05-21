package dev.staticsanches.kge.rasterizer

import dev.staticsanches.kge.rasterizer.service.ClearService
import dev.staticsanches.kge.rasterizer.service.DefaultClearService
import dev.staticsanches.kge.rasterizer.service.DefaultDrawLineService
import dev.staticsanches.kge.rasterizer.service.DefaultDrawRectService
import dev.staticsanches.kge.rasterizer.service.DefaultDrawService
import dev.staticsanches.kge.rasterizer.service.DefaultDrawTriangleService
import dev.staticsanches.kge.rasterizer.service.DrawLineService
import dev.staticsanches.kge.rasterizer.service.DrawRectService
import dev.staticsanches.kge.rasterizer.service.DrawService
import dev.staticsanches.kge.rasterizer.service.DrawTriangleService
import dev.staticsanches.kge.spi.KGESPIExtensible

data object Rasterizer :
    ClearService by KGESPIExtensible.getOptionalWithHigherPriority() ?: DefaultClearService,
    DrawService by KGESPIExtensible.getOptionalWithHigherPriority() ?: DefaultDrawService,
    DrawLineService by KGESPIExtensible.getOptionalWithHigherPriority() ?: DefaultDrawLineService,
    DrawRectService by KGESPIExtensible.getOptionalWithHigherPriority() ?: DefaultDrawRectService,
    DrawTriangleService by KGESPIExtensible.getOptionalWithHigherPriority() ?: DefaultDrawTriangleService {
    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
