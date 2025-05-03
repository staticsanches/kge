package dev.staticsanches.kge.buffer

import java.nio.ByteBuffer
import java.nio.ByteOrder

actual typealias ByteBuffer = ByteBuffer

actual typealias ByteOrder = ByteOrder

actual val ByteOrder.isNative: Boolean
    get() = this === ByteOrder.nativeOrder()
