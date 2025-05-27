package dev.staticsanches.kge.renderer.gl

import dev.staticsanches.kge.renderer.gl.service.GLTextureService

data object GL : GLTextureService by GLTextureService {
    // Texture target

    const val TEXTURE_2D: GLenum = 0x0DE1

    // Data type

    const val UNSIGNED_BYTE: GLenum = 0x1401

    // Pixel format

    const val RGBA: GLenum = 0x1908

    // Texture mag filter
    const val NEAREST: GLenum = 0x2600
    const val LINEAR: GLenum = 0x2601

    // Texture parameter name

    const val TEXTURE_MAG_FILTER: GLenum = 0x2800
    const val TEXTURE_MIN_FILTER: GLenum = 0x2801
    const val TEXTURE_WRAP_S: GLenum = 0x2802
    const val TEXTURE_WRAP_T: GLenum = 0x2803

    // Texture wrap mode

    const val REPEAT: GLenum = 0x2901
    const val CLAMP_TO_EDGE: GLenum = 0x812F
}
