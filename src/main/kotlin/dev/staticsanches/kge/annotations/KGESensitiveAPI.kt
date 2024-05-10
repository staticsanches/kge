package dev.staticsanches.kge.annotations

/**
 * Marks elements that should be used with caution.
 */
@RequiresOptIn(
	level = RequiresOptIn.Level.ERROR,
	message = "This is a sensitive piece of the KGE. Use it cautiously."
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class KGESensitiveAPI
