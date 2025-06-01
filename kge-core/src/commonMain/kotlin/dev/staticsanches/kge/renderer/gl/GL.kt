@file:Suppress("unused", "SpellCheckingInspection")

package dev.staticsanches.kge.renderer.gl

import dev.staticsanches.kge.renderer.gl.service.GLService

data object GL : GLService by GLService {
    // Blending factors

    const val ZERO: GLenum = 0
    const val ONE: GLenum = 1
    const val SRC_COLOR: GLenum = 0x0300
    const val ONE_MINUS_SRC_COLOR: GLenum = 0x0301
    const val SRC_ALPHA: GLenum = 0x0302
    const val ONE_MINUS_SRC_ALPHA: GLenum = 0x0303
    const val DST_ALPHA: GLenum = 0x0304
    const val ONE_MINUS_DST_ALPHA: GLenum = 0x0305
    const val DST_COLOR: GLenum = 0x0306
    const val ONE_MINUS_DST_COLOR: GLenum = 0x0307
    const val SRC_ALPHA_SATURATE: GLenum = 0x0308

    // Texture target

    const val TEXTURE_2D: GLenum = 0x0DE1

    // Data type

    const val BYTE: GLenum = 0x1400
    const val UNSIGNED_BYTE: GLenum = 0x1401
    const val SHORT: GLenum = 0x1402
    const val UNSIGNED_SHORT: GLenum = 0x1403
    const val INT: GLenum = 0x1404
    const val UNSIGNED_INT: GLenum = 0x1405
    const val FLOAT: GLenum = 0x1406

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

    // Shaders

    const val FRAGMENT_SHADER: GLenum = 0x8B30
    const val VERTEX_SHADER: GLenum = 0x8B31

    // Buffer Objects

    const val ARRAY_BUFFER: GLenum = 0x8892

    const val STREAM_DRAW: GLenum = 0x88E0
    const val STATIC_DRAW: GLenum = 0x88E4
    const val DYNAMIC_DRAW: GLenum = 0x88E8

    // Clear buffer masks

    const val DEPTH_BUFFER_BIT: GLenum = 0x00000100
    const val STENCIL_BUFFER_BIT: GLenum = 0x00000400
    const val COLOR_BUFFER_BIT: GLenum = 0x00004000

    // Begin mode

    const val POINTS: GLenum = 0x0000
    const val LINES: GLenum = 0x0001
    const val LINE_LOOP: GLenum = 0x0002
    const val LINE_STRIP: GLenum = 0x0003
    const val TRIANGLES: GLenum = 0x0004
    const val TRIANGLE_STRIP: GLenum = 0x0005
    const val TRIANGLE_FAN: GLenum = 0x0006

    // Enable capabilities

    const val CULL_FACE: GLenum = 0x0B44
    const val BLEND: GLenum = 0x0BE2
    const val DITHER: GLenum = 0x0BD0
    const val STENCIL_TEST: GLenum = 0x0B90
    const val DEPTH_TEST: GLenum = 0x0B71
    const val SCISSOR_TEST: GLenum = 0x0C11
    const val POLYGON_OFFSET_FILL: GLenum = 0x8037
    const val SAMPLE_ALPHA_TO_COVERAGE: GLenum = 0x809E
    const val SAMPLE_COVERAGE: GLenum = 0x80A0

    // Stencil functions

    const val NEVER: GLenum = 0x0200
    const val LESS: GLenum = 0x0201
    const val EQUAL: GLenum = 0x0202
    const val LEQUAL: GLenum = 0x0203
    const val GREATER: GLenum = 0x0204
    const val NOTEQUAL: GLenum = 0x0205
    const val GEQUAL: GLenum = 0x0206
    const val ALWAYS: GLenum = 0x0207
}
