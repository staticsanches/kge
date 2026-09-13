package dev.staticsanches.kge.resource

/**
 * Marks objects that hold resources that must be released after use: [close]
 * is idempotent and using the object after close fails fast.
 */
interface KGEResource : AutoCloseable

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
