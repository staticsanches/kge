package dev.staticsaches.knes.emulator

import dev.staticsaches.knes.utils.UInt16
import dev.staticsaches.knes.utils.UInt16.Companion.toUInt8
import dev.staticsaches.knes.utils.UInt8
import dev.staticsanches.kge.buffer.wrapper.ByteBufferWrapper
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.letClosingIfFailed

/**
 * Represents the NES console.
 */
class NES private constructor(
    ramWrapper: ByteBufferWrapper,
) : Bus,
    KGEResource by ramWrapper {
    val ram by ramWrapper
    val cpu = MP6502(this)

    init {
        with(ram.clear()) {
            while (hasRemaining()) put(0x00)
        }
    }

    override fun cpuWrite(
        address: UInt16,
        data: UInt8,
    ) {
        ram.put(address.toInt(), data.value.toByte())
    }

    override fun cpuRead(
        address: UInt16,
        readOnly: Boolean,
    ): UInt8 = ram.get(address.toInt()).toUInt8()

    companion object {
        operator fun invoke(): NES = ByteBufferWrapper(64 * 1_024) { "NES RAM ($it)" }.letClosingIfFailed { NES(it) }
    }
}
