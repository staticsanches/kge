package dev.staticsanches.kge.engine

import dev.staticsanches.kge.overridable.KGEOverridable

/**
 * Creates the engine's [Driver]. The engine-fixed platform behavior is
 * overridable process-wide via
 * [override][KGEOverridable.Proxy.override]: tests fake the window, and a web
 * app supplies the service bound to its own canvas.
 */
interface DriverService : KGEOverridable {
    /** Opens the window/context described by [config]. */
    fun create(config: WindowConfig): Driver

    companion object :
        KGEOverridable.Proxy<DriverService>(DriverService::class, driverServiceDefault),
        DriverService {
        override fun create(config: WindowConfig): Driver = delegate.create(config)
    }
}

internal expect val driverServiceDefault: DriverService
