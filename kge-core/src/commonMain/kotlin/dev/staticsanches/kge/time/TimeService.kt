package dev.staticsanches.kge.time

import dev.staticsanches.kge.overridable.KGEOverridable
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Supplies the engine's monotonic clock.
 *
 * A consumer may replace the whole behavior for the process via
 * [override][KGEOverridable.Proxy.override] — a virtual clock for tests,
 * observed from the next call.
 */
interface TimeService : KGEOverridable {
    /** The monotonic elapsed time from the service origin; never decreases between calls. */
    fun elapsed(): Duration

    companion object :
        KGEOverridable.Proxy<TimeService>(TimeService::class, MonotonicTimeService),
        TimeService {
        override fun elapsed(): Duration = delegate.elapsed()
    }
}

/** The engine default: elapsed monotonic time since process start. */
private object MonotonicTimeService : TimeService {
    private val start = TimeSource.Monotonic.markNow()

    override fun elapsed(): Duration = start.elapsedNow()
}

/**
 * The user-facing clock: [elapsed][TimeService.elapsed] resolves the current
 * [TimeService] on every call, so an override is observed immediately.
 */
object Time : TimeService by TimeService
