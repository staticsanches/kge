package dev.staticsanches.kge.font

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import java.util.stream.Stream
import kotlin.streams.asSequence

class TextShaper private constructor(
    initializer: Initializer,
) {
    constructor(face: TypeFace, block: Initializer.() -> Unit = {}) : this(Initializer(face).apply(block))

    // Fixed control info
    private val tabSize = initializer.tabSize

    // Variable control info
    var face = initializer.face
    var size = initializer.size
        set(value) {
            check(value > 0) { "Invalid size: $value" }
            field = value
        }
    var color = initializer.color

    // Accumulated values
    private val lines = mutableListOf(Line())

    fun append(
        text: CharSequence,
        face: TypeFace = this@TextShaper.face,
        size: Int = this@TextShaper.size,
        color: Pixel = this@TextShaper.color,
    ): TextShaper {
        check(size > 0)
        this@TextShaper.face = face
        this@TextShaper.size = size
        this@TextShaper.color = color

        val iterator = lineBreakPattern.split(text).iterator()
        lines.last() += iterator.next()
        iterator.forEach { lines.add(Line().apply { this += it }) }
        return this
    }

    operator fun plusAssign(text: CharSequence) {
        append(text)
    }

    fun shape(): MutableList<ShapedGlyph> {
        val shapedGlyphs = mutableListOf<ShapedGlyph>()
        val iterator = lines.iterator()
        val firstLine = iterator.next()
        var baselineOrigin = FPU(0) by firstLine.maxAscender
        baselineOrigin = firstLine.doShape(baselineOrigin, shapedGlyphs)
        iterator.forEach { line ->
            baselineOrigin = line.shape(baselineOrigin, shapedGlyphs)
        }
        return shapedGlyphs
    }

    override fun toString(): String = lines.joinToString("\n")

    data class ShapedGlyph(
        val face: TypeFace,
        val size: Int,
        val glyphIndex: Int,
        val color: Pixel,
        val origin: FPU2D,
    )

    class Initializer internal constructor(
        val face: TypeFace,
        var color: Pixel = Colors.BLACK,
    ) {
        var size: Int = 12
            set(value) {
                check(value > 0)
                field = value
            }

        var tabSize: Int = 4
            set(value) {
                check(value > 0)
                field = value
            }
    }

    private inner class Line private constructor(
        private val text: StringBuilder,
    ) : CharSequence by text,
        Iterable<Line.Piece> {
        constructor() : this(StringBuilder())

        private val head: Piece = Piece(0, 0)
        private var tail: Piece = head

        private val maxHeight: FPU
            get() = (this as Iterable<Piece>).asSequence().map { it.atlas.baselineToBaseline }.maxOrNull() ?: FPU(0)
        val maxAscender: FPU
            get() =
                (this as Iterable<Piece>)
                    .asSequence()
                    .flatMap { it.glyphs.asSequence() }
                    .map { -it.bearing.y }
                    .maxOrNull() ?: FPU(0)

        fun shape(
            lastBaselineOrigin: FPU2D,
            shapedGlyphs: MutableList<ShapedGlyph>,
        ): FPU2D = doShape(lastBaselineOrigin + (FPU(0) by maxHeight), shapedGlyphs)

        fun doShape(
            currentBaselineOrigin: FPU2D,
            shapedGlyphs: MutableList<ShapedGlyph>,
        ): FPU2D {
            var penPositionStart = currentBaselineOrigin
            forEach { piece ->
                penPositionStart = piece.shape(penPositionStart, shapedGlyphs)
            }
            return currentBaselineOrigin
        }

        operator fun plusAssign(text: String) {
            tail += text
        }

        override fun iterator(): Iterator<Piece> =
            iterator {
                var current: Piece? = head
                do {
                    if (current != null) {
                        yield(current)
                    }
                } while (current?.next?.also { current = current.next } != null)
            }

        override fun toString(): String = text.toString()

        private inner class Piece(
            val startInclusive: Int,
            val codePointsStartInclusive: Int,
        ) {
            val face = this@TextShaper.face
            val size = this@TextShaper.size
            val color = this@TextShaper.color

            val atlas: GlyphAtlas
                get() = face.atlas(size)

            val glyphs: Stream<GlyphAtlas.GlyphInfo>
                get() =
                    text
                        .codePoints()
                        .skip(codePointsStartInclusive.toLong())
                        .limit((codePointsEndExclusive - codePointsStartInclusive).toLong())
                        .map(face::glyphIndex)
                        .mapToObj(atlas::glyph)

            private val isEmpty: Boolean
                get() = codePointsStartInclusive == codePointsEndExclusive

            var next: Piece? = null
                private set

            private var endExclusive: Int = startInclusive
            private var codePointsEndExclusive: Int = codePointsStartInclusive

            fun shape(
                penPositionStart: FPU2D,
                shapedGlyphs: MutableList<ShapedGlyph>,
            ): FPU2D {
                if (isEmpty) {
                    return penPositionStart
                }
                var penPosition = penPositionStart
                glyphs
                    .forEach { glyph ->
                        shapedGlyphs +=
                            ShapedGlyph(
                                face = face,
                                size = size,
                                glyphIndex = glyph.index,
                                color = color,
                                origin = penPosition,
                            )
                        penPosition += glyph.advance
                    }
                return penPosition
            }

            operator fun plus(otherText: String): Piece {
                check(next == null) { "This piece is not the tail: $this" }
                val piece =
                    if (
                        this@Piece.face == this@TextShaper.face &&
                        this@Piece.size == this@TextShaper.size &&
                        this@Piece.color == this@TextShaper.color
                    ) {
                        this
                    } else {
                        Piece(endExclusive, codePointsEndExclusive).also { next = it }
                    }

                otherText.forEach { char ->
                    if (char != '\t') {
                        text.append(char)
                    } else {
                        repeat(tabSize - text.length % tabSize) { text.append(' ') }
                    }
                }

                piece.endExclusive = text.length
                piece.codePointsEndExclusive = text.codePointCount(0, text.length)

                return piece
            }

            override fun toString(): String = text.substring(startInclusive, endExclusive)
        }
    }

    private companion object {
        private val lineBreakPattern = "\\R".toPattern()
    }
}
