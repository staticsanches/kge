@file:Suppress("ktlint:standard:property-naming", "ConstPropertyName", "unused")

package dev.staticsanches.kge.utils

data object BytesSize {
    const val float: Int = Float.SIZE_BYTES
    const val int: Int = Int.SIZE_BYTES
    const val uint: Int = UInt.SIZE_BYTES

    const val vec2: Int = 2 * float
    const val vec3: Int = 3 * float
    const val vec4: Int = 4 * float

    const val ivec2: Int = 2 * int
    const val ivec3: Int = 3 * int
    const val ivec4: Int = 4 * int

    const val uvec2: Int = 2 * uint
    const val uvec3: Int = 3 * uint
    const val uvec4: Int = 4 * uint

    const val mat2: Int = 2 * vec2
    const val mat2x2: Int = mat2
    const val mat2x3: Int = 2 * vec3
    const val mat2x4: Int = 2 * vec4

    const val mat3: Int = 3 * vec3
    const val mat3x2: Int = 3 * vec2
    const val mat3x3: Int = mat3
    const val mat3x4: Int = 3 * vec4

    const val mat4: Int = 4 * vec4
    const val mat4x2: Int = 4 * vec2
    const val mat4x3: Int = 4 * vec3
    const val mat4x4: Int = mat4

    inline operator fun invoke(block: BytesSize.() -> Int): Int = BytesSize.block()
}
