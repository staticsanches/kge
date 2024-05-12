package dev.staticsanches.kge.rasterizer

import dev.staticsanches.kge.rasterizer.service.DrawService
import dev.staticsanches.kge.spi.KGESPIExtensible

data object Rasterizer :
	DrawService by KGESPIExtensible.getWithHigherPriority()
