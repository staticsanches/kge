package dev.staticsanches.kge.engine.state

import org.lwjgl.glfw.GLFW

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
    private var frameTimer: Double = 1.0
    private var timeMark1: Double = 0.0
    private var timeMark2: Double = 0.0

    override fun tick(): Double {
        timeMark2 = GLFW.glfwGetTime()
        val elapsedTime = timeMark2 - timeMark1
        timeMark1 = timeMark2
        frameCount++
        frameTimer += elapsedTime
        if (frameTimer >= 1.0) {
            fpsUpdater(frameCount)
            frameCount = 0
            frameTimer -= 1.0
        }
        return elapsedTime
    }

    override fun reset() {
        frameCount = 0
        frameTimer = 1.0
        timeMark1 = GLFW.glfwGetTime()
        timeMark2 = timeMark1
    }
}
