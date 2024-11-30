package dev.staticsanches.kge.font

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D
import org.lwjgl.system.MemoryStack
import java.nio.IntBuffer
import java.util.Locale

class TextShaper private constructor(
    initializer: Initializer,
) {
    constructor(face: TypeFace, block: Initializer.() -> Unit = {}) : this(Initializer(face).apply(block))

    // Fixed control info
    private val tabSize = initializer.tabSize

    // Variable control info
    private var currentFace = initializer.face
    private var currentSize = initializer.size
    private var currentScriptTag = initializer.scriptTag
    private var currentLanguageTag = initializer.languageTag
    private var currentColor = initializer.color

    // Accumulated values
    private val lines = mutableListOf(Line())

    fun append(
        text: CharSequence,
        face: TypeFace = currentFace,
        size: Int = currentSize,
        scriptTag: ScriptTag = currentScriptTag,
        languageTag: String = currentLanguageTag,
        color: Pixel = currentColor,
    ): TextShaper {
        check(size >= 1)
        currentFace = face
        currentSize = size
        currentScriptTag = scriptTag
        currentLanguageTag = languageTag
        currentColor = color

        val iterator = lineBreakPattern.split(text).iterator()
        lines.last() += iterator.next()
        iterator.forEach { lines.add(Line().apply { this += it }) }
        return this
    }

    operator fun plusAssign(text: CharSequence) {
        append(text)
    }

    fun shape(
        topLeftCorner: Int2D,
        shapedGlyphs: MutableList<ShapedGlyph> = mutableListOf(),
    ): MutableList<ShapedGlyph> {
        MemoryStack.stackPush().use { memoryStack ->
            val iterator = lines.iterator()
            val firstLine = iterator.next()

            val maxLineLength = lines.maxOf { it.codePoints().count() }.toInt()
            val intBuffer = memoryStack.mallocInt(maxLineLength)

            var baselineOrigin = topLeftCorner.toFPU2D() + (FPU(0) by firstLine.maxAscender)
            baselineOrigin = firstLine.doShape(baselineOrigin, intBuffer, shapedGlyphs)
            iterator.forEach {
                baselineOrigin = it.shape(baselineOrigin, intBuffer, shapedGlyphs)
            }
        }
        return shapedGlyphs
    }

    data class ShapedGlyph(
        val face: TypeFace,
        val size: Int,
        val glyphIndex: Int,
        val penPosition: FPU2D,
        val color: Pixel,
    )

    class Initializer internal constructor(
        val face: TypeFace,
        var scriptTag: ScriptTag = ScriptTag.Latn,
        var languageTag: String = Locale.getDefault().toLanguageTag(),
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
            get() = (this as Iterable<Piece>).maxBy { it.shaper.height.value }.shaper.height
        val maxAscender: FPU
            get() = (this as Iterable<Piece>).maxBy { it.shaper.ascender.value }.shaper.ascender

        fun shape(
            lastBaselineOrigin: FPU2D,
            intBuffer: IntBuffer,
            shapedGlyphs: MutableList<ShapedGlyph>,
        ): FPU2D = doShape(lastBaselineOrigin + (FPU(0) by maxHeight), intBuffer, shapedGlyphs)

        fun doShape(
            currentBaselineOrigin: FPU2D,
            intBuffer: IntBuffer,
            shapedGlyphs: MutableList<ShapedGlyph>,
        ): FPU2D {
            // Fill the buffer with the used codepoints
            codePoints().forEach(intBuffer.clear()::put)

            var penPositionStart = currentBaselineOrigin
            forEach { piece ->
                penPositionStart = piece.shape(penPositionStart, intBuffer, shapedGlyphs)
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
            val face = currentFace
            val size = currentSize
            val scriptTag = currentScriptTag
            val languageTag = currentLanguageTag
            val color = currentColor

            val shaper = face.shaper(size)

            val isEmpty: Boolean
                get() = codePointsStartInclusive == codePointsEndExclusive

            var next: Piece? = null
                private set

            var endExclusive: Int = startInclusive
                private set
            var codePointsEndExclusive: Int = codePointsStartInclusive
                private set

            fun shape(
                penPositionStart: FPU2D,
                intBuffer: IntBuffer,
                shapedGlyphs: MutableList<ShapedGlyph>,
            ): FPU2D {
                if (isEmpty) {
                    return penPositionStart
                }
                return shaper.shape(
                    codePoints = intBuffer.position(codePointsStartInclusive).limit(codePointsEndExclusive),
                    language = languageTag,
                    script = scriptTag,
                    penPositionStart = penPositionStart,
                    color = color,
                    shapedGlyphs = shapedGlyphs,
                )
            }

            operator fun plus(otherText: String): Piece {
                check(next == null) { "This piece is not the tail: $this" }
                val piece =
                    if (
                        face == currentFace &&
                        size == currentSize &&
                        scriptTag == currentScriptTag &&
                        languageTag == currentLanguageTag &&
                        color == currentColor
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

                piece.endExclusive += otherText.length
                piece.codePointsEndExclusive += otherText.codePoints().count().toInt()

                return piece
            }

            override fun toString(): String = text.substring(startInclusive, endExclusive)
        }
    }

    private companion object {
        private val lineBreakPattern = "\\R".toPattern()
    }
}
