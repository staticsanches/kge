package dev.staticsanches.kge.utils

interface PeekingIterator<T> : Iterator<T> {
    fun peek(): T
}

fun <T> Iterator<T>.peeking(): PeekingIterator<T> = if (this is PeekingIteratorImpl) this else PeekingIteratorImpl(this)

private class PeekingIteratorImpl<T>(
    val delegate: Iterator<T>,
) : PeekingIterator<T> {
    var hasPeek = false
    var peek: T? = null

    override fun hasNext(): Boolean = hasPeek || delegate.hasNext()

    override fun peek(): T {
        if (!hasPeek) {
            peek = delegate.next()
            hasPeek = true
        }
        @Suppress("UNCHECKED_CAST")
        return peek as T
    }

    override fun next(): T {
        if (hasPeek) {
            @Suppress("UNCHECKED_CAST")
            val peek: T = peek as T
            this.peek = null
            hasPeek = false
            return peek
        }
        return delegate.next()
    }
}
