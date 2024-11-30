package dev.staticsanches.kge.font

import org.lwjgl.util.freetype.FreeType

internal typealias FTError = Int

internal inline fun FTError.handleFTError(
    errorProvider: (String) -> Throwable = ::RuntimeException,
    messageFormatter: (errorCode: Int, errorString: String?) -> String,
) {
    if (this != 0) {
        throw errorProvider(messageFormatter(this, FreeType.FT_Error_String(this)))
    }
}
