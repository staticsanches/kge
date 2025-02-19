package dev.staticsanches.kge.configuration

import io.github.oshai.kotlinlogging.KotlinLogging

data object Configuration {
    var useOpenGL11: Boolean = System.getProperty("dev.staticsanches.kge.useOpenGL11").toBoolean()

    var glyphChartWidthHeight: Int = 512
        set(value) {
            check(value > 1 && (value and (value - 1)) == 0) {
                "The chart dimension must be positive and a power of 2, and it is not: $value"
            }
            field = value
        }

    var glyphChartRowHeightThreshold: Double = .7
        set(value) {
            check(value > 0 && value < 1) { "The threshold must be in (0, 1), and it is not: $value" }
            field = value
        }

    var glyphChartRowHeightIncrease: Double = 1.1
        set(value) {
            check(value > 1) { "The increase must be greater than 1, and it is not: $value" }
            field = value
        }

    init {
        try {
            val dimension = System.getProperty("dev.staticsanches.kge.glyphChartDimension")?.toInt()
            if (dimension != null) {
                glyphChartWidthHeight = dimension
            }
        } catch (t: Throwable) {
            logger.error(t) { "Invalid chart dimension for glyphs, using ${glyphChartWidthHeight}px: " + t.message }
        }

        try {
            val threshold = System.getProperty("dev.staticsanches.kge.glyphChartLineHeightThreshold")?.toDouble()
            if (threshold != null) {
                glyphChartRowHeightThreshold = threshold
            }
        } catch (t: Throwable) {
            logger.error(t) { "Invalid threshold for row height, using $glyphChartRowHeightThreshold: " + t.message }
        }

        try {
            val increase = System.getProperty("dev.staticsanches.kge.glyphChartLineHeightIncrease")?.toDouble()
            if (increase != null) {
                glyphChartRowHeightIncrease = increase
            }
        } catch (t: Throwable) {
            logger.error(t) { "Invalid increase for row height, using $glyphChartRowHeightIncrease: " + t.message }
        }
    }
}

private val logger = KotlinLogging.logger {}
