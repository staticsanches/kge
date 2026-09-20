package dev.staticsanches.kge.benchmark

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

private const val SHEET_WIDTH = 128
private const val SHEET_HEIGHT = 48

/** The merged list: two triangles over the default strip's four corners. */
private const val VERTS_PER_GLYPH = 6

private fun run(text: String) =
    mergedRunVertices(
        spriteWidth = SHEET_WIDTH,
        spriteHeight = SHEET_HEIGHT,
        position = Float2D(4f, 2f),
        text = text,
        tint = Colors.WHITE,
        scale = Float2D(1f, 1f),
        tabSizeInSpaces = 4,
        screenSize = Int2D(240, 48),
    )

private fun assertVertex(
    vertices: FlatVertices,
    index: Int,
    x: Float,
    y: Float,
    u: Float,
    v: Float,
) {
    vertices.x(index) shouldBe (x plusOrMinus 1e-5f)
    vertices.y(index) shouldBe (y plusOrMinus 1e-5f)
    vertices.u(index) shouldBe (u plusOrMinus 1e-5f)
    vertices.v(index) shouldBe (v plusOrMinus 1e-5f)
}

/**
 * The merged run must keep the default partial-decal geometry; the snapshot is
 * that path's own output and the shape test guards a builder drift.
 */
class MergedRunGeometryTest :
    FunSpec({
        test("one glyph is two triangles over the default quantised quad") {
            val vertices = run("A")

            vertices.vertexCount shouldBe 6
            assertVertex(vertices, 0, -0.9666667f, 0.9166667f, 0.0625008f, 0.3333354f)
            assertVertex(vertices, 1, -0.9666667f, 0.5833333f, 0.0625008f, 0.4999979f)
            assertVertex(vertices, 2, -0.8958333f, 0.5833333f, 0.1249992f, 0.4999979f)
            assertVertex(vertices, 3, -0.9666667f, 0.9166667f, 0.0625008f, 0.3333354f)
            assertVertex(vertices, 4, -0.8958333f, 0.5833333f, 0.1249992f, 0.4999979f)
            assertVertex(vertices, 5, -0.8958333f, 0.9166667f, 0.1249992f, 0.3333354f)
        }

        test("the mono walk advances one 8px cell per glyph") {
            val vertices = run("AA")

            vertices.vertexCount shouldBe 12
            (vertices.x(6) - vertices.x(0)) shouldBe (0.0666667f plusOrMinus 1e-5f)
            vertices.v(6) shouldBe (0.3333354f plusOrMinus 1e-5f)
        }

        test("a newline advances a whole cell height") {
            val vertices = run("A\nA")

            vertices.vertexCount shouldBe 12
            (vertices.y(6) - vertices.y(0)) shouldBe (-0.3333333f plusOrMinus 1e-5f)
        }

        test("every glyph is two triangles over one rectangle") {
            val vertices = run("AA")

            for (glyph in 0 until 2) {
                val base = glyph * VERTS_PER_GLYPH
                val left = vertices.x(base)
                val right = vertices.x(base + 2)
                val top = vertices.y(base)
                val bottom = vertices.y(base + 1)
                val u0 = vertices.u(base)
                val u1 = vertices.u(base + 2)
                val v0 = vertices.v(base)
                val v1 = vertices.v(base + 1)

                listOf(base, base + 1, base + 3).forEach { vertices.x(it) shouldBe left }
                listOf(base + 2, base + 4, base + 5).forEach { vertices.x(it) shouldBe right }
                listOf(base, base + 3, base + 5).forEach { vertices.y(it) shouldBe top }
                listOf(base + 1, base + 2, base + 4).forEach { vertices.y(it) shouldBe bottom }
                listOf(base, base + 1, base + 3).forEach { vertices.u(it) shouldBe u0 }
                listOf(base + 2, base + 4, base + 5).forEach { vertices.u(it) shouldBe u1 }
                listOf(base, base + 3, base + 5).forEach { vertices.v(it) shouldBe v0 }
                listOf(base + 1, base + 2, base + 4).forEach { vertices.v(it) shouldBe v1 }

                (left < right) shouldBe true
                (top > bottom) shouldBe true
                (u0 < u1) shouldBe true
                (v0 < v1) shouldBe true
            }
        }
    })
