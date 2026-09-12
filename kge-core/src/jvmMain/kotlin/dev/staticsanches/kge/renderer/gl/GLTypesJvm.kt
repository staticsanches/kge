package dev.staticsanches.kge.renderer.gl

import dev.staticsanches.kge.annotations.KGESensitiveAPI

/**
 * JVM handle representation: a thin wrapper over the LWJGL object name. The
 * class keeps the handle types distinct (a texture cannot stand in for a
 * buffer). The constructor is public but sensitive so an external GL service
 * implementer can forge a handle from a raw object name; the engine itself only
 * transports them. [id] stays a normal read — the raw name is what the seam
 * moves between the engine and the backend. `@JvmInline value class` is
 * rejected by the modality check for an `expect class`, so this is a plain
 * class.
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
