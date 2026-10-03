@file:OptIn(KGESensitiveAPI::class)

package dev.staticsanches.kge.testsupport.gl

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.renderer.gl.GLBuffer
import dev.staticsanches.kge.renderer.gl.GLProgram
import dev.staticsanches.kge.renderer.gl.GLShader
import dev.staticsanches.kge.renderer.gl.GLTexture
import dev.staticsanches.kge.renderer.gl.GLUniformLocation
import dev.staticsanches.kge.renderer.gl.GLVertexArrayObject

actual fun recordingTextureHandle(seed: Int): GLTexture = GLTexture(seed)

actual fun recordingProgramHandle(seed: Int): GLProgram = GLProgram(seed)

actual fun recordingShaderHandle(seed: Int): GLShader = GLShader(seed)

actual fun recordingBufferHandle(seed: Int): GLBuffer = GLBuffer(seed)

actual fun recordingVertexArrayHandle(seed: Int): GLVertexArrayObject = GLVertexArrayObject(seed)

actual fun recordingUniformLocationHandle(seed: Int): GLUniformLocation = GLUniformLocation(seed)
