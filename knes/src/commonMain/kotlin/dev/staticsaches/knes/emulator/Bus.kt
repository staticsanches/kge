package dev.staticsaches.knes.emulator

import dev.staticsaches.knes.utils.UInt16
import dev.staticsaches.knes.utils.UInt8

interface Bus {
    fun cpuWrite(
        address: UInt16,
        data: UInt8,
    )

    fun cpuRead(
        address: UInt16,
        readOnly: Boolean,
    ): UInt8
}
