package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.image.WebImageCodec
import dev.staticsanches.kge.resource.ResourceWrapper

/**
 * Web backend: the browser-native codec primitive, shared by js and wasmJs.
 * The encoded bytes are sniffed by `createImageBitmap`, so the input format is
 * auto-detected; see [WebImageCodec] for the ownership of the RGBA wrapper.
 */
internal actual suspend fun decodeImageBytes(
    source: ByteBuffer,
    consume: (width: Int, height: Int, pixels: ResourceWrapper<ByteBuffer>) -> Unit,
) = WebImageCodec.decode(source, consume)
