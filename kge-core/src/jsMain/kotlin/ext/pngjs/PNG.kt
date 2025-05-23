@file:JsModule("pngjs/browser")
@file:JsNonModule
@file:Suppress("unused")

package ext.pngjs

import js.buffer.ArrayBuffer
import js.buffer.ArrayBufferLike
import js.core.JsAny
import js.errors.JsErrorLike
import js.typedarrays.Uint8Array

internal external class PNG(
    args: JsAny = definedExternally,
) {
    val data: Uint8Array<ArrayBufferLike>
    val width: Int
    val height: Int

    fun parse(
        data: ArrayBufferLike,
        callback: (error: JsErrorLike?, png: PNG) -> Unit,
    )

    companion object {
        val sync: PNGSync
    }
}

internal external interface PNGSync {
    fun read(data: ArrayBufferLike): PNG

    fun write(png: PNG): Uint8Array<ArrayBuffer>
}
