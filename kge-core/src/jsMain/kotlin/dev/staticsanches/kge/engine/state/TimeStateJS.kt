package dev.staticsanches.kge.engine.state

import kotlin.js.Date

actual val originalTimeStateClockServiceImplementation: TimeState.ClockService
    get() = DefaultClockService

private data object DefaultClockService : TimeState.ClockService {
    override fun create(fpsUpdater: (Int) -> Unit): TimeState.Clock = DefaultClock(fpsUpdater)

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}

private class DefaultClock(
    private val fpsUpdater: (Int) -> Unit,
) : TimeState.Clock {
    private var frameCount: Int = 0
    private var frameTimer: Double = 1_000.0
    private var timeMark1: Double = 0.0
    private var timeMark2: Double = 0.0

    override fun tick(): Double {
        timeMark2 = Date.now()
        val elapsedTime = timeMark2 - timeMark1
        timeMark1 = timeMark2
        frameCount++
        frameTimer += elapsedTime
        if (frameTimer >= 1_000) {
            fpsUpdater(frameCount)
            frameCount = 0
            frameTimer -= 1_000
        }
        return elapsedTime
    }

    override fun reset() {
        frameCount = 0
        frameTimer = 1_000.0
        timeMark1 = Date.now()
        timeMark2 = timeMark1
    }
}
