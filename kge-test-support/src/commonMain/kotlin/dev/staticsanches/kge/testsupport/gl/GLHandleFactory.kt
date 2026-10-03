package dev.staticsanches.kge.testsupport.gl

import dev.staticsanches.kge.renderer.gl.GLBuffer
import dev.staticsanches.kge.renderer.gl.GLProgram
import dev.staticsanches.kge.renderer.gl.GLShader
import dev.staticsanches.kge.renderer.gl.GLTexture
import dev.staticsanches.kge.renderer.gl.GLUniformLocation
import dev.staticsanches.kge.renderer.gl.GLVertexArrayObject

/**
 * Test-only handle factories: fabricate a handle of one kind without a GL
 * context, so [RecordingGLService] can round-trip them on every target. [seed]
 * identifies the handle for logging; web implementations ignore it.
 */
expect fun recordingTextureHandle(seed: Int): GLTexture

expect fun recordingProgramHandle(seed: Int): GLProgram

expect fun recordingShaderHandle(seed: Int): GLShader

expect fun recordingBufferHandle(seed: Int): GLBuffer

expect fun recordingVertexArrayHandle(seed: Int): GLVertexArrayObject

expect fun recordingUniformLocationHandle(seed: Int): GLUniformLocation
