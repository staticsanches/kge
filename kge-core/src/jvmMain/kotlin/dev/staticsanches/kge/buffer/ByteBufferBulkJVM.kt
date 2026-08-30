package dev.staticsanches.kge.buffer

import dev.staticsanches.kge.utils.BytesSize.INT

actual fun ByteBuffer.fillInts(
    byteIndex: Int,
    length: Int,
    value: Int,
) {
    var index = byteIndex
    for (i in 0..<length) {
        putInt(index, value)
        index += INT
    }
}

actual fun ByteBuffer.copyInts(
    destByteIndex: Int,
    source: ByteBuffer,
    sourceByteIndex: Int,
    length: Int,
) {
    var destIndex = destByteIndex
    var sourceIndex = sourceByteIndex
    for (i in 0..<length) {
        putInt(destIndex, source.getInt(sourceIndex))
        destIndex += INT
        sourceIndex += INT
    }
}
