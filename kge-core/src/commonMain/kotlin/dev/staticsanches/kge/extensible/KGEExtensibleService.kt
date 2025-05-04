package dev.staticsanches.kge.extensible

expect interface KGEExtensibleService {
    /**
     * Defines the priority of this service. If two services are located, the one with higher priority is chosen.
     *
     * @see [getOptionalWithHigherPriority]
     */
    val servicePriority: Int

    companion object {
        inline fun <reified T : KGEExtensibleService> getOptionalWithHigherPriority(): T?
    }
}
