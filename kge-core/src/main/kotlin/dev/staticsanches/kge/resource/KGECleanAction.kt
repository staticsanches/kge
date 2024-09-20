@file:Suppress("unused")

package dev.staticsanches.kge.resource

typealias KGECleanAction = () -> Unit

inline fun <T : KGECleanAction?, R> T.invokeIfFailed(crossinline block: (T) -> R): R =
    try {
        block(this)
    } catch (e: Throwable) {
        if (this != null) {
            try {
                invoke()
            } catch (closeException: Throwable) {
                e.addSuppressed(closeException)
            }
        }
        throw e
    }

inline fun <T : KGECleanAction?> T.applyAndInvokeIfFailed(crossinline block: (T) -> Any): T =
    invokeIfFailed {
        block(it)
        it
    }

inline fun <T : KGECleanAction?, R> T.use(block: (T) -> R): R {
    var cause: Throwable? = null
    try {
        return block(this)
    } catch (e: Throwable) {
        cause = e
        throw e
    } finally {
        when {
            this == null -> {}
            cause == null -> invoke()
            else ->
                try {
                    invoke()
                } catch (cleanException: Throwable) {
                    cause.addSuppressed(cleanException)
                }
        }
    }
}

/**
 * Infix function to allow combination of [KGECleanAction]s.
 */
infix fun KGECleanAction.andThen(other: KGECleanAction?): KGECleanAction {
    if (other == null) {
        return this
    }
    return {
        var error: Throwable? = null
        try {
            invoke()
        } catch (e: Throwable) {
            error = e
        }
        try {
            other.invoke()
        } catch (e: Throwable) {
            if (error == null) {
                error = e
            } else {
                error.addSuppressed(e)
            }
        }
        if (error != null) {
            throw error
        }
    }
}
