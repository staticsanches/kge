package dev.staticsanches.kge.buffer.service

import dev.staticsanches.kge.buffer.ByteBufferWrapper
import dev.staticsanches.kge.extensible.KGEExtensibleService

interface ByteBufferWrapperService : KGEExtensibleService {
    fun create(
        capacity: Int,
        name: String,
    ): ByteBufferWrapper

    fun duplicate(
        original: ByteBufferWrapper,
        newName: String?,
    ): ByteBufferWrapper

    companion object : ByteBufferWrapperService by KGEExtensibleService.getOptionalWithHigherPriority()
        ?: originalByteBufferWrapperServiceImplementation
}

expect val originalByteBufferWrapperServiceImplementation: ByteBufferWrapperService
