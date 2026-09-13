package dev.staticsanches.kge.renderer.gl

import dev.staticsanches.kge.annotations.KGESensitiveAPI

/**
 * JVM handle representation: a thin wrapper over the LWJGL object name that
 * keeps the handle types distinct.
 *
 * The constructor is sensitive so an external GL service implementer can forge
 * a handle from a raw object name; the engine only transports them.
 */
actual class GLBuffer
    @KGESensitiveAPI
    constructor(
        val id: Int,
    )

actual class GLProgram
    @KGESensitiveAPI
    constructor(
        val id: Int,
    )

actual class GLShader
    @KGESensitiveAPI
    constructor(
        val id: Int,
    )

actual class GLTexture
    @KGESensitiveAPI
    constructor(
        val id: Int,
    )

actual class GLUniformLocation
    @KGESensitiveAPI
    constructor(
        val id: Int,
    )

actual class GLVertexArrayObject
    @KGESensitiveAPI
    constructor(
        val id: Int,
    )
