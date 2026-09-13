package dev.staticsanches.kge.rasterizer

import dev.staticsanches.kge.rasterizer.service.BlitService
import dev.staticsanches.kge.rasterizer.service.ClipService
import dev.staticsanches.kge.rasterizer.service.DrawService
import dev.staticsanches.kge.rasterizer.service.FillService
import dev.staticsanches.kge.rasterizer.service.OutlineService

/**
 * CPU raster primitives over a [dev.staticsanches.kge.image.Pixmap.Mutable],
 * delegating to the five independently overridable sub-services ([DrawService],
 * [ClipService], [OutlineService], [FillService], [BlitService]). Composite
 * primitives resolve their sub-draws through the *active* sub-service, so a
 * decorator override is observed by the composites too.
 */
data object Rasterizer :
    DrawService by DrawService,
    ClipService by ClipService,
    OutlineService by OutlineService,
    FillService by FillService,
    BlitService by BlitService
