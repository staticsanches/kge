package dev.staticsanches.kge.image

import dev.staticsanches.kge.overridable.KGEOverridable
import kotlin.text.HexFormat
import kotlin.text.toHexString

/**
 * Formats a [Pixel] for display as a string.
 *
 * The engine default is the uppercase `#RRGGBBAA` hex form; a consumer may
 * replace the whole behavior for the process via
 * [override][KGEOverridable.Proxy.override], observed from the next call.
 */
interface PixelFormatService : KGEOverridable {
    /** Renders [pixel] as a display string. */
    fun format(pixel: Pixel): String

    companion object :
        KGEOverridable.Proxy<PixelFormatService>(PixelFormatService::class, HexPixelFormat),
        PixelFormatService {
        override fun format(pixel: Pixel): String = delegate.format(pixel)
    }
}

/** The engine default: `#RRGGBBAA`, uppercase, 8 hex digits. */
private object HexPixelFormat : PixelFormatService {
    private val hexFormat =
        HexFormat {
            upperCase = true
            number.minLength = 8
            number.prefix = "#"
        }

    override fun format(pixel: Pixel): String = pixel.rgba.toHexString(hexFormat)
}
