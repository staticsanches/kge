package dev.staticsanches.kge.endian

/**
 * Marks elements that should be used with caution given that it depends on the system's endian.
 */
@RequiresOptIn(
	level = RequiresOptIn.Level.ERROR,
	message = "This is a sensitive piece of the KGE given that it depends on the system's endian. Use it cautiously."
)
@Retention(AnnotationRetention.BINARY)
annotation class KGEEndianDependent
