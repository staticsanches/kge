package dev.staticsanches.kge.resource

/**
 * Marks objects that hold resources that must be released after use.
 *
 * The contract: [close] is idempotent, and using the object after close fails
 * fast. Implementations hold at least one resource under the engine's
 * ownership — native memory, GPU objects, decoded image data, window and
 * layer objects.
 */
interface KGEResource : AutoCloseable

/**
 * Runs [block] with [this] and closes the receiver when the block failed —
 * the open resource is transferred to the successful result, so a
 * construction failure between allocation and ownership never leaks.
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
