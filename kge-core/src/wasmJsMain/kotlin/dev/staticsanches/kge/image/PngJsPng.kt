@file:JsModule("pngjs/browser.js")
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@file:Suppress("unused")

package dev.staticsanches.kge.image

/**
 * The pngjs `PNG` entry point: decode via [sync.read], encode via
 * [sync.write]. Pixels are the raw row-major R,G,B,A bytes a [Sprite] stores.
 * Width, height and data are plain writable properties — an encoding target
 * is assembled on a no-arg instance.
 *
 * The wasm target imports ES modules; node resolves the `pngjs/browser.js`
 * subpath (the standalone, browser-safe build) by its exact file name, and
 * exposes `PNG` as a named export the wasm namespace import binds.
 */
internal external class PNG {
    var width: Int
    var height: Int
    var data: Buffer

    companion object {
        val sync: PNGSync
    }
}

internal external interface PNGSync {
    fun read(data: Buffer): PNG

    fun write(png: PNG): Buffer
}
