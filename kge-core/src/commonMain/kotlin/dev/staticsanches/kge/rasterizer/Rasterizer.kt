package dev.staticsanches.kge.rasterizer

import dev.staticsanches.kge.rasterizer.service.ClearService
import dev.staticsanches.kge.rasterizer.service.ClipLineService
import dev.staticsanches.kge.rasterizer.service.DrawCircleService
import dev.staticsanches.kge.rasterizer.service.DrawLineService
import dev.staticsanches.kge.rasterizer.service.DrawRectService
import dev.staticsanches.kge.rasterizer.service.DrawService
import dev.staticsanches.kge.rasterizer.service.DrawStringService
import dev.staticsanches.kge.rasterizer.service.FillCircleService
import dev.staticsanches.kge.rasterizer.service.FillRectService

data object Rasterizer :
    ClearService by ClearService,
    ClipLineService by ClipLineService,
    DrawCircleService by DrawCircleService,
    DrawLineService by DrawLineService,
    DrawRectService by DrawRectService,
    DrawService by DrawService,
    DrawStringService by DrawStringService,
    FillCircleService by FillCircleService,
    FillRectService by FillRectService {
    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
