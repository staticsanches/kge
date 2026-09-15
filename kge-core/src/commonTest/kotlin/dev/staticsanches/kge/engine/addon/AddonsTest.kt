package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.engine.HasDrawModes
import dev.staticsanches.kge.engine.HasDrawTarget
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.CircleOctantMask
import dev.staticsanches.kge.rasterizer.LinePattern
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.resource.applyClosingIfFailed
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * The raster addons: default methods that forward to the `Rasterizer` over the
 * draw-target and draw-mode roles, painting a real sprite.
 */
class AddonsTest :
    FunSpec({
        fun target(
            width: Int,
            height: Int,
        ): Sprite =
            SpriteService
                .create(width, height, Pixmap.SampleMode.NORMAL, null)
                .applyClosingIfFailed { clear(Colors.TRANSPARENT) }

        fun painted(sprite: Sprite): Set<Pair<Int, Int>> =
            (0 until sprite.height)
                .flatMap { y -> (0 until sprite.width).map { x -> x to y } }
                .filter { (x, y) -> sprite.get(x, y) != Colors.TRANSPARENT }
                .toSet()

        context("DrawAddon") {
            test("draw paints a single pixel, white by default") {
                target(3, 3).use { sprite ->
                    val host = AddonHost(sprite)
                    host.draw(Int2D(1, 1)) shouldBe true

                    sprite.get(1, 1) shouldBe Colors.WHITE
                    sprite.get(0, 0) shouldBe Colors.TRANSPARENT
                }
            }

            test("the raw draw agrees with the Int2D overload and honors the color") {
                target(3, 3).use { typed ->
                    target(3, 3).use { raw ->
                        AddonHost(typed).draw(Int2D(1, 2), Colors.RED) shouldBe true
                        AddonHost(raw).draw(1, 2, Colors.RED) shouldBe true

                        raw.get(1, 2) shouldBe typed.get(1, 2)
                        typed.get(1, 2) shouldBe Colors.RED
                    }
                }
            }

            test("draw forwards the pixel mode to the rasterizer") {
                target(2, 2).use { sprite ->
                    sprite.clear(Colors.WHITE)
                    val host = AddonHost(sprite)

                    host.pixelMode = Pixel.Mode.Alpha(0.5f)
                    host.draw(Int2D(0, 0), Colors.RED) shouldBe true
                    sprite.get(0, 0) shouldBe Pixel.rgba(255, 127, 127, 255)

                    host.pixelMode = Pixel.Mode.Normal
                    host.draw(Int2D(1, 1), Colors.RED) shouldBe true
                    sprite.get(1, 1) shouldBe Colors.RED
                }
            }

            test("a null draw target makes draw a no-op") {
                target(2, 2).use { sprite ->
                    val host = AddonHost(null)

                    host.draw(Int2D(0, 0), Colors.RED) shouldBe false
                    host.draw(0, 1, Colors.RED) shouldBe false

                    for (y in 0 until 2) {
                        for (x in 0 until 2) {
                            sprite.get(x, y) shouldBe Colors.TRANSPARENT
                        }
                    }
                }
            }
        }

        context("DrawCircleAddon") {
            fun ring(
                cx: Int,
                cy: Int,
            ): Set<Pair<Int, Int>> =
                setOf(
                    0 to -2, 0 to 2, -2 to 0, 2 to 0,
                    1 to -2, 1 to 2, -1 to -2, -1 to 2,
                    2 to -1, 2 to 1, -2 to -1, -2 to 1,
                ).map { (x, y) -> (x + cx) to (y + cy) }.toSet()

            test("drawCircle uses the default ALL mask and white") {
                target(7, 7).use { sprite ->
                    AddonHost(sprite).drawCircle(Int2D(3, 3), 2)

                    painted(sprite) shouldBe ring(3, 3)
                    sprite.get(3, 1) shouldBe Colors.WHITE
                }
            }

            test("the raw drawCircle agrees with the Int2D overload and a mask restricts the ring") {
                target(7, 7).use { typed ->
                    target(7, 7).use { raw ->
                        AddonHost(typed).drawCircle(Int2D(3, 3), 2, CircleOctantMask.O1, Colors.RED)
                        AddonHost(raw).drawCircle(3, 3, 2, CircleOctantMask.O1, Colors.RED)

                        painted(raw) shouldBe painted(typed)
                        painted(typed) shouldBe setOf(3 to 1, 4 to 1)
                        (painted(typed) - ring(3, 3)).isEmpty() shouldBe true
                    }
                }
            }

            test("a null draw target makes drawCircle a no-op") {
                target(7, 7).use { sprite ->
                    AddonHost(null).drawCircle(Int2D(3, 3), 2)
                    AddonHost(null).drawCircle(3, 3, 2)

                    painted(sprite) shouldBe emptySet()
                }
            }
        }

        context("FillCircleAddon") {
            fun disc(
                cx: Int,
                cy: Int,
            ): Set<Pair<Int, Int>> =
                buildSet {
                    for (dy in -2..2) {
                        val dxMax = if (dy in -1..1) 2 else 1
                        for (dx in -dxMax..dxMax) {
                            add((cx + dx) to (cy + dy))
                        }
                    }
                }

            test("fillCircle uses the default ALL mask and white") {
                target(7, 7).use { sprite ->
                    AddonHost(sprite).fillCircle(Int2D(3, 3), 2)

                    painted(sprite) shouldBe disc(3, 3)
                    sprite.get(3, 3) shouldBe Colors.WHITE
                }
            }

            test("the raw fillCircle agrees with the Int2D overload and a mask restricts the disc") {
                target(7, 7).use { typed ->
                    target(7, 7).use { raw ->
                        AddonHost(typed).fillCircle(Int2D(3, 3), 2, CircleOctantMask.O1, Colors.RED)
                        AddonHost(raw).fillCircle(3, 3, 2, CircleOctantMask.O1, Colors.RED)

                        painted(raw) shouldBe painted(typed)
                        painted(typed).isNotEmpty() shouldBe true
                        (painted(typed) - disc(3, 3)).isEmpty() shouldBe true
                        (disc(3, 3) - painted(typed)).isNotEmpty() shouldBe true
                    }
                }
            }

            test("a null draw target makes fillCircle a no-op") {
                target(7, 7).use { sprite ->
                    AddonHost(null).fillCircle(Int2D(3, 3), 2)
                    AddonHost(null).fillCircle(3, 3, 2)

                    painted(sprite) shouldBe emptySet()
                }
            }
        }

        context("DrawLineAddon") {
            test("drawLine uses the default Filled pattern and white") {
                target(6, 3).use { sprite ->
                    AddonHost(sprite).drawLine(Int2D(0, 1), Int2D(4, 1))

                    painted(sprite) shouldBe setOf(0 to 1, 1 to 1, 2 to 1, 3 to 1, 4 to 1)
                    sprite.get(0, 1) shouldBe Colors.WHITE
                }
            }

            test("the raw drawLine agrees with the Int2D overload and forwards the pattern") {
                target(6, 3).use { typed ->
                    target(6, 3).use { raw ->
                        target(6, 3).use { filled ->
                            AddonHost(typed).drawLine(Int2D(0, 1), Int2D(4, 1), Colors.RED, LinePattern.Dotted())
                            AddonHost(raw).drawLine(0, 1, 4, 1, Colors.RED, LinePattern.Dotted())
                            AddonHost(filled).drawLine(0, 1, 4, 1, Colors.RED)

                            painted(raw) shouldBe painted(typed)
                            painted(typed) shouldBe setOf(0 to 1, 2 to 1, 4 to 1)
                            painted(filled) shouldBe setOf(0 to 1, 1 to 1, 2 to 1, 3 to 1, 4 to 1)
                        }
                    }
                }
            }

            test("a null draw target makes drawLine a no-op") {
                target(6, 3).use { sprite ->
                    AddonHost(null).drawLine(Int2D(0, 1), Int2D(4, 1))
                    AddonHost(null).drawLine(0, 1, 4, 1)

                    painted(sprite) shouldBe emptySet()
                }
            }
        }

        context("DrawRectAddon") {
            val ring =
                (1..4)
                    .flatMap { x -> (1..3).map { y -> x to y } }
                    .filter { (x, y) -> x == 1 || x == 4 || y == 1 || y == 3 }
                    .toSet()

            test("drawRect uses the default Filled pattern and white") {
                target(6, 4).use { sprite ->
                    AddonHost(sprite).drawRect(Int2D(1, 1), Int2D(4, 3))

                    painted(sprite) shouldBe ring
                    sprite.get(1, 1) shouldBe Colors.WHITE
                }
            }

            test("the raw drawRect draws the inclusive ring, not a diagonal line") {
                target(6, 4).use { typed ->
                    target(6, 4).use { raw ->
                        AddonHost(typed).drawRect(Int2D(1, 1), Int2D(4, 3), Colors.RED)
                        AddonHost(raw).drawRect(1, 1, 4, 3, Colors.RED)

                        painted(raw) shouldBe painted(typed)
                        painted(raw) shouldBe ring
                    }
                }
            }

            test("the raw drawRect forwards the pattern") {
                target(6, 4).use { typed ->
                    target(6, 4).use { raw ->
                        AddonHost(typed).drawRect(Int2D(1, 1), Int2D(4, 3), Colors.RED, LinePattern.Dotted())
                        AddonHost(raw).drawRect(1, 1, 4, 3, Colors.RED, LinePattern.Dotted())

                        painted(raw) shouldBe painted(typed)
                        painted(raw) shouldNotBe ring
                    }
                }
            }

            test("a null draw target makes drawRect a no-op") {
                target(6, 4).use { sprite ->
                    AddonHost(null).drawRect(Int2D(1, 1), Int2D(4, 3))
                    AddonHost(null).drawRect(1, 1, 4, 3)

                    painted(sprite) shouldBe emptySet()
                }
            }
        }

        context("FillRectAddon") {
            val box = (1..3).flatMap { x -> (1..2).map { y -> x to y } }.toSet()

            test("fillRect uses the default white and fills the inclusive box") {
                target(5, 4).use { sprite ->
                    AddonHost(sprite).fillRect(Int2D(1, 1), Int2D(3, 2))

                    painted(sprite) shouldBe box
                    sprite.get(2, 1) shouldBe Colors.WHITE
                }
            }

            test("the raw fillRect agrees with the Int2D overload and either corner order") {
                target(5, 4).use { typed ->
                    target(5, 4).use { raw ->
                        target(5, 4).use { reversed ->
                            AddonHost(typed).fillRect(Int2D(1, 1), Int2D(3, 2), Colors.RED)
                            AddonHost(raw).fillRect(1, 1, 3, 2, Colors.RED)
                            AddonHost(reversed).fillRect(3, 2, 1, 1, Colors.RED)

                            painted(raw) shouldBe painted(typed)
                            painted(reversed) shouldBe painted(typed)
                            painted(raw) shouldBe box
                        }
                    }
                }
            }

            test("a null draw target makes fillRect a no-op") {
                target(5, 4).use { sprite ->
                    AddonHost(null).fillRect(Int2D(1, 1), Int2D(3, 2))
                    AddonHost(null).fillRect(1, 1, 3, 2)

                    painted(sprite) shouldBe emptySet()
                }
            }
        }

        context("DrawTriangleAddon") {
            test("drawTriangle uses the default Filled pattern and white, painting the three edges") {
                target(6, 4).use { sprite ->
                    target(6, 4).use { reference ->
                        Rasterizer.drawLine(reference, 0, 0, 4, 0, Colors.WHITE, LinePattern.Filled, Pixel.Mode.Normal)
                        Rasterizer.drawLine(reference, 4, 0, 0, 3, Colors.WHITE, LinePattern.Filled, Pixel.Mode.Normal)
                        Rasterizer.drawLine(reference, 0, 3, 0, 0, Colors.WHITE, LinePattern.Filled, Pixel.Mode.Normal)

                        AddonHost(sprite).drawTriangle(Int2D(0, 0), Int2D(4, 0), Int2D(0, 3))

                        painted(sprite) shouldBe painted(reference)
                        sprite.get(0, 0) shouldBe Colors.WHITE
                        sprite.get(4, 0) shouldBe Colors.WHITE
                    }
                }
            }

            test("the raw drawTriangle agrees with the Int2D overload and forwards the pattern") {
                target(6, 4).use { typed ->
                    target(6, 4).use { raw ->
                        target(6, 4).use { filled ->
                            AddonHost(typed)
                                .drawTriangle(
                                    Int2D(0, 0), Int2D(4, 0), Int2D(0, 3), Colors.RED, LinePattern.Dotted(),
                                )
                            AddonHost(raw).drawTriangle(0, 0, 4, 0, 0, 3, Colors.RED, LinePattern.Dotted())
                            AddonHost(filled).drawTriangle(0, 0, 4, 0, 0, 3, Colors.RED)

                            painted(raw) shouldBe painted(typed)
                            painted(raw) shouldNotBe painted(filled)
                        }
                    }
                }
            }

            test("a null draw target makes drawTriangle a no-op") {
                target(6, 4).use { sprite ->
                    AddonHost(null).drawTriangle(Int2D(0, 0), Int2D(4, 0), Int2D(0, 3))
                    AddonHost(null).drawTriangle(0, 0, 4, 0, 0, 3)

                    painted(sprite) shouldBe emptySet()
                }
            }
        }

        context("FillTriangleAddon") {
            val triangle = setOf(0 to 0, 0 to 1, 1 to 1)

            test("fillTriangle uses the default white and fills the interior") {
                target(4, 3).use { sprite ->
                    AddonHost(sprite).fillTriangle(Int2D(0, 0), Int2D(0, 2), Int2D(3, 2))

                    painted(sprite) shouldBe triangle
                    sprite.get(0, 0) shouldBe Colors.WHITE
                }
            }

            test("the raw fillTriangle agrees with the Int2D overload and any vertex order") {
                target(4, 3).use { typed ->
                    target(4, 3).use { raw ->
                        target(4, 3).use { reordered ->
                            AddonHost(typed).fillTriangle(Int2D(0, 0), Int2D(0, 2), Int2D(3, 2), Colors.RED)
                            AddonHost(raw).fillTriangle(0, 0, 0, 2, 3, 2, Colors.RED)
                            AddonHost(reordered).fillTriangle(0, 2, 3, 2, 0, 0, Colors.RED)

                            painted(raw) shouldBe painted(typed)
                            painted(reordered) shouldBe painted(typed)
                            painted(raw) shouldBe triangle
                        }
                    }
                }
            }

            test("a null draw target makes fillTriangle a no-op") {
                target(4, 3).use { sprite ->
                    AddonHost(null).fillTriangle(Int2D(0, 0), Int2D(0, 2), Int2D(3, 2))
                    AddonHost(null).fillTriangle(0, 0, 0, 2, 3, 2)

                    painted(sprite) shouldBe emptySet()
                }
            }
        }

        context("DrawSpriteAddon") {
            fun spriteOf(
                width: Int,
                height: Int,
            ): Sprite {
                val sprite = SpriteService.create(width, height, Pixmap.SampleMode.NORMAL, null)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        sprite.set(x, y, Pixel.rgba(x + 1, y + 1, 0))
                    }
                }
                return sprite
            }

            test("drawSprite blits the whole sprite at the position") {
                spriteOf(2, 2).use { source ->
                    target(5, 5).use { sprite ->
                        AddonHost(sprite).drawSprite(Int2D(1, 1), source)

                        sprite.get(1, 1) shouldBe Pixel.rgba(1, 1, 0)
                        sprite.get(2, 1) shouldBe Pixel.rgba(2, 1, 0)
                        sprite.get(1, 2) shouldBe Pixel.rgba(1, 2, 0)
                        sprite.get(2, 2) shouldBe Pixel.rgba(2, 2, 0)
                        painted(sprite) shouldBe setOf(1 to 1, 2 to 1, 1 to 2, 2 to 2)
                    }
                }
            }

            test("the raw drawSprite agrees with the Int2D overload and forwards scale and flip") {
                spriteOf(2, 2).use { source ->
                    target(6, 6).use { typed ->
                        target(6, 6).use { raw ->
                            target(6, 6).use { flipped ->
                                AddonHost(typed).drawSprite(Int2D(1, 1), source, 2)
                                AddonHost(raw).drawSprite(1, 1, source, 2)
                                AddonHost(flipped).drawSprite(0, 0, source, 1, Pixmap.Flip.HORIZONTAL)

                                painted(raw) shouldBe painted(typed)
                                flipped.get(0, 0) shouldBe Pixel.rgba(2, 1, 0)
                                flipped.get(1, 0) shouldBe Pixel.rgba(1, 1, 0)
                            }
                        }
                    }
                }
            }

            test("drawPartialSprite maps the inclusive diagonal to the source region") {
                spriteOf(4, 4).use { source ->
                    target(6, 6).use { sprite ->
                        AddonHost(sprite).drawPartialSprite(Int2D(1, 1), source, Int2D(1, 2), Int2D(2, 3))

                        sprite.get(1, 1) shouldBe Pixel.rgba(2, 3, 0)
                        sprite.get(2, 1) shouldBe Pixel.rgba(3, 3, 0)
                        sprite.get(1, 2) shouldBe Pixel.rgba(2, 4, 0)
                        sprite.get(2, 2) shouldBe Pixel.rgba(3, 4, 0)
                        painted(sprite) shouldBe setOf(1 to 1, 2 to 1, 1 to 2, 2 to 2)
                    }
                }
            }

            test("drawPartialSprite is endpoint-order agnostic and the raw overload agrees") {
                spriteOf(4, 4).use { source ->
                    target(6, 6).use { typed ->
                        target(6, 6).use { reversed ->
                            target(6, 6).use { raw ->
                                AddonHost(typed).drawPartialSprite(
                                    Int2D(1, 1), source, Int2D(1, 2), Int2D(2, 3), 2,
                                )
                                AddonHost(reversed).drawPartialSprite(
                                    Int2D(1, 1), source, Int2D(2, 3), Int2D(1, 2), 2,
                                )
                                AddonHost(raw).drawPartialSprite(1, 1, source, 1, 2, 2, 3, 2)

                                painted(reversed) shouldBe painted(typed)
                                painted(raw) shouldBe painted(typed)
                            }
                        }
                    }
                }
            }

            test("drawPartialSprite fails fast when the region leaves the sprite") {
                spriteOf(4, 4).use { source ->
                    target(6, 6).use { sprite ->
                        shouldThrow<IllegalArgumentException> {
                            AddonHost(sprite).drawPartialSprite(Int2D(0, 0), source, Int2D(2, 2), Int2D(4, 4))
                        }
                    }
                }
            }

            test("a null draw target makes both sprite draws a no-op") {
                spriteOf(2, 2).use { source ->
                    target(5, 5).use { sprite ->
                        AddonHost(null).drawSprite(Int2D(1, 1), source)
                        AddonHost(null).drawSprite(1, 1, source)
                        AddonHost(null).drawPartialSprite(Int2D(1, 1), source, Int2D(0, 0), Int2D(1, 1))
                        AddonHost(null).drawPartialSprite(1, 1, source, 0, 0, 1, 1)

                        painted(sprite) shouldBe emptySet()
                    }
                }
            }
        }

        context("ClearAddon") {
            test("clear(pixel) fills every cell with the pixel") {
                target(2, 3).use { sprite ->
                    AddonHost(sprite).clear(Colors.RED)

                    for (y in 0 until 3) {
                        for (x in 0 until 2) {
                            sprite.get(x, y) shouldBe Colors.RED
                        }
                    }
                }
            }

            test("clear(pixelByXY) fills every cell from the function in row-major order") {
                target(3, 2).use { sprite ->
                    AddonHost(sprite).clear { x, y -> if ((x + y) % 2 == 0) Colors.RED else Colors.BLUE }

                    for (y in 0 until 2) {
                        for (x in 0 until 3) {
                            sprite.get(x, y) shouldBe if ((x + y) % 2 == 0) Colors.RED else Colors.BLUE
                        }
                    }
                }
            }

            test("clear(pixels) overwrites row-major and leaves the tail untouched") {
                target(3, 2).use { sprite ->
                    AddonHost(sprite).clear(listOf(Colors.RED, Colors.GREEN, Colors.BLUE))

                    sprite.get(0, 0) shouldBe Colors.RED
                    sprite.get(1, 0) shouldBe Colors.GREEN
                    sprite.get(2, 0) shouldBe Colors.BLUE
                    sprite.get(0, 1) shouldBe Colors.TRANSPARENT
                    sprite.get(1, 1) shouldBe Colors.TRANSPARENT
                    sprite.get(2, 1) shouldBe Colors.TRANSPARENT
                }
            }

            test("a null draw target makes clear a no-op") {
                target(2, 2).use { sprite ->
                    val host = AddonHost(null)
                    host.clear(Colors.RED)
                    host.clear { _, _ -> Colors.RED }
                    host.clear(listOf(Colors.RED))

                    for (y in 0 until 2) {
                        for (x in 0 until 2) {
                            sprite.get(x, y) shouldBe Colors.TRANSPARENT
                        }
                    }
                }
            }
        }
    })

private class AddonHost(
    override var drawTarget: Sprite?,
) : HasDrawTarget,
    HasDrawModes,
    ClearAddon,
    DrawAddon,
    DrawCircleAddon,
    FillCircleAddon,
    DrawLineAddon,
    DrawRectAddon,
    FillRectAddon,
    DrawTriangleAddon,
    FillTriangleAddon,
    DrawSpriteAddon {
    override fun setDrawTarget(
        index: Int,
        dirty: Boolean,
    ) = error("not used by the raster addons")

    override var pixelMode: Pixel.Mode = Pixel.Mode.Normal

    override var decalMode: Decal.Mode = Decal.Mode.NORMAL

    override var decalStructure: Decal.Structure = Decal.Structure.FAN

    override var suspendTextureTransfer: Boolean = false
}
