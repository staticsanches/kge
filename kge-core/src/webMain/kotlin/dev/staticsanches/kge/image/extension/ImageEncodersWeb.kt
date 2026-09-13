package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.WebImageCodec

/**
 * Web backend: the browser-native codec primitive, shared by js and wasmJs.
 * See [WebImageCodec] for the encoding details and buffer ownership.
 */
internal actual fun encodePngBytes(sprite: Sprite): ByteArray = WebImageCodec.encodePng(sprite)

internal actual fun encodeJpegBytes(sprite: Sprite): ByteArray = WebImageCodec.encodeJpeg(sprite)
