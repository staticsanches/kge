package dev.staticsanches.kge.renderer.gl

internal actual fun recordingTextureHandle(seed: Int): GLTexture = GLTexture(seed)

internal actual fun recordingProgramHandle(seed: Int): GLProgram = GLProgram(seed)

internal actual fun recordingShaderHandle(seed: Int): GLShader = GLShader(seed)

internal actual fun recordingBufferHandle(seed: Int): GLBuffer = GLBuffer(seed)

internal actual fun recordingVertexArrayHandle(seed: Int): GLVertexArrayObject = GLVertexArrayObject(seed)

internal actual fun recordingUniformLocationHandle(seed: Int): GLUniformLocation = GLUniformLocation(seed)
