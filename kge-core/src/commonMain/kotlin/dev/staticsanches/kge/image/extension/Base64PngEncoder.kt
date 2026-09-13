package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.image.ImageService
import dev.staticsanches.kge.image.Sprite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64

/**
 * The default encoder of a [Sprite] to a base64-encoded PNG.
 *
 * The PNG bytes come from [PngEncoder] with a transient buffer released before
 * returning; the returned [String] is not a resource. Runs on
 * [Dispatchers.Default].
 */
object Base64PngEncoder : ImageService.Encoder<String> {
    override suspend fun encode(sprite: Sprite): String =
        withContext(Dispatchers.Default) {
            PngEncoder.encode(sprite).use { Base64.Default.encode(it.resource.asByteArray()) }
        }
}
