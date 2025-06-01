package dev.staticsanches.kge.buffer

import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

actual typealias Buffer = Buffer
actual typealias ByteBuffer = ByteBuffer
actual typealias IntBuffer = IntBuffer
actual typealias FloatBuffer = FloatBuffer
actual typealias ByteOrder = ByteOrder

actual val ByteOrder.isNative: Boolean
    get() = this === ByteOrder.nativeOrder()
