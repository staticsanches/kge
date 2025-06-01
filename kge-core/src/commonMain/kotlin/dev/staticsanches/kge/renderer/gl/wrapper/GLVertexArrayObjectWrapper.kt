package dev.staticsanches.kge.renderer.gl.wrapper

import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.GLVertexArrayObject
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.toCleanerProvider

typealias GLVertexArrayObjectWrapper = ResourceWrapper<GLVertexArrayObject>

fun GLVertexArrayObjectWrapper(name: String? = null): GLVertexArrayObjectWrapper =
    ResourceWrapper(
        { "VAO ${name ?: it}" },
        GL::createVertexArray,
        GL::deleteVertexArray.toCleanerProvider(),
    )
