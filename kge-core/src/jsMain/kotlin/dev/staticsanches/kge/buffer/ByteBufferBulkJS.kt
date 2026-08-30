package dev.staticsanches.kge.buffer

actual fun ByteBuffer.fillInts(
    byteIndex: Int,
    length: Int,
    value: Int,
) {
    fillIntsNative(byteIndex, length, value)
}

actual fun ByteBuffer.copyInts(
    destByteIndex: Int,
    source: ByteBuffer,
    sourceByteIndex: Int,
    length: Int,
) {
    copyIntsNative(destByteIndex, source, sourceByteIndex, length)
}
