package dev.staticsanches.kge.renderer.internal

/**
 * The GLSL version of the built-in program's shaders: `330 core` on the JVM
 * (LWJGL GL33) and `300 es` on the web (WebGL2).
 */
internal expect val glslVersion: String
