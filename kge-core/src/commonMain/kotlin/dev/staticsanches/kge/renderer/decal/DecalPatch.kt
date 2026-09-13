package dev.staticsanches.kge.renderer.decal

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.math.vector.Float2D

/**
 * A [Decal] plus the four named normalized texture coordinates [bl], [tl], [tr],
 * [br] — olc's `DecalPatch`.
 *
 * Built through [Decal.patch] or the sensitive constructor for a custom patch.
 */
class DecalPatch
    @KGESensitiveAPI
    constructor(
        /** The texture the coordinates sample. */
        val decal: Decal,
        /** The bottom-left texture coordinate. */
        val bl: Float2D,
        /** The top-left texture coordinate. */
        val tl: Float2D,
        /** The top-right texture coordinate. */
        val tr: Float2D,
        /** The bottom-right texture coordinate. */
        val br: Float2D,
    )
