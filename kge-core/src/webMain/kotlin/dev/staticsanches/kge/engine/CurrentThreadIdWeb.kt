package dev.staticsanches.kge.engine

/** The browser runs on a single thread, so every call reports the same identity. */
internal actual fun currentThreadId(): Long = 0L
