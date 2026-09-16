package dev.staticsanches.kge.renderer.gl

actual fun recordingTextureHandle(seed: Int): GLTexture = GLTexture(seed)

actual fun recordingProgramHandle(seed: Int): GLProgram = GLProgram(seed)

actual fun recordingShaderHandle(seed: Int): GLShader = GLShader(seed)

actual fun recordingBufferHandle(seed: Int): GLBuffer = GLBuffer(seed)

actual fun recordingVertexArrayHandle(seed: Int): GLVertexArrayObject = GLVertexArrayObject(seed)

actual fun recordingUniformLocationHandle(seed: Int): GLUniformLocation = GLUniformLocation(seed)
