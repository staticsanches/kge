package dev.staticsanches.kge.buffer.service

import dev.staticsanches.kge.buffer.Buffer
import dev.staticsanches.kge.buffer.wrapper.BufferWrapperType
import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.resource.ResourceWrapper

expect interface BufferWrapperService : KGEExtensibleService {
    fun <B : Buffer> create(
        type: BufferWrapperType<B>,
        capacity: Int,
        name: String,
    ): ResourceWrapper<B>

    fun <B : Buffer> duplicate(
        original: ResourceWrapper<B>,
        newName: String?,
    ): ResourceWrapper<B>

    companion object : BufferWrapperService {
        override fun <B : Buffer> create(
            type: BufferWrapperType<B>,
            capacity: Int,
            name: String,
        ): ResourceWrapper<B>

        override fun <B : Buffer> duplicate(
            original: ResourceWrapper<B>,
            newName: String?,
        ): ResourceWrapper<B>

        override val servicePriority: Int
    }
}

expect val originalBufferWrapperServiceImplementation: BufferWrapperService
