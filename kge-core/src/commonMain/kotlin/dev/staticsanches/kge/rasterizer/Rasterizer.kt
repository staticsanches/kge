package dev.staticsanches.kge.rasterizer

import dev.staticsanches.kge.rasterizer.service.ClipService
import dev.staticsanches.kge.rasterizer.service.DrawService
import dev.staticsanches.kge.rasterizer.service.DrawSpriteService
import dev.staticsanches.kge.rasterizer.service.FillService
import dev.staticsanches.kge.rasterizer.service.OutlineService

/**
 * CPU raster primitives over a [dev.staticsanches.kge.image.MutablePixmap] —
 * the single call site that aggregates the five raster sub-services.
 *
 * The primitives live in independently overridable services grouped by scope:
 * [DrawService] (the per-pixel mode-resolving write every painted cell goes
 * through), [ClipService] (the line clip resolved before every
 * [OutlineService.drawLine]), [OutlineService] (lines, rectangle rings,
 * circles and triangle outlines), [FillService] (solid rectangles, circles,
 * triangles) and [DrawSpriteService] (blits). A composite primitive resolves
 * its sub-draws through the *active* sub-service, so a decorator override of
 * any sub-service is observed by the composites that consume it too. This
 * object delegates every method to its sub-service companion, whose calls
 * resolve the current implementation.
 */
data object Rasterizer :
    DrawService by DrawService,
    ClipService by ClipService,
    OutlineService by OutlineService,
    FillService by FillService,
    DrawSpriteService by DrawSpriteService
