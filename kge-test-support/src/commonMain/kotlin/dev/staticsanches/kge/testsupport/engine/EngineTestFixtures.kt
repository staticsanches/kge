package dev.staticsanches.kge.testsupport.engine

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.Driver
import dev.staticsanches.kge.engine.DriverService
import dev.staticsanches.kge.engine.WindowConfig
import dev.staticsanches.kge.renderer.gl.service.GLService
import dev.staticsanches.kge.testsupport.gl.RecordingGLService

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

@OptIn(KGESensitiveAPI::class)
fun installDriver(driver: Driver): FakeDriverService {
    val service = FakeDriverService(driver)
    DriverService.override(service)
    return service
}

@OptIn(KGESensitiveAPI::class)
fun installGl(): RecordingGLService {
    val service = RecordingGLService()
    GLService.override(service)
    return service
}
