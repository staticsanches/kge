package dev.staticsanches.kge.renderer.gl

/**
 * Test-only handle factories: each fabricates a handle of one kind without a
 * GL context, so [RecordingGLService] can produce and round-trip them on every
 * target. [seed] identifies the handle for logging; the web implementations
 * ignore it because the DOM handle is opaque.
 */
internal expect fun recordingTextureHandle(seed: Int): GLTexture

internal expect fun recordingProgramHandle(seed: Int): GLProgram

internal expect fun recordingShaderHandle(seed: Int): GLShader

internal expect fun recordingBufferHandle(seed: Int): GLBuffer

internal expect fun recordingVertexArrayHandle(seed: Int): GLVertexArrayObject

internal expect fun recordingUniformLocationHandle(seed: Int): GLUniformLocation
