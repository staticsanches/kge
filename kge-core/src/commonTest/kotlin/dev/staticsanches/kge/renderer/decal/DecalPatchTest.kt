package dev.staticsanches.kge.renderer.decal

import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * [DecalPatch]: the decal plus its four normalized texture coordinates in the
 * order bottom-left, top-left, top-right, bottom-right (olc's `DecalPatch`).
 * The two `Decal.patch` factories build that order.
 */
class DecalPatchTest :
    FunSpec({
        test("patch(pos, size) builds the four BL/TL/TR/BR coordinates") {
            withTestDecal { decal ->
                val patch = decal.patch(Int2D(2, 1), Int2D(4, 2))

                patch.decal shouldBe decal
                patch.coords shouldBe
                    listOf(
                        Float2D(0.25f, 0.75f),
                        Float2D(0.25f, 0.25f),
                        Float2D(0.75f, 0.25f),
                        Float2D(0.75f, 0.75f),
                    )
            }
        }

        test("patch(bl, tl, tr, br) stores the explicit coordinates in order") {
            withTestDecal { decal ->
                val bl = Float2D(0.1f, 0.2f)
                val tl = Float2D(0.1f, 0.3f)
                val tr = Float2D(0.4f, 0.3f)
                val br = Float2D(0.4f, 0.2f)

                val patch = decal.patch(bl, tl, tr, br)

                patch.decal shouldBe decal
                patch.coords shouldBe listOf(bl, tl, tr, br)
            }
        }
    })
