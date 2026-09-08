@file:JsModule("pngjs/browser")
@file:JsNonModule
@file:Suppress("unused")

package dev.staticsanches.kge.image

/**
 * The pngjs `PNG` entry point (`pngjs/browser`, a self-contained CommonJS
 * build): decode via [sync.read], encode via [sync.write]. Pixels are the raw
 * row-major R,G,B,A bytes a [Sprite] stores. Width, height and data are plain
 * writable properties — an encoding target is assembled on a no-arg instance.
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
