@file:Suppress("MemberVisibilityCanBePrivate")

package dev.staticsanches.kge.engine.state

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import org.lwjgl.glfw.GLFW

class TimeState {
    var fps: Int = 0
        private set(value) {
            previousFPS = field
            field = value
        }

    val fpsChanged: Boolean
        get() = previousFPS != fps

    var elapsedTime: Double = 0.0
        private set

    // Control variables
    private var previousFPS: Int = 0
    private var frameCount: Int = 0
    private var frameTimer: Double = 1.0
    private var timeMark1: Double = 0.0
    private var timeMark2: Double = 0.0

    @KGESensitiveAPI
    fun tick() {
        timeMark2 = GLFW.glfwGetTime()
        elapsedTime = timeMark2 - timeMark1
        timeMark1 = timeMark2
        frameCount++
        frameTimer += elapsedTime
        if (frameTimer >= 1.0) {
            fps = frameCount
            frameCount = 0
            frameTimer -= 1.0
        }
    }

    @KGESensitiveAPI
    fun reset() {
        frameCount = 0
        frameTimer = 1.0
        timeMark1 = GLFW.glfwGetTime()
        timeMark2 = timeMark1
    }
}
