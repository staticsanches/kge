package dev.staticsanches.kge.renderer.gl.wrapper

import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.GLBuffer
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.toCleanerProvider

typealias GLBufferWrapper = ResourceWrapper<GLBuffer>

inline fun GLBufferWrapper(nameFactory: (GLBuffer) -> String = { "GLBuffer $it" }): GLBufferWrapper =
    ResourceWrapper(nameFactory, GL::createBuffer, GL::deleteBuffer.toCleanerProvider())
