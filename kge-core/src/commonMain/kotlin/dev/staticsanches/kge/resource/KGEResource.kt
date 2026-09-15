package dev.staticsanches.kge.resource

import dev.staticsanches.kge.annotations.KGESensitiveAPI

/**
 * Marks objects that hold resources that must be released after use: [close]
 * is idempotent and using the object after close fails fast.
 */
interface KGEResource : AutoCloseable

/**
 * A [KGEResource] whose release is managed by the engine; [close] is a
 * sensitive API, so only engine code holding the resource closes it.
 */
interface KGEInternalResource : KGEResource {
    @KGESensitiveAPI
    override fun close()
}

/**
 * Closes every element, keeping going past a failure: the first throwable is
 * rethrown and the later ones are added to it as suppressed.
 */
internal fun <T : KGEResource> Iterable<T>.closeAll() {
    var failure: Throwable? = null
    for (element in this) {
        try {
            element.close()
        } catch (e: Throwable) {
            val first = failure
            if (first == null) failure = e else first.addSuppressed(e)
        }
    }
    failure?.let { throw it }
}

/**
 * Runs [block] with [this] and closes the receiver if it throws, so a failure
 * between allocation and ownership never leaks.
 */
inline fun <T : AutoCloseable, R> T.letClosingIfFailed(crossinline block: (T) -> R): R =
    try {
        block(this)
    } catch (e: Throwable) {
        try {
            close()
        } catch (closeException: Throwable) {
            e.addSuppressed(closeException)
        }
        throw e
    }

/**
 * Like [apply] but closes the receiver when [block] throws, so a failure
 * between allocation and hand-off never leaks.
 */
inline fun <T : AutoCloseable> T.applyClosingIfFailed(crossinline block: T.() -> Any): T =
    letClosingIfFailed {
        it.block()
        it
    }
