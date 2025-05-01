package dev.staticsanches.kge.spi

import java.util.ServiceLoader

/**
 * Marks types that are extensible by Java SPI.
 */
interface KGESPIExtensible {
    /**
     * Defines the priority of this service. If two services are located, the one with higher priority is chosen.
     *
     * @see [getOptionalWithHigherPriority]
     */
    val servicePriority: Int
        get() = Int.MIN_VALUE

    companion object {
        /**
         * Optionally loads the service with higher priority.
         *
         * @see [ServiceLoader.load]
         */
        inline fun <reified T : KGESPIExtensible> getOptionalWithHigherPriority(): T? =
            ServiceLoader.load(T::class.java).maxByOrNull { it.servicePriority }
    }
}
