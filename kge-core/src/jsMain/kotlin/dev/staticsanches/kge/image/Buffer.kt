@file:JsModule("buffer")
@file:JsNonModule
@file:Suppress("unused")

package dev.staticsanches.kge.image

/**
 * The pngjs byte buffer. The pngjs parser only accepts real `Buffer`s (they
 * carry `readUInt32BE` & co.). Node resolves the `buffer` module to its
 * builtin; browser bundles resolve it to the npm `buffer` package — both
 * expose the same constructor and accessors, so the codec is environment-
 * independent.
 */
internal external class Buffer {
    val length: Int

    constructor(sizeInBytes: Int)

    fun readUInt8(offset: Int): Int

    fun writeUInt8(
        value: Int,
        offset: Int,
    ): Int
}
