@file:Suppress("unused")

package dev.staticsanches.kge.resource

import dev.staticsanches.kge.annotations.KGESensitiveAPI

/**
 * Marks objects that may hold resources that should be released after use.
 */
interface KGEResource : AutoCloseable

/**
 * Marks objects that may hold resources that are managed by the engine.
 */
interface KGEInternalResource : KGEResource {
    @KGESensitiveAPI
    override fun close()
}

inline fun <T : AutoCloseable?, R> T.letClosingIfFailed(crossinline block: (T) -> R): R =
    try {
        block(this)
    } catch (e: Throwable) {
        if (this != null) {
            try {
                close()
            } catch (closeException: Throwable) {
                e.addSuppressed(closeException)
            }
        }
        throw e
    }

inline fun <T : AutoCloseable?> T.applyClosingIfFailed(crossinline block: T.() -> Any): T =
    letClosingIfFailed {
        it.block()
        it
    }
