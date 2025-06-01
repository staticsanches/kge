package dev.staticsanches.kge.buffer

actual class ByteOrder private constructor(
    private val name: String,
) {
    override fun toString(): String = name

    companion object {
        val bigEndian: ByteOrder = ByteOrder("BIG_ENDIAN")
        val littleEndian: ByteOrder = ByteOrder("LITTLE_ENDIAN")
        val nativeOrder =
            if (js("new Uint8Array(new Uint16Array([1]).buffer)[0] === 0")) bigEndian else littleEndian
    }
}

actual val ByteOrder.isNative: Boolean
    get() = this === ByteOrder.nativeOrder
