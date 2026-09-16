package dev.staticsanches.kge.engine

import dev.staticsanches.kge.renderer.gl.RecordingGLService
import dev.staticsanches.kge.renderer.gl.service.GLService
import kotlin.time.Duration

private val testWindowConfig = WindowConfig(screenWidth = 320, screenHeight = 240)

/** A [DriverService] handing out one recorded driver and counting creations. */
class FakeDriverService(
    private val driver: Driver,
) : DriverService {
    var createCount = 0
        private set

    override fun create(config: WindowConfig): Driver {
        createCount++
        return driver
    }
}

fun installDriver(driver: Driver): FakeDriverService {
    val service = FakeDriverService(driver)
    DriverService.override(service)
    return service
}

fun installGl(): RecordingGLService {
    val service = RecordingGLService()
    GLService.override(service)
    return service
}

/** An [Engine] whose callbacks are scripted by lambdas and counted. */
class ScriptedEngine(
    config: WindowConfig = testWindowConfig,
    private val onCreate: suspend (ScriptedEngine) -> Boolean = { true },
    private val onUpdate: suspend (ScriptedEngine, Duration) -> Boolean = { _, _ -> true },
    private val onDestroy: suspend (ScriptedEngine) -> Boolean = { true },
) : Engine(config) {
    var createCount = 0
        private set
    var destroyCount = 0
        private set
    val updateElapsed = mutableListOf<Duration>()

    override suspend fun onUserCreate(): Boolean {
        createCount++
        return onCreate(this)
    }

    override suspend fun onUserUpdate(elapsed: Duration): Boolean {
        updateElapsed += elapsed
        return onUpdate(this, elapsed)
    }

    override suspend fun onUserDestroy(): Boolean {
        destroyCount++
        return onDestroy(this)
    }
}
