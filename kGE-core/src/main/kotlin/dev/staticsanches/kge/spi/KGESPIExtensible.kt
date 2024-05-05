package dev.staticsanches.kge.spi

import java.util.*

/**
 * Marks types that are extensible by Java SPI.
 */
interface KGESPIExtensible {

	/**
	 * Defines the priority of this service. If two services are located, the one with higher priority is chosen.
	 *
	 * @see [getWithHigherPriority]
	 */
	val servicePriority: Int

	companion object {

		/**
		 * Loads the service with higher priority.
		 *
		 * @see [getOptionalWithHigherPriority]
		 */
		inline fun <reified T : KGESPIExtensible> getWithHigherPriority(): T =
			getOptionalWithHigherPriority() ?: throw RuntimeException("Could not load service ${T::class.java.name}")

		/**
		 * Optionally loads the service with higher priority.
		 *
		 * @see [ServiceLoader.load]
		 */
		inline fun <reified T : KGESPIExtensible> getOptionalWithHigherPriority(): T? =
			ServiceLoader.load(T::class.java).maxByOrNull { it.servicePriority }

	}

}
