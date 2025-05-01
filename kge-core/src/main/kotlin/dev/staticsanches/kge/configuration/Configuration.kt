package dev.staticsanches.kge.configuration

import dev.staticsanches.kge.renderer.GL33Renderer
import dev.staticsanches.kge.renderer.Renderer
import io.github.oshai.kotlinlogging.KotlinLogging

data object Configuration {
    var defaultRenderer: Renderer = GL33Renderer

    var glyphChartDimension: Int = 512
        set(value) {
            check(value > 1 && (value and (value - 1)) == 0) {
                "The chart dimension must be positive and a power of 2, and it is not: $value"
            }
            field = value
        }

    var glyphChartLineHeightThreshold: Double = .7
        set(value) {
            check(value > 0 && value < 1) { "The threshold must be in (0, 1), and it is not: $value" }
            field = value
        }

    var glyphChartLineHeightIncrease: Double = 1.1
        set(value) {
            check(value > 1) { "The increase must be greater than 1, and it is not: $value" }
            field = value
        }

    init {
        try {
            val dimension = System.getProperty("dev.staticsanches.kge.glyphChartDimension")?.toInt()
            if (dimension != null) {
                glyphChartDimension = dimension
            }
        } catch (t: Throwable) {
            logger.error(t) { "Invalid chart dimension for glyphs, using ${glyphChartDimension}px: " + t.message }
        }

        try {
            val threshold = System.getProperty("dev.staticsanches.kge.glyphChartLineHeightThreshold")?.toDouble()
            if (threshold != null) {
                glyphChartLineHeightThreshold = threshold
            }
        } catch (t: Throwable) {
            logger.error(t) { "Invalid threshold for line height, using $glyphChartLineHeightThreshold: " + t.message }
        }

        try {
            val increase = System.getProperty("dev.staticsanches.kge.glyphChartLineHeightIncrease")?.toDouble()
            if (increase != null) {
                glyphChartLineHeightIncrease = increase
            }
        } catch (t: Throwable) {
            logger.error(t) { "Invalid increase for line height, using $glyphChartLineHeightIncrease: " + t.message }
        }
    }
}

private val logger = KotlinLogging.logger {}
