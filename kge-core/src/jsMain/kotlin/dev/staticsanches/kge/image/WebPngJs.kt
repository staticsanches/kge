package dev.staticsanches.kge.image

internal actual object WebPngJs {
    actual fun decodePng(pngBytes: ByteArray): WebPngSurface {
        val fileBytes = Buffer(pngBytes.size)
        for (i in pngBytes.indices) {
            fileBytes.writeUInt8(pngBytes[i].toInt() and 0xFF, i)
        }
        val png = PNG.sync.read(fileBytes)
        val rgba = ByteArray(png.width * png.height * Int.SIZE_BYTES)
        for (i in rgba.indices) {
            rgba[i] = png.data.readUInt8(i).toByte()
        }
        return WebPngSurface(png.width, png.height, rgba)
    }

    actual fun encodePng(
        width: Int,
        height: Int,
        rgba: ByteArray,
    ): ByteArray {
        val pixels = Buffer(rgba.size)
        for (i in rgba.indices) {
            pixels.writeUInt8(rgba[i].toInt() and 0xFF, i)
        }
        val png = PNG()
        png.width = width
        png.height = height
        png.data = pixels
        val pngBytes = PNG.sync.write(png)
        val result = ByteArray(pngBytes.length)
        for (i in result.indices) {
            result[i] = pngBytes.readUInt8(i).toByte()
        }
        return result
    }
}
