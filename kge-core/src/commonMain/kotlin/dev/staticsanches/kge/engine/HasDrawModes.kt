package dev.staticsanches.kge.engine

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.renderer.decal.Decal

/** A carrier of the engine's draw modes. */
interface HasDrawModes {
    /** How raster primitives blend into the draw target. */
    var pixelMode: Pixel.Mode

    /** How decals blend into the draw target. */
    var decalMode: Decal.Mode

    /** How a decal instance's vertex list is assembled into primitives. */
    var decalStructure: Decal.Structure

    /** Suppresses the automatic CPU → GPU upload of a layer before it is composited. */
    var suspendTextureTransfer: Boolean
}
