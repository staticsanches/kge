package dev.staticsanches.kge.image

import kotlin.jvm.JvmInline

/**
 * A 32-bit RGBA color.
 *
 * The [nativeRGBA] value stores the color little-endian packed —
 * `R | G shl 8 | B shl 16 | A shl 24` — so its memory bytes read R, G, B, A, the channel
 * order RGBA surfaces and GL_RGBA uploads expect. All planned targets run little-endian,
 * so no conversion is ever needed. [rgba] holds the same color in the canonical
 * `0xRRGGBBAA` value convention (alpha in the low byte); the factories take either form.
 * The constructor is private: a `Pixel` is created through the factories or `Colors`.
 *
 * @property nativeRGBA the little-endian packed value (memory bytes read R,G,B,A).
 * @property rgba the canonical `0xRRGGBBAA` value (alpha in the low byte).
 */
@JvmInline
value class Pixel
    private constructor(
        val nativeRGBA: Int,
    ) {
        /** The red channel in [0, 255]. */
        val r: Int
            get() = nativeRGBA and 0xFF

        /** The green channel in [0, 255]. */
        val g: Int
            get() = (nativeRGBA ushr 8) and 0xFF

        /** The blue channel in [0, 255]. */
        val b: Int
            get() = (nativeRGBA ushr 16) and 0xFF

        /** The alpha channel in [0, 255]. */
        val a: Int
            get() = (nativeRGBA ushr 24) and 0xFF

        /** The canonical `0xRRGGBBAA` value (alpha in the low byte). */
        val rgba: UInt
            get() = ((r shl 24) or (g shl 16) or (b shl 8) or a).toUInt()

        operator fun component1(): Int = r

        operator fun component2(): Int = g

        operator fun component3(): Int = b

        operator fun component4(): Int = a

        /** Inverts the RGB channels (255 - channel), keeping the alpha. */
        fun inv(): Pixel = Pixel(compose(255 - r, 255 - g, 255 - b, a))

        /**
         * Linear interpolation: `this * (1 - t) + end * t`, channel-wise, truncated,
         * with the receiver's alpha kept.
         */
        fun lerp(
            end: Pixel,
            t: Float,
        ): Pixel = this * (1f - t) + end * t

        /** Adds the RGB channels (saturating at 255), keeping the receiver's alpha. */
        operator fun plus(other: Pixel): Pixel = Pixel(compose(r + other.r, g + other.g, b + other.b, a))

        /** Subtracts the RGB channels (saturating at 0), keeping the receiver's alpha. */
        operator fun minus(other: Pixel): Pixel = Pixel(compose(r - other.r, g - other.g, b - other.b, a))

        /** Scales the RGB channels (truncating, saturating at [0, 255]), keeping the alpha. */
        operator fun times(factor: Float): Pixel =
            Pixel(compose((r * factor).toInt(), (g * factor).toInt(), (b * factor).toInt(), a))

        /** Divides the RGB channels (truncating, saturating at [0, 255]), keeping the alpha. */
        operator fun div(factor: Float): Pixel =
            Pixel(compose((r / factor).toInt(), (g / factor).toInt(), (b / factor).toInt(), a))

        /**
         * Renders the pixel through [PixelFormatService]. The engine default is
         * the uppercase `#RRGGBBAA` hex form; the display representation is an
         * engine extension capability and may be replaced for the whole process.
         */
        override fun toString(): String = PixelFormatService.format(this)

        companion object {
            private fun compose(
                r: Int,
                g: Int,
                b: Int,
                a: Int,
            ): Int =
                r.coerceIn(0, 255) or
                    (g.coerceIn(0, 255) shl 8) or
                    (b.coerceIn(0, 255) shl 16) or
                    (a.coerceIn(0, 255) shl 24)

            /** Creates a pixel from RGBA channels; out-of-range channels saturate to [0, 255]. */
            fun rgba(
                r: Int,
                g: Int,
                b: Int,
                a: Int = 0xFF,
            ): Pixel = Pixel(compose(r, g, b, a))

            /** Creates a pixel from the `0xRRGGBBAA` value convention (alpha in the low byte). */
            fun rgba(rgba: UInt): Pixel =
                Pixel(
                    compose(
                        ((rgba shr 24) and 0xFFu).toInt(),
                        ((rgba shr 16) and 0xFFu).toInt(),
                        ((rgba shr 8) and 0xFFu).toInt(),
                        (rgba and 0xFFu).toInt(),
                    ),
                )
        }
    }
