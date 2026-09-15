package dev.staticsanches.kge.resource

import dev.staticsanches.kge.annotations.KGESensitiveAPI

/**
 * Owns an ordered group of [KGEResource]s and releases them as one unit. It is
 * created with its first element, so a live composite is never empty; the empty
 * list is its closed state. The owner appends elements and reads or iterates
 * them, failing fast once closed.
 */
internal class CompositeResource<T : KGEResource>(
    first: T,
) : KGEInternalResource {
    private val elements = mutableListOf(first)

    /** The number of elements; zero once the composite is closed. */
    val size: Int get() = elements.size

    /** The element at [index]. */
    operator fun get(index: Int): T {
        check(elements.isNotEmpty()) { "CompositeResource is closed" }
        return elements[index]
    }

    /** Appends [element]. */
    fun add(element: T) {
        check(elements.isNotEmpty()) { "CompositeResource is closed" }
        elements += element
    }

    /** Visits the elements in add order. */
    fun forEach(action: (T) -> Unit) {
        check(elements.isNotEmpty()) { "CompositeResource is closed" }
        elements.forEach(action)
    }

    /** Releases every element in reverse add order, then clears; idempotent. */
    @KGESensitiveAPI
    override fun close() {
        val toClose = elements.toList().asReversed()
        elements.clear()
        toClose.closeAll()
    }
}
