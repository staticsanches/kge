@file:Suppress("ktlint:standard:filename", "unused")

package dev.staticsanches.kge.extensible

import kotlin.reflect.KClass

actual interface KGEExtensibleService {
    actual val servicePriority: Int

    /**
     * Stores the user registered [KGEExtensibleService].
     */
    actual companion object : HashMap<KClass<out KGEExtensibleService>, MutableList<KGEExtensibleService>>() {
        actual inline fun <reified T : KGEExtensibleService> getOptionalWithHigherPriority(): T? =
            this[T::class]?.maxByOrNull { it.servicePriority } as? T

        fun <T : KGEExtensibleService> register(
            type: KClass<out T>,
            service: T,
        ) = getOrPut(type, ::mutableListOf).add(service)
    }
}
