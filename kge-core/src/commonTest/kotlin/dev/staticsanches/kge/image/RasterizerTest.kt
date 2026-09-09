package dev.staticsanches.kge.image

import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.rasterizer.service.DrawService
import dev.staticsanches.kge.rasterizer.service.FillService
import dev.staticsanches.kge.rasterizer.service.OutlineService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * The raster aggregate ([Rasterizer]) over its sub-services. Every pixel-mode
 * write passes through the [DrawService.draw] seam: out of bounds it never
 * throws and never touches the storage, and the old-pixel read for
 * Alpha/Custom is the stored value — the target's sample mode is irrelevant
 * to raster draws. [FillService.fillRect] fills an inclusive rectangle that
 * is endpoint-agnostic and clipped to the target; under Normal on a [Sprite]
 * it writes the rows directly, so its result must equal the per-pixel draw.
 */
class RasterizerTest :
    FunSpec({
        fun target(
            width: Int = 2,
            height: Int = 2,
            mode: Pixmap.SampleMode = Pixmap.SampleMode.NORMAL,
        ): Sprite = SpriteCreationService.create(width, height, mode, null)

        test("draw writes in bounds and reports true") {
            target().use { t ->
                Rasterizer.draw(t, 1, 1, Colors.RED, Pixel.Mode.Normal) shouldBe true
                t.get(1, 1) shouldBe Colors.RED
            }
        }

        test("Normal out of bounds is a no-op returning false, never throwing") {
            target().use { t ->
                Rasterizer.draw(t, -1, 0, Colors.RED, Pixel.Mode.Normal) shouldBe false
                Rasterizer.draw(t, 2, 0, Colors.RED, Pixel.Mode.Normal) shouldBe false
                Rasterizer.draw(t, 0, 2, Colors.RED, Pixel.Mode.Normal) shouldBe false

                for (y in 0 until 2) {
                    for (x in 0 until 2) {
                        t.get(x, y) shouldBe Colors.TRANSPARENT
                    }
                }
            }
        }

        test("Mask out of bounds with an opaque color is a no-op returning false") {
            target().use { t ->
                Rasterizer.draw(t, 3, 0, Colors.RED, Pixel.Mode.Mask) shouldBe false
            }
        }

        test("Alpha out of bounds returns false without throwing or writing") {
            target().use { t ->
                Rasterizer.draw(t, 0, 0, Colors.WHITE, Pixel.Mode.Normal)

                Rasterizer.draw(t, -1, 0, Colors.RED, Pixel.Mode.Alpha(0.5f)) shouldBe false
                Rasterizer.draw(t, 1, 2, Colors.RED, Pixel.Mode.Alpha(0.5f)) shouldBe false

                t.get(0, 0) shouldBe Colors.WHITE
            }
        }

        test("the old pixel for Alpha/Custom is the stored value, never a sample-mode wrap") {
            target(mode = Pixmap.SampleMode.PERIODIC).use { t ->
                Rasterizer.draw(t, 0, 0, Colors.RED, Pixel.Mode.Normal)
                t.get(0, 0) shouldBe Colors.RED

                Rasterizer.draw(t, 2, 0, Colors.RED, Pixel.Mode.Alpha(0.5f)) shouldBe false
                t.get(0, 0) shouldBe Colors.RED
            }
        }

        test("Custom is not invoked out of bounds") {
            target().use { t ->
                var invocations = 0
                val custom =
                    object : Pixel.Mode.Custom {
                        override fun apply(
                            x: Int,
                            y: Int,
                            newPixel: Pixel,
                            oldPixel: Pixel,
                        ): Pixel {
                            invocations++
                            return newPixel
                        }
                    }

                Rasterizer.draw(t, -1, 0, Colors.RED, custom) shouldBe false
                Rasterizer.draw(t, 0, 1, Colors.RED, custom) shouldBe true
                invocations shouldBe 1
            }
        }

        test("fillRect fills the inclusive rectangle between any two corners") {
            target(width = 4, height = 4).use { t ->
                Rasterizer.fillRect(t, 1, 1, 2, 2, Colors.RED, Pixel.Mode.Normal)

                for (y in 0 until 4) {
                    for (x in 0 until 4) {
                        val expected = if (x in 1..2 && y in 1..2) Colors.RED else Colors.TRANSPARENT
                        t.get(x, y) shouldBe expected
                    }
                }
            }
        }

        test("fillRect paints the same box whichever diagonal endpoint comes first") {
            target(width = 4, height = 4).use { t ->
                val other = target(width = 4, height = 4)
                other.use { expected ->
                    Rasterizer.fillRect(t, 1, 1, 3, 3, Colors.BLUE, Pixel.Mode.Normal)
                    Rasterizer.fillRect(expected, 3, 3, 1, 1, Colors.BLUE, Pixel.Mode.Normal)

                    for (y in 0 until 4) {
                        for (x in 0 until 4) {
                            t.get(x, y) shouldBe expected.get(x, y)
                        }
                    }
                }
            }
        }

        test("fillRect is clipped to the target and paints nothing fully outside") {
            target(width = 3, height = 3).use { t ->
                Rasterizer.fillRect(t, -2, -1, 1, 2, Colors.RED, Pixel.Mode.Normal)

                for (y in 0 until 3) {
                    for (x in 0 until 3) {
                        val expected = if (x in 0..1 && y in 0..2) Colors.RED else Colors.TRANSPARENT
                        t.get(x, y) shouldBe expected
                    }
                }
            }
        }

        test("fillRect fully outside the target paints nothing") {
            target().use { t ->
                Rasterizer.fillRect(t, -5, -5, -1, -1, Colors.RED, Pixel.Mode.Normal)
                Rasterizer.fillRect(t, 2, 2, 4, 4, Colors.RED, Pixel.Mode.Normal)

                for (y in 0 until 2) {
                    for (x in 0 until 2) {
                        t.get(x, y) shouldBe Colors.TRANSPARENT
                    }
                }
            }
        }

        test("fillRect handles a single row, column and pixel") {
            target(width = 3, height = 3).use { t ->
                Rasterizer.fillRect(t, 1, 0, 1, 2, Colors.RED, Pixel.Mode.Normal)
                t.get(1, 0) shouldBe Colors.RED
                t.get(1, 1) shouldBe Colors.RED
                t.get(1, 2) shouldBe Colors.RED
                t.get(0, 1) shouldBe Colors.TRANSPARENT

                Rasterizer.fillRect(t, 2, 2, 2, 2, Colors.GREEN, Pixel.Mode.Normal)
                t.get(2, 2) shouldBe Colors.GREEN
            }
        }

        test("fillRect honors Mask by dropping non-opaque colors over the whole box") {
            target().use { t ->
                Rasterizer.fillRect(t, 0, 0, 1, 1, Pixel.rgba(1, 2, 3, 4), Pixel.Mode.Mask)
                Rasterizer.fillRect(t, 0, 0, 1, 1, Pixel.rgba(1, 2, 3, 255), Pixel.Mode.Mask)

                t.get(0, 0) shouldBe Pixel.rgba(1, 2, 3, 255)
                t.get(1, 1) shouldBe Pixel.rgba(1, 2, 3, 255)
            }
        }

        test("fillRect under Alpha blends every pixel of the box") {
            target(width = 2, height = 2).use { t ->
                Rasterizer.fillRect(t, 0, 0, 1, 1, Colors.WHITE, Pixel.Mode.Normal)
                Rasterizer.fillRect(t, 0, 0, 1, 1, Colors.RED, Pixel.Mode.Alpha(0.5f))

                for (y in 0 until 2) {
                    for (x in 0 until 2) {
                        t.get(x, y) shouldBe Pixel.rgba(255, 127, 127, 255)
                    }
                }
            }
        }

        test("fillRect under Normal on a Sprite matches the per-pixel draw, alpha included") {
            val rects = listOf(listOf(0, 0, 3, 3), listOf(1, 1, 2, 2), listOf(-1, -1, 2, 1), listOf(2, 0, 2, 3))
            val colors = listOf(Colors.RED, Pixel.rgba(9, 8, 7, 0), Pixel.rgba(5, 5, 5, 128))

            for (rect in rects) {
                for (color in colors) {
                    target(width = 4, height = 4).use { t ->
                        val expected = target(width = 4, height = 4)
                        expected.use { e ->
                            val (x0, y0, x1, y1) = rect
                            Rasterizer.fillRect(t, x0, y0, x1, y1, color, Pixel.Mode.Normal)

                            for (y in 0 until 4) {
                                for (x in 0 until 4) {
                                    if (x in minOf(x0, x1)..maxOf(x0, x1) && y in minOf(y0, y1)..maxOf(y0, y1)) {
                                        Rasterizer.draw(e, x, y, color, Pixel.Mode.Normal)
                                    }
                                }
                            }

                            for (y in 0 until 4) {
                                for (x in 0 until 4) {
                                    t.get(x, y) shouldBe e.get(x, y)
                                }
                            }
                        }
                    }
                }
            }
        }

        fun grid(
            width: Int = 8,
            height: Int = 8,
        ): Sprite = target(width, height).also { t -> t.clear(Colors.TRANSPARENT) }

        fun painted(t: Sprite): Set<Pair<Int, Int>> =
            (0 until t.height)
                .flatMap { y -> (0 until t.width).map { x -> x to y } }
                .filter { (x, y) -> t.get(x, y) != Colors.TRANSPARENT }
                .toSet()

        test("drawLine paints the shallow-octant cells, both ends inclusive") {
            grid().use { t ->
                Rasterizer.drawLine(t, 0, 0, 3, 1, Colors.RED, Pixel.Mode.Normal)
                painted(t) shouldBe setOf(0 to 0, 1 to 0, 2 to 1, 3 to 1)
            }
        }

        test("drawLine tie cells step toward the diagonal (equidistant case)") {
            grid().use { t ->
                Rasterizer.drawLine(t, 0, 0, 4, 2, Colors.RED, Pixel.Mode.Normal)
                painted(t) shouldBe setOf(0 to 0, 1 to 1, 2 to 1, 3 to 2, 4 to 2)
            }
        }

        test("drawLine paints the same cells regardless of endpoint order") {
            grid().use { t ->
                Rasterizer.drawLine(t, 0, 0, 4, 2, Colors.RED, Pixel.Mode.Normal)
                val forward = painted(t)

                grid().use { back ->
                    Rasterizer.drawLine(back, 4, 2, 0, 0, Colors.RED, Pixel.Mode.Normal)
                    painted(back) shouldBe forward
                }
            }
        }

        test("drawLine paints steep-octant cells exactly") {
            grid().use { t ->
                Rasterizer.drawLine(t, 0, 0, 1, 3, Colors.RED, Pixel.Mode.Normal)
                painted(t) shouldBe setOf(0 to 0, 0 to 1, 1 to 2, 1 to 3)
            }
        }

        test("drawLine handles vertical, horizontal, diagonal and single-point lines") {
            grid().use { t ->
                Rasterizer.drawLine(t, 2, 0, 2, 3, Colors.RED, Pixel.Mode.Normal)
                painted(t) shouldBe setOf(2 to 0, 2 to 1, 2 to 2, 2 to 3)
            }
            grid().use { t ->
                Rasterizer.drawLine(t, 0, 1, 3, 1, Colors.RED, Pixel.Mode.Normal)
                painted(t) shouldBe setOf(0 to 1, 1 to 1, 2 to 1, 3 to 1)
            }
            grid().use { t ->
                Rasterizer.drawLine(t, 0, 0, 2, 2, Colors.RED, Pixel.Mode.Normal)
                painted(t) shouldBe setOf(0 to 0, 1 to 1, 2 to 2)
            }
            grid().use { t ->
                Rasterizer.drawLine(t, 2, 2, 2, 2, Colors.RED, Pixel.Mode.Normal)
                painted(t) shouldBe setOf(2 to 2)
            }
        }

        test("drawLine honors Mask by dropping a non-opaque color") {
            grid().use { t ->
                Rasterizer.drawLine(t, 0, 0, 3, 1, Pixel.rgba(1, 2, 3, 4), Pixel.Mode.Mask)
                painted(t) shouldBe emptySet()
            }
        }

        test("drawLine under Alpha blends every painted cell over the base") {
            grid(width = 4, height = 4).use { t ->
                Rasterizer.fillRect(t, 0, 0, 3, 3, Colors.WHITE, Pixel.Mode.Normal)
                Rasterizer.drawLine(t, 0, 0, 3, 1, Colors.RED, Pixel.Mode.Alpha(0.5f))

                for ((x, y) in listOf(0 to 0, 1 to 0, 2 to 1, 3 to 1)) {
                    t.get(x, y) shouldBe Pixel.rgba(255, 127, 127, 255)
                }
                for (cell in setOf(0 to 1, 1 to 1, 2 to 0, 3 to 0)) {
                    t.get(cell.first, cell.second) shouldBe Colors.WHITE
                }
            }
        }

        test("drawLine fully outside the target paints nothing and never throws") {
            grid(width = 3, height = 3).use { t ->
                Rasterizer.drawLine(t, 5, 0, 9, 0, Colors.RED, Pixel.Mode.Normal)
                Rasterizer.drawLine(t, -6, -1, -4, 1, Colors.RED, Pixel.Mode.Alpha(0.5f))
                painted(t) shouldBe emptySet()
            }
        }

        test("drawLine crossing the edge paints only the visible cells of the walk") {
            grid(width = 3, height = 3).use { t ->
                Rasterizer.drawLine(t, -2, 0, 1, 0, Colors.RED, Pixel.Mode.Normal)
                painted(t) shouldBe setOf(0 to 0, 1 to 0)
            }
            grid(width = 3, height = 3).use { t ->
                Rasterizer.drawLine(t, 0, -2, 1, 1, Colors.RED, Pixel.Mode.Normal)
                painted(t) shouldBe setOf(1 to 0, 1 to 1)
            }
        }

        val ringR2 =
            setOf(
                0 to -2, 0 to 2, -2 to 0, 2 to 0,
                1 to -2, 1 to 2, -1 to -2, -1 to 2,
                2 to -1, 2 to 1, -2 to -1, -2 to 1,
            )

        fun shifted(
            cells: Set<Pair<Int, Int>>,
            cx: Int,
            cy: Int,
        ): Set<Pair<Int, Int>> = cells.map { (x, y) -> (x + cx) to (y + cy) }.toSet()

        test("drawRect draws the inclusive box perimeter") {
            grid(width = 8, height = 8).use { t ->
                Rasterizer.drawRect(t, 1, 1, 4, 4, Colors.RED, Pixel.Mode.Normal)
                val expected =
                    (1..4)
                        .flatMap { x ->
                            (1..4).map { y -> x to y }
                        }.filter { (x, y) -> x == 1 || x == 4 || y == 1 || y == 4 }
                        .toSet()
                painted(t) shouldBe expected
            }
        }

        test("drawRect paints the same ring whichever diagonal endpoint comes first") {
            grid(width = 8, height = 8).use { t ->
                Rasterizer.drawRect(t, 1, 1, 4, 4, Colors.RED, Pixel.Mode.Normal)
                val forward = painted(t)

                grid().use { back ->
                    Rasterizer.drawRect(back, 4, 4, 1, 1, Colors.RED, Pixel.Mode.Normal)
                    painted(back) shouldBe forward
                }
            }
        }

        test("drawRect on a degenerate box draws a single line") {
            grid().use { t ->
                Rasterizer.drawRect(t, 3, 1, 3, 5, Colors.RED, Pixel.Mode.Normal)
                painted(t) shouldBe setOf(3 to 1, 3 to 2, 3 to 3, 3 to 4, 3 to 5)
            }
        }

        test("drawRect corners are drawn twice, so Alpha blends them twice") {
            grid(width = 6, height = 6).use { t ->
                Rasterizer.fillRect(t, 0, 0, 5, 5, Colors.WHITE, Pixel.Mode.Normal)
                Rasterizer.drawRect(t, 1, 1, 4, 4, Colors.RED, Pixel.Mode.Alpha(0.5f))

                t.get(1, 1) shouldBe Pixel.rgba(255, 63, 63, 255)
                t.get(4, 4) shouldBe Pixel.rgba(255, 63, 63, 255)
                t.get(2, 1) shouldBe Pixel.rgba(255, 127, 127, 255)
                t.get(1, 2) shouldBe Pixel.rgba(255, 127, 127, 255)
                t.get(2, 2) shouldBe Colors.WHITE
            }
        }

        test("drawCircle r=2 paints the reference midpoint ring cells") {
            grid().use { t ->
                Rasterizer.drawCircle(t, 4, 4, 2, Colors.RED, Pixel.Mode.Normal)
                painted(t) shouldBe shifted(ringR2, 4, 4)
            }
        }

        test("drawCircle radius 0 paints the center, a negative radius paints nothing") {
            grid().use { t ->
                Rasterizer.drawCircle(t, 4, 4, 0, Colors.RED, Pixel.Mode.Normal)
                painted(t) shouldBe setOf(4 to 4)
            }
            grid().use { t ->
                Rasterizer.drawCircle(t, 4, 4, -1, Colors.RED, Pixel.Mode.Normal)
                painted(t) shouldBe emptySet()
            }
        }

        test("drawCircle honors Mask by dropping a non-opaque color") {
            grid().use { t ->
                Rasterizer.drawCircle(t, 4, 4, 2, Pixel.rgba(1, 2, 3, 4), Pixel.Mode.Mask)
                painted(t) shouldBe emptySet()
            }
        }

        val fillR2 =
            buildSet {
                for (dy in -2..2) {
                    val dxMax = if (dy in -1..1) 2 else 1
                    for (dx in -dxMax..dxMax) {
                        add(dx to dy)
                    }
                }
            }

        test("fillCircle r=2 paints the reference midpoint rows") {
            grid().use { t ->
                Rasterizer.fillCircle(t, 4, 4, 2, Colors.RED, Pixel.Mode.Normal)
                painted(t) shouldBe shifted(fillR2, 4, 4)
            }
        }

        test("fillCircle radius 0 paints the center, a negative radius paints nothing") {
            grid().use { t ->
                Rasterizer.fillCircle(t, 4, 4, 0, Colors.RED, Pixel.Mode.Normal)
                painted(t) shouldBe setOf(4 to 4)
            }
            grid().use { t ->
                Rasterizer.fillCircle(t, 4, 4, -1, Colors.RED, Pixel.Mode.Normal)
                painted(t) shouldBe emptySet()
            }
        }

        test("fillCircle crossing the edge paints the visible cells of the reference circle") {
            grid(width = 5, height = 5).use { t ->
                Rasterizer.fillCircle(t, 0, 0, 2, Colors.RED, Pixel.Mode.Normal)
                val visible =
                    fillR2.filter { (x, y) -> x in 0..4 && y in 0..4 }.toSet()
                painted(t) shouldBe visible
            }
        }

        fun oracleTriangleCells(
            ax: Int,
            ay: Int,
            bx: Int,
            by: Int,
            cx: Int,
            cy: Int,
        ): Set<Pair<Int, Int>> {
            // an independent path for the same rule: floats at half-integer
            // centers, winding normalized by the float area
            var v = Triple(ax to ay, bx to by, cx to cy)

            fun ccw(t: Triple<Pair<Int, Int>, Pair<Int, Int>, Pair<Int, Int>>): Long {
                val (p, q, r) = t
                return (q.first - p.first).toLong() * (r.second - p.second) -
                    (q.second - p.second).toLong() * (r.first - p.first)
            }

            if (ccw(v) < 0) {
                v = Triple(v.first, v.third, v.second)
            }
            val (a, b, c) = v

            val xs = listOf(a.first, b.first, c.first)
            val ys = listOf(a.second, b.second, c.second)
            val out = mutableSetOf<Pair<Int, Int>>()

            fun inside(
                eax: Int,
                eay: Int,
                ebx: Int,
                eby: Int,
                px: Float,
                py: Float,
            ): Boolean {
                val ex = (ebx - eax).toFloat()
                val ey = (eby - eay).toFloat()
                val open = ey < 0f || (ey == 0f && ex > 0f)
                val cross = ex * (py - eay) - ey * (px - eax)
                return if (open) cross > 0f else cross >= 0f
            }

            for (y in ys.min()..ys.max()) {
                for (x in xs.min()..xs.max()) {
                    val px = x + 0.5f
                    val py = y + 0.5f
                    if (
                        inside(a.first, a.second, b.first, b.second, px, py) &&
                        inside(b.first, b.second, c.first, c.second, px, py) &&
                        inside(c.first, c.second, a.first, a.second, px, py)
                    ) {
                        out += x to y
                    }
                }
            }
            return out
        }

        test("fillTriangle paints only the center-inside cells (GPU top-left rule)") {
            grid().use { t ->
                Rasterizer.fillTriangle(t, 0, 0, 0, 2, 3, 2, Colors.RED, Pixel.Mode.Normal)
                painted(t) shouldBe setOf(0 to 0, 0 to 1, 1 to 1)
            }
        }

        test("fillTriangle paints the same cells for any vertex order") {
            val v = listOf(0 to 0, 0 to 2, 3 to 2)
            val expected = setOf(0 to 0, 0 to 1, 1 to 1)
            for (p in listOf(v, v.reversed(), listOf(v[1], v[2], v[0]))) {
                grid().use { t ->
                    Rasterizer.fillTriangle(
                        t,
                        p[0].first,
                        p[0].second,
                        p[1].first,
                        p[1].second,
                        p[2].first,
                        p[2].second,
                        Colors.RED,
                        Pixel.Mode.Normal,
                    )
                    painted(t) shouldBe expected
                }
            }
        }

        test("two triangles sharing a diagonal paint the seam pixels exactly once") {
            grid(width = 4, height = 4).use { t ->
                Rasterizer.fillRect(t, 0, 0, 3, 3, Colors.WHITE, Pixel.Mode.Normal)
                Rasterizer.fillTriangle(t, 0, 0, 2, 0, 2, 2, Colors.RED, Pixel.Mode.Alpha(0.5f))
                Rasterizer.fillTriangle(t, 0, 0, 2, 2, 0, 2, Colors.RED, Pixel.Mode.Alpha(0.5f))

                val blended = Pixel.rgba(255, 127, 127, 255)
                val doubleBlended = Pixel.rgba(255, 63, 63, 255)
                val changed =
                    (0 until 4)
                        .flatMap { y -> (0 until 4).map { x -> x to y } }
                        .filter { (x, y) -> t.get(x, y) != Colors.WHITE }
                        .toSet()
                changed shouldBe setOf(0 to 0, 1 to 0, 0 to 1, 1 to 1)
                for ((x, y) in changed) {
                    t.get(x, y) shouldBe blended
                }
                (0 until 4).forEach { y -> (0 until 4).forEach { x -> t.get(x, y) shouldNotBe doubleBlended } }
            }
        }

        fun collinear(p: List<Pair<Int, Int>>): Boolean {
            val (a, b, c) = p
            return (b.first - a.first).toLong() * (c.second - a.second) -
                (b.second - a.second).toLong() * (c.first - a.first) == 0L
        }

        test("fillTriangle under Normal matches the independent oracle on seeded random triangles") {
            val random = kotlin.random.Random(20260908)
            grid(width = 24, height = 24).use { t ->
                var compared = 0
                while (compared < 300) {
                    val p =
                        listOf(
                            random.nextInt(0, 24) to random.nextInt(0, 24),
                            random.nextInt(0, 24) to random.nextInt(0, 24),
                            random.nextInt(0, 24) to random.nextInt(0, 24),
                        )
                    if (collinear(p)) continue
                    val expected =
                        oracleTriangleCells(p[0].first, p[0].second, p[1].first, p[1].second, p[2].first, p[2].second)
                    t.clear(Colors.TRANSPARENT)
                    Rasterizer.fillTriangle(
                        t,
                        p[0].first,
                        p[0].second,
                        p[1].first,
                        p[1].second,
                        p[2].first,
                        p[2].second,
                        Colors.RED,
                        Pixel.Mode.Normal,
                    )
                    painted(t) shouldBe expected
                    compared++
                }
            }
        }

        test("fillTriangle under Alpha matches the oracle on seeded random triangles") {
            val random = kotlin.random.Random(99)
            grid(width = 24, height = 24).use { t ->
                var compared = 0
                while (compared < 100) {
                    val p =
                        listOf(
                            random.nextInt(0, 24) to random.nextInt(0, 24),
                            random.nextInt(0, 24) to random.nextInt(0, 24),
                            random.nextInt(0, 24) to random.nextInt(0, 24),
                        )
                    if (collinear(p)) continue
                    val expected =
                        oracleTriangleCells(p[0].first, p[0].second, p[1].first, p[1].second, p[2].first, p[2].second)
                    t.clear(Colors.WHITE)
                    Rasterizer
                        .fillTriangle(
                            t, p[0].first, p[0].second, p[1].first, p[1].second, p[2].first, p[2].second, Colors.RED,
                            Pixel.Mode
                                .Alpha(0.5f),
                        )

                    val blended = Pixel.rgba(255, 127, 127, 255)
                    val changed =
                        (0 until 24)
                            .flatMap { y -> (0 until 24).map { x -> x to y } }
                            .filter { (x, y) -> t.get(x, y) != Colors.WHITE }
                            .toSet()
                    changed shouldBe expected
                    for ((x, y) in expected) {
                        t.get(x, y) shouldBe blended
                    }
                    compared++
                }
            }
        }

        test("fillTriangle fully outside the target paints nothing") {
            grid(width = 3, height = 3).use { t ->
                Rasterizer.fillTriangle(t, 5, 0, 8, 0, 6, 3, Colors.RED, Pixel.Mode.Normal)
                painted(t) shouldBe emptySet()
            }
        }

        test("collinear fillTriangle draws the line between the farthest pair") {
            grid().use { t ->
                Rasterizer.fillTriangle(t, 0, 0, 5, 0, 2, 0, Colors.RED, Pixel.Mode.Normal)
                painted(t) shouldBe (0..5).map { it to 0 }.toSet()
            }
        }

        test("drawTriangle paints exactly the three edges of a composition") {
            val vertices = listOf(0 to 0, 4 to 0, 0 to 3)
            grid().use { reference ->
                Rasterizer.drawLine(reference, 0, 0, 4, 0, Colors.RED, Pixel.Mode.Normal)
                Rasterizer.drawLine(reference, 4, 0, 0, 3, Colors.RED, Pixel.Mode.Normal)
                Rasterizer.drawLine(reference, 0, 3, 0, 0, Colors.RED, Pixel.Mode.Normal)
                grid().use { t ->
                    Rasterizer.drawTriangle(
                        t,
                        0,
                        0,
                        4,
                        0,
                        0,
                        3,
                        Colors.RED,
                        Pixel.Mode.Normal,
                    )
                    painted(t) shouldBe painted(reference)
                }
            }
        }

        test("drawTriangle paints the same outline regardless of vertex order") {
            val v = listOf(1 to 1, 5 to 2, 2 to 4)
            grid().use { first ->
                Rasterizer.drawTriangle(first, 1, 1, 5, 2, 2, 4, Colors.RED, Pixel.Mode.Normal)
                val expected = painted(first)
                for (p in listOf(v.reversed(), listOf(v[1], v[2], v[0]))) {
                    grid().use { t ->
                        Rasterizer.drawTriangle(
                            t,
                            p[0].first,
                            p[0].second,
                            p[1].first,
                            p[1].second,
                            p[2].first,
                            p[2].second,
                            Colors.RED,
                            Pixel.Mode.Normal,
                        )
                        painted(t) shouldBe expected
                    }
                }
            }
        }

        test("drawTriangle vertices are edge endpoints, so Alpha blends corners twice") {
            grid(width = 6, height = 6).use { t ->
                Rasterizer.fillRect(t, 0, 0, 5, 5, Colors.WHITE, Pixel.Mode.Normal)
                Rasterizer.drawTriangle(t, 0, 0, 4, 0, 0, 3, Colors.RED, Pixel.Mode.Alpha(0.5f))

                val cells =
                    (0 until 6)
                        .flatMap { y -> (0 until 6).map { x -> x to y } }
                        .filter { (x, y) -> t.get(x, y) != Colors.WHITE }
                        .toSet()
                val single =
                    cells.filter { (x, y) -> t.get(x, y) == Pixel.rgba(255, 127, 127, 255) }.toSet()
                val corners =
                    cells.filter { (x, y) -> t.get(x, y) == Pixel.rgba(255, 63, 63, 255) }.toSet()
                cells shouldBe
                    setOf(
                        0 to 0, 1 to 0, 2 to 0, 3 to 0, 4 to 0,
                        0 to 1, 0 to 2, 0 to 3,
                        1 to 2, 2 to 1, 3 to 1,
                    )
                single shouldBe setOf(1 to 0, 2 to 0, 3 to 0, 0 to 1, 0 to 2, 1 to 2, 2 to 1, 3 to 1)
                corners shouldBe setOf(0 to 0, 4 to 0, 0 to 3)
            }
        }

        test("collinear drawTriangle draws a single line that blends each cell once") {
            grid().use { t ->
                Rasterizer.fillRect(t, 0, 0, 5, 5, Colors.WHITE, Pixel.Mode.Normal)
                Rasterizer.drawTriangle(t, 0, 0, 5, 0, 2, 0, Colors.RED, Pixel.Mode.Alpha(0.5f))

                val blended =
                    (0 until 6)
                        .flatMap { y -> (0 until 6).map { x -> x to y } }
                        .filter { (x, y) -> t.get(x, y) == Pixel.rgba(255, 127, 127, 255) }
                        .toSet()
                blended shouldBe (0..5).map { it to 0 }.toSet()
            }
        }

        test("degenerate drawTriangle with two equal vertices draws the remaining line") {
            grid().use { t ->
                Rasterizer.drawTriangle(t, 0, 0, 3, 1, 0, 0, Colors.RED, Pixel.Mode.Normal)
                painted(t) shouldBe setOf(0 to 0, 1 to 0, 2 to 1, 3 to 1)
            }
        }

        test("drawTriangle fully outside the target paints nothing") {
            grid(width = 3, height = 3).use { t ->
                Rasterizer.drawTriangle(t, 5, 0, 8, 0, 6, 3, Colors.RED, Pixel.Mode.Normal)
                painted(t) shouldBe emptySet()
            }
        }

        fun spriteOf(
            width: Int,
            height: Int,
            cells: Map<Pair<Int, Int>, Pixel>,
        ): Sprite {
            val sprite = SpriteCreationService.create(width, height, Pixmap.SampleMode.NORMAL, null)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    sprite.set(x, y, cells[x to y] ?: Colors.BLACK)
                }
            }
            return sprite
        }

        fun values(t: Sprite): Map<Pair<Int, Int>, Pixel> =
            (0 until t.width)
                .flatMap { x -> (0 until t.height).map { y -> (x to y) to t.get(x, y) } }
                .toMap()

        test("drawSprite blits the sprite pixels 1:1 at the destination") {
            grid(width = 6, height = 6).use { t ->
                val cells =
                    mapOf(
                        0 to 0 to Colors.RED,
                        1 to 0 to Colors.GREEN,
                        0 to 1 to Colors.BLUE,
                        1 to 1 to Colors.WHITE,
                    )
                spriteOf(2, 2, cells).use { s ->
                    Rasterizer.drawSprite(t, 2, 1, s, scale = 1, Sprite.Flip.NONE, Pixel.Mode.Normal)
                }

                for ((cell, color) in cells) {
                    t.get(cell.first + 2, cell.second + 1) shouldBe color
                }
                val outside = t.get(0, 0)
                outside shouldBe Colors.TRANSPARENT
                t.get(3, 0) shouldBe Colors.TRANSPARENT
            }
        }

        test("drawSprite flips mirror the blit inside the same footprint") {
            val cells =
                mapOf(
                    0 to 0 to Colors.RED,
                    1 to 0 to Colors.GREEN,
                    0 to 1 to Colors.BLUE,
                    1 to 1 to Colors.WHITE,
                )

            val expected: Map<Sprite.Flip, Map<Pair<Int, Int>, Pixel>> =
                mapOf(
                    Sprite.Flip.HORIZONTAL to
                        mapOf(
                            0 to 0 to Colors.GREEN, 1 to 0 to Colors.RED, 0 to 1 to Colors.WHITE,
                            1 to 1 to Colors.BLUE,
                        ),
                    Sprite.Flip.VERTICAL to
                        mapOf(
                            0 to 0 to Colors.BLUE, 1 to 0 to Colors.WHITE, 0 to 1 to Colors.RED,
                            1 to 1 to Colors.GREEN,
                        ),
                    Sprite.Flip.BOTH to
                        mapOf(
                            0 to 0 to Colors.WHITE, 1 to 0 to Colors.BLUE, 0 to 1 to Colors.GREEN,
                            1 to 1 to Colors.RED,
                        ),
                )

            for ((flip, flippedCells) in expected) {
                grid(width = 4, height = 4).use { t ->
                    spriteOf(2, 2, cells).use { s ->
                        Rasterizer.drawSprite(t, 0, 0, s, scale = 1, flip, Pixel.Mode.Normal)
                    }
                    for ((cell, color) in flippedCells) {
                        t.get(cell.first, cell.second) shouldBe color
                    }
                }
            }
        }

        test("drawSprite scale s paints an s x s block per source pixel") {
            grid(width = 6, height = 6).use { t ->
                val cells =
                    mapOf(
                        0 to 0 to Colors.RED,
                        0 to 1 to Colors.GREEN,
                    )
                spriteOf(1, 2, cells).use { s ->
                    Rasterizer.drawSprite(t, 1, 1, s, scale = 2, Sprite.Flip.NONE, Pixel.Mode.Normal)
                }

                for (x in 1..2) {
                    for (y in 1..2) {
                        t.get(x, y) shouldBe Colors.RED
                    }
                    for (y in 3..4) {
                        t.get(x, y) shouldBe Colors.GREEN
                    }
                }
                t.get(3, 3) shouldBe Colors.TRANSPARENT
            }
        }

        test("drawSprite unflipped and vertical flips match the per-pixel draw on random content") {
            grid(width = 8, height = 8).use { t ->
                val random = kotlin.random.Random(7)
                val cells =
                    (0 until 3)
                        .flatMap { y ->
                            (0 until 4).map { x ->
                                (x to y) to
                                    Pixel.rgba(
                                        random.nextInt(256), random.nextInt(256), random.nextInt(256),
                                        random
                                            .nextInt(256),
                                    )
                            }
                        }.toMap()

                for (flip in listOf(Sprite.Flip.NONE, Sprite.Flip.VERTICAL)) {
                    grid(width = 8, height = 8).use { reference ->
                        spriteOf(4, 3, cells).use { s ->
                            Rasterizer.drawSprite(t, 2, 2, s, scale = 1, flip, Pixel.Mode.Normal)
                            for (y in 0 until 3) {
                                for (x in 0 until 4) {
                                    val sx = x
                                    val sy = if (flip == Sprite.Flip.VERTICAL) 2 - y else y
                                    Rasterizer
                                        .draw(reference, 2 + x, 2 + y, cells[sx to sy]!!, Pixel.Mode.Normal)
                                }
                            }
                        }
                        values(t) shouldBe values(reference)
                    }
                }
            }
        }

        test("drawSprite clips a partial blit to the visible cells and rejects a fully outside one") {
            grid(width = 4, height = 4).use { t ->
                val cells =
                    mapOf(
                        0 to 0 to Colors.RED,
                        1 to 0 to Colors.GREEN,
                        0 to 1 to Colors.BLUE,
                        1 to 1 to Colors.WHITE,
                    )
                spriteOf(2, 2, cells).use { s ->
                    Rasterizer.drawSprite(t, -1, 1, s, scale = 1, Sprite.Flip.NONE, Pixel.Mode.Normal)
                }
                val changed = values(t).filterValues { it != Colors.TRANSPARENT }
                changed shouldBe mapOf(0 to 1 to Colors.GREEN, 0 to 2 to Colors.WHITE)
            }

            grid(width = 2, height = 2).use { t ->
                spriteOf(2, 2, emptyMap()).use { s ->
                    Rasterizer.drawSprite(t, 5, 0, s, scale = 1, Sprite.Flip.NONE, Pixel.Mode.Normal)
                }
                painted(t) shouldBe emptySet()
            }
        }

        test("drawSprite honors Mask and Alpha on the source pixels") {
            grid(width = 3, height = 3).use { t ->
                spriteOf(1, 1, mapOf(0 to 0 to Pixel.rgba(255, 0, 0, 128))).use { s ->
                    Rasterizer.drawSprite(t, 0, 0, s, scale = 1, Sprite.Flip.NONE, Pixel.Mode.Mask)
                }
                t.get(0, 0) shouldBe Colors.TRANSPARENT
            }

            grid(width = 3, height = 3).use { t ->
                Rasterizer.fillRect(t, 0, 0, 2, 2, Colors.WHITE, Pixel.Mode.Normal)
                spriteOf(1, 1, mapOf(0 to 0 to Colors.RED)).use { s ->
                    Rasterizer.drawSprite(t, 1, 1, s, scale = 1, Sprite.Flip.NONE, Pixel.Mode.Alpha(0.5f))
                }
                t.get(1, 1) shouldBe Pixel.rgba(255, 127, 127, 255)
            }
        }

        test("drawSprite with a non-positive scale paints nothing") {
            grid().use { t ->
                spriteOf(2, 2, mapOf(0 to 0 to Colors.RED)).use { s ->
                    Rasterizer.drawSprite(t, 0, 0, s, scale = 0, Sprite.Flip.NONE, Pixel.Mode.Normal)
                }
                painted(t) shouldBe emptySet()
            }
        }

        fun drawDelegate(original: DrawService): DrawService =
            object : DrawService {
                override fun draw(
                    target: MutablePixmap,
                    x: Int,
                    y: Int,
                    color: Pixel,
                    mode: Pixel.Mode,
                ) = original.draw(target, x, y, color, mode)
            }

        fun outlineDelegate(original: OutlineService): OutlineService =
            object : OutlineService {
                override fun drawLine(
                    target: MutablePixmap,
                    x0: Int,
                    y0: Int,
                    x1: Int,
                    y1: Int,
                    color: Pixel,
                    mode: Pixel.Mode,
                ) = original.drawLine(target, x0, y0, x1, y1, color, mode)

                override fun drawRect(
                    target: MutablePixmap,
                    x0: Int,
                    y0: Int,
                    x1: Int,
                    y1: Int,
                    color: Pixel,
                    mode: Pixel.Mode,
                ) = original.drawRect(target, x0, y0, x1, y1, color, mode)

                override fun drawCircle(
                    target: MutablePixmap,
                    cx: Int,
                    cy: Int,
                    radius: Int,
                    color: Pixel,
                    mode: Pixel.Mode,
                ) = original.drawCircle(target, cx, cy, radius, color, mode)

                override fun drawTriangle(
                    target: MutablePixmap,
                    x0: Int,
                    y0: Int,
                    x1: Int,
                    y1: Int,
                    x2: Int,
                    y2: Int,
                    color: Pixel,
                    mode: Pixel.Mode,
                ) = original.drawTriangle(target, x0, y0, x1, y1, x2, y2, color, mode)
            }

        fun fillDelegate(original: FillService): FillService =
            object : FillService {
                override fun fillRect(
                    target: MutablePixmap,
                    x0: Int,
                    y0: Int,
                    x1: Int,
                    y1: Int,
                    color: Pixel,
                    mode: Pixel.Mode,
                ) = original.fillRect(target, x0, y0, x1, y1, color, mode)

                override fun fillCircle(
                    target: MutablePixmap,
                    cx: Int,
                    cy: Int,
                    radius: Int,
                    color: Pixel,
                    mode: Pixel.Mode,
                ) = original.fillCircle(target, cx, cy, radius, color, mode)

                override fun fillTriangle(
                    target: MutablePixmap,
                    x0: Int,
                    y0: Int,
                    x1: Int,
                    y1: Int,
                    x2: Int,
                    y2: Int,
                    color: Pixel,
                    mode: Pixel.Mode,
                ) = original.fillTriangle(target, x0, y0, x1, y1, x2, y2, color, mode)
            }

        test("an active DrawService override is observed by a composite fill") {
            grid(width = 4, height = 4).use { t ->
                val original = DrawService.original
                var drawCalls = 0
                DrawService.override(
                    object : DrawService by drawDelegate(original) {
                        override fun draw(
                            target: MutablePixmap,
                            x: Int,
                            y: Int,
                            color: Pixel,
                            mode: Pixel.Mode,
                        ): Boolean {
                            drawCalls++
                            return original.draw(target, x, y, color, mode)
                        }
                    },
                )

                Rasterizer.fillRect(t, 0, 0, 1, 1, Colors.RED, Pixel.Mode.Alpha(0.5f))
                drawCalls shouldBe 4
            }
        }

        test("a decorator overriding only fillRect changes the fill while the outline delegates to original") {
            grid(width = 4, height = 4).use { t ->
                val original = FillService.original
                FillService.override(
                    object : FillService by fillDelegate(original) {
                        override fun fillRect(
                            target: MutablePixmap,
                            x0: Int,
                            y0: Int,
                            x1: Int,
                            y1: Int,
                            color: Pixel,
                            mode: Pixel.Mode,
                        ) {
                            original.fillRect(target, x0, y0, x1, y1, Colors.BLUE, mode)
                        }
                    },
                )

                Rasterizer.fillRect(t, 0, 0, 2, 2, Colors.RED, Pixel.Mode.Normal)
                t.get(0, 0) shouldBe Colors.BLUE

                Rasterizer.drawLine(t, 0, 3, 3, 3, Colors.RED, Pixel.Mode.Normal)
                for (x in 0..3) {
                    t.get(x, 3) shouldBe Colors.RED
                }
            }
        }

        test("drawTriangle resolves its edges through an active OutlineService.drawLine override") {
            grid(width = 4, height = 4).use { t ->
                val original = OutlineService.original
                var drawLineCalls = 0
                OutlineService.override(
                    object : OutlineService by outlineDelegate(original) {
                        override fun drawLine(
                            target: MutablePixmap,
                            x0: Int,
                            y0: Int,
                            x1: Int,
                            y1: Int,
                            color: Pixel,
                            mode: Pixel.Mode,
                        ) {
                            drawLineCalls++
                            original.drawLine(target, x0, y0, x1, y1, color, mode)
                        }
                    },
                )

                Rasterizer.drawTriangle(t, 0, 0, 3, 0, 0, 3, Colors.RED, Pixel.Mode.Normal)
                drawLineCalls shouldBe 3
            }
        }

        test("resetAll restores the engine raster defaults") {
            grid(width = 4, height = 4).use { t ->
                val original = FillService.original
                FillService.override(
                    object : FillService by fillDelegate(original) {
                        override fun fillRect(
                            target: MutablePixmap,
                            x0: Int,
                            y0: Int,
                            x1: Int,
                            y1: Int,
                            color: Pixel,
                            mode: Pixel.Mode,
                        ) {
                            original.fillRect(target, x0, y0, x1, y1, Colors.BLUE, mode)
                        }
                    },
                )

                dev.staticsanches.kge.overridable.KGEOverridable.Proxy
                    .resetAll()

                Rasterizer.fillRect(t, 0, 0, 2, 2, Colors.RED, Pixel.Mode.Normal)
                t.get(0, 0) shouldBe Colors.RED
            }
        }
    })
