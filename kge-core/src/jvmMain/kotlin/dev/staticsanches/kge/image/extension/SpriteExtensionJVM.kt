package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.service.SpriteService
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.nio.channels.WritableByteChannel

fun Sprite.Companion.loadPNG(
    fileName: String,
    sampleMode: Sprite.SampleMode = Sprite.SampleMode.NORMAL,
    name: String? = null,
): Sprite = SpriteService.loadPNG(fileName, sampleMode, name)

fun Sprite.Companion.loadPNG(
    url: URL,
    sampleMode: Sprite.SampleMode = Sprite.SampleMode.NORMAL,
    name: String? = null,
): Sprite = SpriteService.loadPNG(url, sampleMode, name)

fun Sprite.Companion.loadPNG(
    isProvider: () -> InputStream,
    sampleMode: Sprite.SampleMode = Sprite.SampleMode.NORMAL,
    name: String? = null,
): Sprite = SpriteService.loadPNG(isProvider, sampleMode, name)

fun Sprite.Companion.loadPNGFromBase64(
    data: String,
    sampleMode: Sprite.SampleMode = Sprite.SampleMode.NORMAL,
    name: String? = null,
): Sprite = SpriteService.loadPNGFromBase64(data, sampleMode, name)

fun Sprite.writePNG(fileName: String) = SpriteService.writePNG(this, fileName)

fun Sprite.writePNG(os: OutputStream) = SpriteService.writePNG(this, os)

fun Sprite.writePNG(channel: WritableByteChannel) = SpriteService.writePNG(this, channel)
