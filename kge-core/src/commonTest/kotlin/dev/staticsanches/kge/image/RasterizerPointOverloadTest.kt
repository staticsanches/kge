package dev.staticsanches.kge.image

import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.rasterizer.service.OutlineService
import dev.staticsanches.kge.resource.applyClosingIfFailed
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The typed `Int2D` overloads on the raster sub-service interfaces, exposed
 * through the aggregate: each must paint exactly the pixels of its raw
 * counterpart, whatever the parameter mapping and mode.
 */
class RasterizerPointOverloadTest :
    FunSpec({
        fun target(
            width: Int = 8,
            height: Int = 8,
        ): Sprite =
            SpriteService
                .create(width, height, Pixmap.SampleMode.NORMAL, null)
                .applyClosingIfFailed { clear(Colors.TRANSPARENT) }

        fun grid(target: Pixmap): List<Pixel> =
            (0 until target.height).flatMap { y -> (0 until target.width).map { x -> target.get(x, y) } }

        fun spriteOf(
            width: Int,
            height: Int,
            cells: Map<Pair<Int, Int>, Pixel>,
        ): Sprite {
            val sprite = SpriteService.create(width, height, Pixmap.SampleMode.NORMAL, null)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    sprite.set(x, y, cells[x to y] ?: Colors.BLACK)
                }
            }
            return sprite
        }

        context("OutlineService typed overloads") {
            test("typed drawLine paints the same cells as the raw line") {
                target().use { typed ->
                    target().use { raw ->
                        Rasterizer.drawLine(typed, Int2D(0, 0), Int2D(6, 2), Colors.RED, Pixel.Mode.Normal)
                        Rasterizer.drawLine(raw, 0, 0, 6, 2, Colors.RED, Pixel.Mode.Normal)
                        grid(typed) shouldBe grid(raw)
                    }
                }
            }

            test("typed drawRect paints the same ring as raw, both endpoint orders") {
                target().use { typed ->
                    target().use { raw ->
                        Rasterizer.drawRect(typed, Int2D(1, 1), Int2D(6, 4), Colors.RED, Pixel.Mode.Normal)
                        Rasterizer.drawRect(raw, 1, 1, 6, 4, Colors.RED, Pixel.Mode.Normal)
                        grid(typed) shouldBe grid(raw)
                    }
                }
                target().use { typedReversed ->
                    target().use { raw ->
                        Rasterizer.drawRect(typedReversed, Int2D(6, 4), Int2D(1, 1), Colors.RED, Pixel.Mode.Normal)
                        Rasterizer.drawRect(raw, 1, 1, 6, 4, Colors.RED, Pixel.Mode.Normal)
                        grid(typedReversed) shouldBe grid(raw)
                    }
                }
            }

            test("typed drawCircle paints the same cells as the raw circle") {
                target().use { typed ->
                    target().use { raw ->
                        Rasterizer.drawCircle(typed, Int2D(3, 3), 2, Colors.RED, Pixel.Mode.Normal)
                        Rasterizer.drawCircle(raw, 3, 3, 2, Colors.RED, Pixel.Mode.Normal)
                        grid(typed) shouldBe grid(raw)
                    }
                }
            }

            test("typed drawTriangle paints the same cells as the raw triangle") {
                target().use { typed ->
                    target().use { raw ->
                        Rasterizer
                            .drawTriangle(typed, Int2D(0, 0), Int2D(6, 0), Int2D(0, 5), Colors.RED, Pixel.Mode.Normal)
                        Rasterizer.drawTriangle(raw, 0, 0, 6, 0, 0, 5, Colors.RED, Pixel.Mode.Normal)
                        grid(typed) shouldBe grid(raw)
                    }
                }
            }

            test("typed drawLine fully outside the target paints nothing") {
                target(width = 3, height = 3).use { t ->
                    Rasterizer.drawLine(t, Int2D(5, 0), Int2D(9, 0), Colors.RED, Pixel.Mode.Normal)
                    grid(t) shouldBe List(9) { Colors.TRANSPARENT }
                }
            }

            test("typed drawLine under Alpha blends exactly like the raw line") {
                target().use { typed ->
                    target().use { raw ->
                        Rasterizer.fillRect(typed, 0, 0, 7, 7, Colors.WHITE, Pixel.Mode.Normal)
                        Rasterizer.fillRect(raw, 0, 0, 7, 7, Colors.WHITE, Pixel.Mode.Normal)
                        Rasterizer.drawLine(typed, Int2D(0, 0), Int2D(7, 3), Colors.RED, Pixel.Mode.Alpha(0.5f))
                        Rasterizer.drawLine(raw, 0, 0, 7, 3, Colors.RED, Pixel.Mode.Alpha(0.5f))
                        grid(typed) shouldBe grid(raw)
                    }
                }
            }
        }

        context("FillService typed overloads") {
            test("typed fillRect paints the same box as raw, both endpoint orders") {
                target().use { typed ->
                    target().use { raw ->
                        Rasterizer.fillRect(typed, Int2D(1, 1), Int2D(6, 4), Colors.RED, Pixel.Mode.Normal)
                        Rasterizer.fillRect(raw, 1, 1, 6, 4, Colors.RED, Pixel.Mode.Normal)
                        grid(typed) shouldBe grid(raw)
                    }
                }
                target().use { typedReversed ->
                    target().use { raw ->
                        Rasterizer.fillRect(typedReversed, Int2D(6, 4), Int2D(1, 1), Colors.RED, Pixel.Mode.Normal)
                        Rasterizer.fillRect(raw, 1, 1, 6, 4, Colors.RED, Pixel.Mode.Normal)
                        grid(typedReversed) shouldBe grid(raw)
                    }
                }
            }

            test("typed fillCircle paints the same rows as the raw circle") {
                target().use { typed ->
                    target().use { raw ->
                        Rasterizer.fillCircle(typed, Int2D(3, 3), 2, Colors.RED, Pixel.Mode.Normal)
                        Rasterizer.fillCircle(raw, 3, 3, 2, Colors.RED, Pixel.Mode.Normal)
                        grid(typed) shouldBe grid(raw)
                    }
                }
            }

            test("typed fillCircle honors Mask like the raw fill") {
                target().use { typed ->
                    target().use { raw ->
                        Rasterizer.fillCircle(typed, Int2D(3, 3), 2, Pixel.rgba(1, 2, 3, 4), Pixel.Mode.Mask)
                        Rasterizer.fillCircle(raw, 3, 3, 2, Pixel.rgba(1, 2, 3, 4), Pixel.Mode.Mask)
                        grid(typed) shouldBe grid(raw)
                    }
                }
                target().use { t ->
                    Rasterizer.fillCircle(t, Int2D(3, 3), 2, Pixel.rgba(1, 2, 3, 4), Pixel.Mode.Mask)
                    grid(t) shouldBe List(64) { Colors.TRANSPARENT }
                }
            }

            test("typed fillTriangle paints the same cells as the raw triangle") {
                target().use { typed ->
                    target().use { raw ->
                        Rasterizer
                            .fillTriangle(typed, Int2D(0, 0), Int2D(6, 0), Int2D(0, 6), Colors.RED, Pixel.Mode.Normal)
                        Rasterizer.fillTriangle(raw, 0, 0, 6, 0, 0, 6, Colors.RED, Pixel.Mode.Normal)
                        grid(typed) shouldBe grid(raw)
                    }
                }
            }
        }

        context("BlitService typed overload") {
            test("typed blit blits the same pixels as the raw draw") {
                val cells =
                    mapOf(
                        0 to 0 to Colors.RED,
                        1 to 0 to Colors.GREEN,
                        0 to 1 to Colors.BLUE,
                        1 to 1 to Colors.WHITE,
                    )
                spriteOf(2, 2, cells).use { s ->
                    target(width = 6, height = 6).use { typed ->
                        target(width = 6, height = 6).use { raw ->
                            Rasterizer.blit(typed, Int2D(2, 1), s, 1, Pixmap.Flip.NONE, Pixel.Mode.Normal)
                            Rasterizer.blit(raw, 2, 1, s, 1, Pixmap.Flip.NONE, Pixel.Mode.Normal)
                            grid(typed) shouldBe grid(raw)
                        }
                    }
                }
            }

            test("typed blitRegion blits the same pixels as the raw draw") {
                spriteOf(3, 3, emptyMap()).use { s ->
                    target(width = 6, height = 6).use { typed ->
                        target(width = 6, height = 6).use { raw ->
                            Rasterizer.blitRegion(
                                typed,
                                Int2D(2, 1),
                                s,
                                Int2D(1, 1),
                                Int2D(2, 2),
                                1,
                                Pixmap.Flip.NONE,
                                Pixel.Mode.Normal,
                            )
                            Rasterizer.blitRegion(
                                raw,
                                2,
                                1,
                                s,
                                Int2D(1, 1),
                                Int2D(2, 2),
                                1,
                                Pixmap.Flip.NONE,
                                Pixel.Mode.Normal,
                            )
                            grid(typed) shouldBe grid(raw)
                        }
                    }
                }
            }
        }

        context("typed overload extension contract") {
            test("a by-wrapped decorator overriding only the typed drawLine is observed through the typed entry") {
                target().use { t ->
                    val original = OutlineService.original
                    var typedCalls = 0
                    OutlineService.override(
                        object : OutlineService by original {
                            override fun drawLine(
                                target: Pixmap.Mutable,
                                start: Int2D,
                                end: Int2D,
                                color: Pixel,
                                mode: Pixel.Mode,
                            ) {
                                typedCalls++
                                original.drawLine(target, start, end, color, mode)
                            }
                        },
                    )

                    Rasterizer.drawLine(t, Int2D(0, 0), Int2D(3, 1), Colors.RED, Pixel.Mode.Normal)
                    typedCalls shouldBe 1
                    t.get(0, 0) shouldBe Colors.RED
                    t.get(3, 1) shouldBe Colors.RED
                }
            }

            test("a plain override of only the raw drawLine is observed through the typed entry") {
                target().use { t ->
                    val original = OutlineService.original
                    var rawCalls = 0
                    OutlineService.override(
                        object : OutlineService {
                            override fun drawLine(
                                target: Pixmap.Mutable,
                                x0: Int,
                                y0: Int,
                                x1: Int,
                                y1: Int,
                                color: Pixel,
                                mode: Pixel.Mode,
                            ) {
                                rawCalls++
                                original.drawLine(target, x0, y0, x1, y1, Colors.BLUE, mode)
                            }

                            override fun drawRect(
                                target: Pixmap.Mutable,
                                x0: Int,
                                y0: Int,
                                x1: Int,
                                y1: Int,
                                color: Pixel,
                                mode: Pixel.Mode,
                            ) = original.drawRect(target, x0, y0, x1, y1, color, mode)

                            override fun drawCircle(
                                target: Pixmap.Mutable,
                                cx: Int,
                                cy: Int,
                                radius: Int,
                                color: Pixel,
                                mode: Pixel.Mode,
                            ) = original.drawCircle(target, cx, cy, radius, color, mode)

                            override fun drawTriangle(
                                target: Pixmap.Mutable,
                                x0: Int,
                                y0: Int,
                                x1: Int,
                                y1: Int,
                                x2: Int,
                                y2: Int,
                                color: Pixel,
                                mode: Pixel.Mode,
                            ) = original.drawTriangle(target, x0, y0, x1, y1, x2, y2, color, mode)
                        },
                    )

                    Rasterizer.drawLine(t, Int2D(0, 0), Int2D(3, 1), Colors.RED, Pixel.Mode.Normal)
                    rawCalls shouldBe 1
                    t.get(0, 0) shouldBe Colors.BLUE
                }
            }

            test("untouched typed members of a typed-overriding decorator still reach the engine default") {
                target().use { t ->
                    val original = OutlineService.original
                    OutlineService.override(
                        object : OutlineService by original {
                            override fun drawLine(
                                target: Pixmap.Mutable,
                                start: Int2D,
                                end: Int2D,
                                color: Pixel,
                                mode: Pixel.Mode,
                            ) = original.drawLine(target, start, end, Colors.BLUE, mode)
                        },
                    )

                    Rasterizer.drawCircle(t, Int2D(4, 4), 2, Colors.RED, Pixel.Mode.Normal)
                    grid(t).count { it == Colors.RED } shouldBe 12
                }
            }

            test("resetAll restores the engine typed defaults") {
                target().use { t ->
                    val original = OutlineService.original
                    OutlineService.override(
                        object : OutlineService by original {
                            override fun drawLine(
                                target: Pixmap.Mutable,
                                start: Int2D,
                                end: Int2D,
                                color: Pixel,
                                mode: Pixel.Mode,
                            ) = original.drawLine(target, start, end, Colors.BLUE, mode)
                        },
                    )

                    Rasterizer.drawLine(t, Int2D(0, 0), Int2D(3, 1), Colors.RED, Pixel.Mode.Normal)
                    t.get(0, 0) shouldBe Colors.BLUE

                    dev.staticsanches.kge.overridable.KGEOverridable.Proxy
                        .resetAll()

                    target().use { restored ->
                        Rasterizer.drawLine(restored, Int2D(0, 0), Int2D(3, 1), Colors.RED, Pixel.Mode.Normal)
                        restored.get(0, 0) shouldBe Colors.RED
                    }
                }
            }
        }
    })
