package dev.staticsanches.kge.resource

import dev.staticsanches.kge.overridable.KGEOverridable
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Reports resources that were collected without being closed.
 *
 * The engine default logs the leak; a consumer may replace it process-wide via
 * [override][KGEOverridable.Proxy.override].
 */
interface LeakReporterService : KGEOverridable {
    fun report(representation: String)

    companion object :
        KGEOverridable.Proxy<LeakReporterService>(LeakReporterService::class, LoggingLeakReporter),
        LeakReporterService {
        override fun report(representation: String) = delegate.report(representation)
    }
}

/** The engine default: logs the leak through the engine logger. */
private object LoggingLeakReporter : LeakReporterService {
    private val logger = KotlinLogging.logger("KGELeakDetector")

    override fun report(representation: String) {
        logger.error {
            "Resource $representation was not closed and is potentially " +
                "leaking its resources"
        }
    }
}
