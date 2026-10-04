package dev.staticsanches.kge.text

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.ResourceScope
import kotlin.jvm.JvmInline
import kotlin.math.roundToInt

/**
 * A face at a size, owned by the scope that created it; the family holds the
 * payload those leases share and outlives every one of them.
 */
interface KGEFont : KGEResource {
    val face: Face
    val size: Size
    val family: Family get() = face.family

    /** Applied design coordinates; canonical, in tag order. */
    val axisCoordinates: Map<Axis.Tag, Axis.Value>

    /** The pixel box of [text]; no draw `scale` takes part. `""` is `(0, 0)`. */
    fun measureText(
        text: String,
        tabSizeInSpaces: Int,
    ): Int2D

    /** Draws [text] from the raw ([x], [y]) corner; `scale <= 0` is a no-op. */
    fun drawText(
        target: Pixmap.Mutable,
        x: Int,
        y: Int,
        text: String,
        color: Pixel,
        scale: Int,
        tabSizeInSpaces: Int,
        mode: Pixel.Mode,
    )

    /** Queues [text]'s cells from [position]; decal sign/zero rules apply. */
    fun drawTextDecal(
        position: Float2D,
        text: String,
        color: Pixel,
        scale: Float2D,
        tabSizeInSpaces: Int,
        screenSize: Int2D,
        decalMode: Decal.Mode,
        decalStructure: Decal.Structure,
        decalInstanceCollector: (DecalInstance) -> Unit,
    )

    /** A family's related faces; it owns the payload and native state. */
    interface Family : KGEResource {
        /** The family's preferred name. */
        val name: String
        val faces: List<Face>
        val defaultFace: Face
    }

    /** A selectable design; the family owns it, callers never close it. */
    interface Face {
        val family: Family

        /** The design's name, unique within its family. */
        val name: String

        /** Whether the design's advances are fixed; declared by the face, not measured. */
        val monospaced: Boolean

        /** Descriptors by tag, in `fvar` order; empty for the bitmap faces. */
        val axes: Map<Axis.Tag, Axis>

        /** Creates a configured font and adopts it into [scope]; a native face suspends during setup. */
        @KGESensitiveAPI
        suspend fun font(
            scope: ResourceScope,
            size: Size,
            axes: Map<Axis.Tag, Axis.Value> = emptyMap(),
        ): KGEFont
    }

    /** An OpenType variation axis. */
    data class Axis(
        val tag: Tag,
        val name: String,
        val min: Value,
        val default: Value,
        val max: Value,
        val hidden: Boolean,
    ) {
        /** Exactly four printable ASCII characters. */
        @JvmInline
        value class Tag private constructor(
            val raw: String,
        ) {
            companion object {
                val Weight: Tag = Tag("wght")
                val Width: Tag = Tag("wdth")
                val OpticalSize: Tag = Tag("opsz")
                val Slant: Tag = Tag("slnt")
                val Italic: Tag = Tag("ital")

                /** The [raw] as a [Tag], or `null` unless it is four printable ASCII characters. */
                fun ofOrNull(raw: String): Tag? = if (raw.length == 4 && raw.all { it in ' '..'~' }) Tag(raw) else null
            }
        }

        /** A canonical 16.16 design coordinate. */
        @JvmInline
        value class Value private constructor(
            val raw: Int,
        ) : Comparable<Value> {
            /** The value as a float, `raw / 65536`. */
            val floatValue: Float
                get() = raw / 65536f

            override fun compareTo(other: Value): Int = raw.compareTo(other.raw)

            /** The shortest spelling that round-trips through [ofOrNull], else the exact value. */
            override fun toString(): String {
                val negative = raw < 0
                // The two's-complement split, then the magnitude parts: -raw
                // overflows at Int.MIN_VALUE, while -high does not.
                val high = raw shr 16
                val low = raw and 0xFFFF
                val whole = if (negative) -high - (if (low == 0) 0 else 1) else high
                var remainder = if (negative) (65536 - low) and 0xFFFF else low
                val fraction = IntArray(16)
                for (index in fraction.indices) {
                    remainder *= 10
                    fraction[index] = remainder shr 16
                    remainder = remainder and 0xFFFF
                }

                fun numeral(fractionDigits: Int): String {
                    var integer = whole
                    val kept = fraction.copyOf(fractionDigits)
                    var index = fractionDigits - 1
                    if (fractionDigits < fraction.size && fraction[fractionDigits] >= 5) {
                        while (index >= 0 && kept[index] == 9) {
                            kept[index] = 0
                            index--
                        }
                        if (index >= 0) kept[index]++ else integer++
                    }
                    var end = fractionDigits
                    while (end > 0 && kept[end - 1] == 0) end--
                    if (end == 0) return integer.toString()
                    val text = StringBuilder()
                    text.append(integer)
                    text.append('.')
                    for (digit in 0 until end) text.append(kept[digit])
                    return text.toString()
                }

                for (fractionDigits in 0..fraction.size) {
                    val candidate = if (negative) "-" + numeral(fractionDigits) else numeral(fractionDigits)
                    if (ofOrNull(candidate.toFloat()) == this) return candidate
                }
                val exact = numeral(fraction.size)
                return if (negative) "-$exact" else exact
            }

            companion object {
                /** The exact 16.16 value of [raw]. */
                fun of(raw: Int): Value = Value(raw)

                /** The nearest 16.16 value to [value], or `null` when it is not representable. */
                fun ofOrNull(value: Float): Value? {
                    if (!value.isFinite()) return null
                    val scaled = value.toDouble() * 65536.0
                    if (scaled < Int.MIN_VALUE.toDouble() || scaled > Int.MAX_VALUE.toDouble()) return null
                    return Value(scaled.roundToInt())
                }
            }
        }
    }

    /** The configured font's base size in whole pixels; strictly positive. */
    @JvmInline
    value class Size private constructor(
        val px: Int,
    ) : Comparable<Size> {
        override fun compareTo(other: Size): Int = px.compareTo(other.px)

        companion object {
            /** The [px] as a [Size], or `null` when it is not strictly positive. */
            fun ofOrNull(px: Int): Size? = if (px > 0) Size(px) else null
        }
    }
}

/** The receiver as a [KGEFont.Size]; throws [IllegalArgumentException] when it is not strictly positive. */
val Int.fontPx: KGEFont.Size
    get() = KGEFont.Size.ofOrNull(this) ?: throw IllegalArgumentException("$this is not a positive font size")

/** The receiver as a design coordinate; throws [IllegalArgumentException] when it is not representable. */
val Int.axisValue: KGEFont.Axis.Value
    get() =
        KGEFont.Axis.Value.ofOrNull(toFloat())
            ?: throw IllegalArgumentException("$this is not a representable 16.16 axis value")

/** The receiver's nearest design coordinate; throws [IllegalArgumentException] when it is not representable. */
val Float.axisValue: KGEFont.Axis.Value
    get() =
        KGEFont.Axis.Value.ofOrNull(this)
            ?: throw IllegalArgumentException("$this is not a representable 16.16 axis value")
