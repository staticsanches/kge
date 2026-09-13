package dev.staticsanches.kge.renderer.decal

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The nested decal enumerations are closed to the oracle's scope: the exact value
 * set and order is pinned so a silent widening/reordering is caught. Mipmap
 * filters, mirrored repeat, and clamp-to-border are intentionally absent.
 */
class DecalTypesTest :
    FunSpec({
        test("Decal.Mode is closed to the oracle's six values") {
            Decal.Mode.entries shouldBe
                listOf(
                    Decal.Mode.NORMAL,
                    Decal.Mode.ADDITIVE,
                    Decal.Mode.MULTIPLICATIVE,
                    Decal.Mode.STENCIL,
                    Decal.Mode.ILLUMINATE,
                    Decal.Mode.WIREFRAME,
                )
        }

        test("Decal.Structure is closed to the oracle's four values") {
            Decal.Structure.entries shouldBe
                listOf(
                    Decal.Structure.LINE,
                    Decal.Structure.FAN,
                    Decal.Structure.STRIP,
                    Decal.Structure.LIST,
                )
        }

        test("Decal.Filter is closed to nearest/linear") {
            Decal.Filter.entries shouldBe listOf(Decal.Filter.NEAREST, Decal.Filter.LINEAR)
        }

        test("Decal.Wrap is closed to clamp-to-edge/repeat") {
            Decal.Wrap.entries shouldBe listOf(Decal.Wrap.CLAMP_TO_EDGE, Decal.Wrap.REPEAT)
        }
    })
