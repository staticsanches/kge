@file:Suppress("unused")

package dev.staticsanches.kge.utils

data object BytesSize {
    const val FLOAT: Int = Float.SIZE_BYTES
    const val INT: Int = Int.SIZE_BYTES
    const val UINT: Int = UInt.SIZE_BYTES

    const val VEC2: Int = 2 * FLOAT
    const val VEC3: Int = 3 * FLOAT
    const val VEC4: Int = 4 * FLOAT

    const val IVEC2: Int = 2 * INT
    const val IVEC3: Int = 3 * INT
    const val IVEC4: Int = 4 * INT

    const val UVEC2: Int = 2 * UINT
    const val UVEC3: Int = 3 * UINT
    const val UVEC4: Int = 4 * UINT

    const val MAT2: Int = 2 * VEC2
    const val MAT2_2: Int = MAT2
    const val MAT2_3: Int = 2 * VEC3
    const val MAT2_4: Int = 2 * VEC4

    const val MAT3: Int = 3 * VEC3
    const val MAT3_2: Int = 3 * VEC2
    const val MAT3_3: Int = MAT3
    const val MAT3_4: Int = 3 * VEC4

    const val MAT4: Int = 4 * VEC4
    const val MAT4_2: Int = 4 * VEC2
    const val MAT4_3: Int = 4 * VEC3
    const val MAT4_4: Int = MAT4

    inline operator fun invoke(block: BytesSize.() -> Int): Int = BytesSize.block()
}
