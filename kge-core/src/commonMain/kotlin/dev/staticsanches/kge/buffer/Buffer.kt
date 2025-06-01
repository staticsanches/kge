@file:Suppress("unused")

package dev.staticsanches.kge.buffer

expect abstract class Buffer {
    fun capacity(): Int

    fun position(): Int

    open fun position(newPosition: Int): Buffer

    fun limit(): Int

    open fun limit(newLimit: Int): Buffer

    open fun mark(): Buffer

    open fun clear(): Buffer

    fun remaining(): Int

    fun hasRemaining(): Boolean

    open fun reset(): Buffer

    open fun flip(): Buffer
}
