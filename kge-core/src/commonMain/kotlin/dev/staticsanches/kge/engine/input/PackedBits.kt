package dev.staticsanches.kge.engine.input

/** A fixed-capacity bit field packed into 32-bit words. */
internal class PackedBits(
    bitCount: Int,
) {
    private val words = IntArray((bitCount + BITS_PER_WORD - 1) / BITS_PER_WORD)

    operator fun get(index: Int): Boolean = words[index / BITS_PER_WORD] and (1 shl (index % BITS_PER_WORD)) != 0

    operator fun set(
        index: Int,
        value: Boolean,
    ) {
        val word = index / BITS_PER_WORD
        val bit = 1 shl (index % BITS_PER_WORD)
        words[word] = if (value) words[word] or bit else words[word] and bit.inv()
    }

    fun clear() {
        words.fill(0)
    }

    private companion object {
        const val BITS_PER_WORD = 32
    }
}
