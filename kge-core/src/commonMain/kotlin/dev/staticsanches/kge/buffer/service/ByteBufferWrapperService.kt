package dev.staticsanches.kge.buffer.service

import dev.staticsanches.kge.buffer.ByteBufferWrapper
import dev.staticsanches.kge.extensible.KGEExtensibleService

expect interface ByteBufferWrapperService : KGEExtensibleService {
    fun create(
        capacity: Int,
        name: String,
    ): ByteBufferWrapper

    fun duplicate(
        original: ByteBufferWrapper,
        newName: String?,
    ): ByteBufferWrapper

    companion object : ByteBufferWrapperService {
        override fun create(
            capacity: Int,
            name: String,
        ): ByteBufferWrapper

        override fun duplicate(
            original: ByteBufferWrapper,
            newName: String?,
        ): ByteBufferWrapper

        override val servicePriority: Int
    }
}

expect val originalByteBufferWrapperServiceImplementation: ByteBufferWrapperService
