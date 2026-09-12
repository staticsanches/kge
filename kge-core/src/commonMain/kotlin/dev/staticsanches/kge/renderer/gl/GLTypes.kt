package dev.staticsanches.kge.renderer.gl

typealias GLbitfield = Int
typealias GLboolean = Boolean
typealias GLclampf = Float
typealias GLenum = Int
typealias GLfloat = Float
typealias GLint = Int
typealias GLintptr = Int
typealias GLsizeiptr = Int
typealias GLsizei = Int
typealias GLuint = Int

/**
 * A GL object name that common code transports but never constructs or
 * inspects: the JVM maps it to an `Int` (or a value class over one), the web
 * to the matching `WebGL*` DOM object. Only a platform backend creates one.
 */
expect class GLBuffer

/** A linked GL program — see [GLBuffer]. */
expect class GLProgram

/** A GL shader — see [GLBuffer]. */
expect class GLShader

/** A GL texture — see [GLBuffer]. */
expect class GLTexture

/** A GL uniform location — see [GLBuffer]. */
expect class GLUniformLocation

/** A GL vertex array object — see [GLBuffer]. */
expect class GLVertexArrayObject
