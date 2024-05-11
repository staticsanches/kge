package dev.staticsanches.kge.resource

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import java.io.Closeable

/**
 * Marks objects that may hold resources that should be released after use.
 */
interface KGEResource : Closeable

/**
 * Marks objects that may hold resources that are managed by the engine.
 */
interface KGEInternalResource : KGEResource {

	@KGESensitiveAPI
	override fun close()

}

inline fun <T : AutoCloseable?, R> T.closeIfFailed(crossinline block: (T) -> R): R =
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

inline fun <T : AutoCloseable?> T.applyAndCloseIfFailed(crossinline block: (T) -> Any): T =
	closeIfFailed {
		block(it)
		it
	}
