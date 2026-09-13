package dev.staticsanches.kge.time

import kotlin.time.Duration

/** A [TimeService] whose clock advances by [step] on every [elapsed] call. */
internal class FakeTimeService(
    private val step: Duration = Duration.ZERO,
) : TimeService {
    private var current = Duration.ZERO

    override fun elapsed(): Duration =
        current.also {
            current += step
        }
}
