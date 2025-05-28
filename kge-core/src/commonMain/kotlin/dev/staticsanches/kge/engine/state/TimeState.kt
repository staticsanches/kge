@file:Suppress("unused")

package dev.staticsanches.kge.engine.state

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.extensible.KGEExtensibleService

class TimeState {
    var fps: Int = 0
        private set(value) {
            previousFPS = field
            field = value
        }

    val fpsChanged: Boolean
        get() = previousFPS != fps

    /**
     * In JVM, the default implementation measures the time in seconds.
     * In JS, the default implementation measures the in milliseconds.
     */
    var elapsedTime: Double = 0.0
        private set

    // Control variables
    private val clock = ClockService.create { fps = it }
    private var previousFPS: Int = 0

    @KGESensitiveAPI
    fun tick() {
        elapsedTime = clock.tick()
    }

    @KGESensitiveAPI
    fun reset() = clock.reset()

    interface Clock {
        @KGESensitiveAPI
        fun tick(): Double

        @KGESensitiveAPI
        fun reset()
    }

    interface ClockService : KGEExtensibleService {
        fun create(fpsUpdater: (Int) -> Unit): Clock

        companion object : ClockService by KGEExtensibleService.getOptionalWithHigherPriority()
            ?: originalTimeStateClockServiceImplementation
    }
}

expect val originalTimeStateClockServiceImplementation: TimeState.ClockService
