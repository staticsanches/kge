package dev.staticsanches.kge.renderer.gl

/**
 * Test-only handle factories: fabricate a handle of one kind without a GL
 * context, so [RecordingGLService] can round-trip them on every target. [seed]
 * identifies the handle for logging; web implementations ignore it.
 */
internal expect fun recordingTextureHandle(seed: Int): GLTexture

internal expect fun recordingProgramHandle(seed: Int): GLProgram

internal expect fun recordingShaderHandle(seed: Int): GLShader

internal expect fun recordingBufferHandle(seed: Int): GLBuffer

internal expect fun recordingVertexArrayHandle(seed: Int): GLVertexArrayObject

internal expect fun recordingUniformLocationHandle(seed: Int): GLUniformLocation
