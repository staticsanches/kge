package dev.staticsanches.kge.rasterizer

import dev.staticsanches.kge.rasterizer.service.ClearService
import dev.staticsanches.kge.rasterizer.service.ClipLineService
import dev.staticsanches.kge.rasterizer.service.DrawLineService
import dev.staticsanches.kge.rasterizer.service.DrawService

data object Rasterizer :
    ClearService by ClearService,
    ClipLineService by ClipLineService,
    DrawLineService by DrawLineService,
    DrawService by DrawService {
    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
