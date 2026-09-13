package dev.staticsanches.kge.renderer.decal

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.math.vector.Float2D

/**
 * A [Decal] plus four normalized texture coordinates, in the order bottom-left,
 * top-left, top-right, bottom-right — olc's `DecalPatch`.
 *
 * The coordinates are snapshotted at construction, so a patch is immutable.
 * Built through [Decal.patch] or the sensitive constructor for a custom patch.
 */
class DecalPatch
    @KGESensitiveAPI
    constructor(
        /** The texture the coordinates sample. */
        val decal: Decal,
        coords: List<Float2D>,
    ) {
        /** The four texture coordinates: BL, TL, TR, BR. */
        val coords: List<Float2D> = coords.toList()
    }
