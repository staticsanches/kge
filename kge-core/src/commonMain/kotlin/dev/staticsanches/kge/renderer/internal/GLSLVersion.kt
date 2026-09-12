package dev.staticsanches.kge.renderer.internal

/**
 * The GLSL version of the built-in program's shaders: `330 core` on the JVM
 * (LWJGL GL33) and `300 es` on the web (WebGL2). The renderer is a common
 * default with no platform code of its own, and it holds no device, so this is
 * the smallest platform seam it needs.
 */
internal expect val glslVersion: String
