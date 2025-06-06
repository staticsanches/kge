@file:Suppress("unused")

package dev.staticsanches.kge.image

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.configuration.KGEConfiguration
import dev.staticsanches.kge.image.service.PixelService
import dev.staticsanches.kge.image.service.PixelService.Companion.alphaFromNativeRGBA
import dev.staticsanches.kge.image.service.PixelService.Companion.blueFromNativeRGBA
import dev.staticsanches.kge.image.service.PixelService.Companion.fromNativeRGBA
import dev.staticsanches.kge.image.service.PixelService.Companion.greenFromNativeRGBA
import dev.staticsanches.kge.image.service.PixelService.Companion.invNativeRGBA
import dev.staticsanches.kge.image.service.PixelService.Companion.redFromNativeRGBA
import dev.staticsanches.kge.image.service.PixelService.Companion.toNativeRGBA
import kotlin.jvm.JvmInline
import kotlin.math.max
import kotlin.math.min

/**
 * Represents an [Int] value x that should lie in [0, 255].
 */
typealias IntColorComponent = Int

/**
 * Represents a [Float] value x that should lie in [0, 1].
 */
typealias FloatColorComponent = Float

/**
 * Represents a 32-Bit RGBA color.
 *
 * @see PixelService.toNativeRGBA
 */
@JvmInline
value class Pixel
    @KGESensitiveAPI
    constructor(
        val nativeRGBA: Int,
    ) {
        val r: IntColorComponent
            get() = redFromNativeRGBA(nativeRGBA)
        val g: IntColorComponent
            get() = greenFromNativeRGBA(nativeRGBA)
        val b: IntColorComponent
            get() = blueFromNativeRGBA(nativeRGBA)
        val a: IntColorComponent
            get() = alphaFromNativeRGBA(nativeRGBA)
        val rgba: UInt
            get() = fromNativeRGBA(nativeRGBA)

        operator fun component1(): IntColorComponent = r

        operator fun component2(): IntColorComponent = g

        operator fun component3(): IntColorComponent = b

        operator fun component4(): IntColorComponent = a

        fun inv(): Pixel = Pixel(invNativeRGBA(nativeRGBA))

        /**
         * Calculate the linear interpolation of this (start) and the informed [end].
         */
        fun lerp(
            end: Pixel,
            t: Float,
        ): Pixel = this * (1 - t) + end * t

        operator fun plus(other: Pixel): Pixel = rgba(r + other.r, g + other.g, b + other.b, a)

        operator fun minus(other: Pixel): Pixel = rgba(r - other.r, g - other.g, b - other.b, a)

        operator fun times(factor: Float): Pixel =
            rgba((r * factor).toInt(), (g * factor).toInt(), (b * factor).toInt(), a)

        operator fun div(factor: Float): Pixel =
            rgba((r / factor).toInt(), (g / factor).toInt(), (b / factor).toInt(), a)

        override fun toString(): String = defaultPixelFormat(this)

        sealed interface Mode {
            data object Normal : Mode

            data object Mask : Mode

            @JvmInline
            value class Alpha private constructor(
                val blendFactor: Float,
            ) : Mode {
                constructor(
                    blendFactor: Float = 1f,
                    @Suppress("unused") parameterToAvoidPlatformDeclarationClash: Boolean = true,
                ) : this(max(0f, min(1f, blendFactor)))
            }

            interface Custom : Mode {
                fun apply(
                    x: Int,
                    y: Int,
                    newPixel: Pixel,
                    oldPixel: Pixel,
                ): Pixel
            }
        }

        enum class Format {
            RGBA {
                override fun invoke(pixel: Pixel): String = "rgba(${pixel.r}, ${pixel.g}, ${pixel.b}, ${pixel.a})"
            },

            HEX {
                private val hexFormat =
                    HexFormat {
                        upperCase = true
                        number.minLength = 8
                        number.prefix = "#"
                    }

                override fun invoke(pixel: Pixel): String = pixel.rgba.toHexString(hexFormat)
            },
            ;

            abstract operator fun invoke(pixel: Pixel): String
        }

        companion object {
            var defaultPixelFormat: Format = Format.HEX

            fun rgba(rgba: UInt): Pixel = Pixel(toNativeRGBA(rgba.toInt()))

            fun rgba(
                r: IntColorComponent,
                g: IntColorComponent,
                b: IntColorComponent,
                a: IntColorComponent = KGEConfiguration.defaultPixelAlpha,
            ): Pixel = Pixel(toNativeRGBA(r, g, b, a))

            fun rgba(
                r: FloatColorComponent,
                g: FloatColorComponent,
                b: FloatColorComponent,
                a: FloatColorComponent = KGEConfiguration.defaultPixelAlpha / 255f,
            ): Pixel = Pixel(toNativeRGBA((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt(), (a * 255).toInt()))
        }
    }
