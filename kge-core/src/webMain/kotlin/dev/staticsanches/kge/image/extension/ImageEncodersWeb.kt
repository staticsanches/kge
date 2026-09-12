package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.WebImageCodec

/**
 * Web backend: the browser-native codec primitive, shared by js and wasmJs.
 * Encode draws the sprite to a canvas and reads it back through
 * `toDataURL("image/png"|"image/jpeg")`; see [WebImageCodec] for the details
 * and the ownership of the transient buffers.
 */
internal actual fun encodePngBytes(sprite: Sprite): ByteArray = WebImageCodec.encodePng(sprite)

internal actual fun encodeJpegBytes(sprite: Sprite): ByteArray = WebImageCodec.encodeJpeg(sprite)
