package dev.staticsanches.kge.annotations

/**
 * Marks types that have all methods/properties open.
 */
@MustBeDocumented
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class KGEAllOpen
