package dev.staticsanches.kge.rasterizer

import dev.staticsanches.kge.rasterizer.service.ClearService
import dev.staticsanches.kge.rasterizer.service.DrawCircleService
import dev.staticsanches.kge.rasterizer.service.DrawLineService
import dev.staticsanches.kge.rasterizer.service.DrawRectService
import dev.staticsanches.kge.rasterizer.service.DrawService
import dev.staticsanches.kge.rasterizer.service.DrawSpriteService
import dev.staticsanches.kge.rasterizer.service.DrawStringService
import dev.staticsanches.kge.rasterizer.service.DrawTriangleService
import dev.staticsanches.kge.rasterizer.service.FillCircleService
import dev.staticsanches.kge.rasterizer.service.FillRectService
import dev.staticsanches.kge.rasterizer.service.FillTriangleService

data object Rasterizer :
    ClearService by ClearService,
    DrawCircleService by DrawCircleService,
    DrawLineService by DrawLineService,
    DrawRectService by DrawRectService,
    DrawService by DrawService,
    DrawSpriteService by DrawSpriteService,
    DrawStringService by DrawStringService,
    DrawTriangleService by DrawTriangleService,
    FillCircleService by FillCircleService,
    FillRectService by FillRectService,
    FillTriangleService by FillTriangleService {
    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
