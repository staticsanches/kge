package dev.staticsanches.kge.text.ttf

import kotlinx.coroutines.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.js.ExperimentalWasmJsInterop

private val moduleMutex = Mutex()
private var module: FreeType? = null

/**
 * The shared FreeType module, initialized once: the package builds a new wasm
 * instance per init, so every font in the process resolves to this one.
 */
@OptIn(ExperimentalWasmJsInterop::class)
internal suspend fun freeTypeModule(): FreeType {
    module?.let { return it }
    return moduleMutex.withLock {
        module ?: initFreeType().await().also { module = it }
    }
}
