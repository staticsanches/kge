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
