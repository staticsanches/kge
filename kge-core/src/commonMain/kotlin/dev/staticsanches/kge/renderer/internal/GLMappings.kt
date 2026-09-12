package dev.staticsanches.kge.renderer.internal

import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.GLenum

/** Maps the typed texture sampling filter to the raw GL parameter. */
internal fun Decal.Filter.toGLFilter(): GLenum =
    when (this) {
        Decal.Filter.NEAREST -> GL.NEAREST
        Decal.Filter.LINEAR -> GL.LINEAR
    }

/** Maps the typed texture edge behavior to the raw GL parameter. */
internal fun Decal.Wrap.toGLWrap(): GLenum =
    when (this) {
        Decal.Wrap.CLAMP_TO_EDGE -> GL.CLAMP_TO_EDGE
        Decal.Wrap.REPEAT -> GL.REPEAT
    }

/** The olc `glBlendFunc` pair for a decal mode. */
internal fun Decal.Mode.toGLBlend(): Pair<GLenum, GLenum> =
    when (this) {
        Decal.Mode.NORMAL -> GL.SRC_ALPHA to GL.ONE_MINUS_SRC_ALPHA
        Decal.Mode.ADDITIVE -> GL.SRC_ALPHA to GL.ONE
        Decal.Mode.MULTIPLICATIVE -> GL.DST_COLOR to GL.ONE_MINUS_SRC_ALPHA
        Decal.Mode.STENCIL -> GL.ZERO to GL.SRC_ALPHA
        Decal.Mode.ILLUMINATE -> GL.ONE_MINUS_SRC_ALPHA to GL.SRC_ALPHA
        Decal.Mode.WIREFRAME -> GL.SRC_ALPHA to GL.ONE_MINUS_SRC_ALPHA
    }

/** The olc primitive for a decal structure; `mode` overrides it for wireframe. */
internal fun Decal.Structure.toGLPrimitive(mode: Decal.Mode): GLenum =
    if (mode == Decal.Mode.WIREFRAME) {
        GL.LINE_LOOP
    } else {
        when (this) {
            Decal.Structure.LINE -> GL.LINES
            Decal.Structure.FAN -> GL.TRIANGLE_FAN
            Decal.Structure.STRIP -> GL.TRIANGLE_STRIP
            Decal.Structure.LIST -> GL.TRIANGLES
        }
    }
