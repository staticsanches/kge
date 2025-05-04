@file:Suppress("ktlint:standard:filename")

package dev.staticsanches.kge.extensible

import java.util.ServiceLoader

actual interface KGEExtensibleService {
    actual val servicePriority: Int

    actual companion object {
        actual inline fun <reified T : KGEExtensibleService> getOptionalWithHigherPriority(): T? =
            ServiceLoader.load(T::class.java).maxByOrNull { it.servicePriority }
    }
}
