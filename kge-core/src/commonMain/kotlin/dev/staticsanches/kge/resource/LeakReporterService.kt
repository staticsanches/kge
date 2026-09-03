package dev.staticsanches.kge.resource

import dev.staticsanches.kge.overridable.KGEOverridable
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Reports resources that were collected without being closed.
 *
 * Leak reporting is an extension capability of the engine: the engine default
 * is the log sink; a consumer may replace it for the whole process via
 * [override][KGEOverridable.Proxy.override] — e.g. a test asserting a leak,
 * or a crash on leak in nightly builds — and every detection is observed from
 * the next call on.
 */
interface LeakReporterService : KGEOverridable {
    /** Reports that the resource represented by [representation] leaked. */
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
