package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.image.WebImageCodec
import dev.staticsanches.kge.resource.ResourceWrapper

/**
 * Web backend: the browser-native codec primitive, shared by js and wasmJs.
 * The format is auto-detected by `createImageBitmap`; [WebImageCodec] owns the
 * RGBA wrapper hand-off.
 */
internal actual suspend fun decodeImageBytes(
    source: ByteBuffer,
    consume: (width: Int, height: Int, pixels: ResourceWrapper<ByteBuffer>) -> Unit,
) = WebImageCodec.decode(source, consume)
