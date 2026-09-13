@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.staticsanches.kge.resource

import js.memory.FinalizationRegistry
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsReference
import kotlin.js.toJsReference

/**
 * Web collection trigger: a FinalizationRegistry (js + wasmJs). Observation is
 * best-effort — the host collects when it collects, so the leak report is
 * never a deterministic signal.
 */
internal actual fun registerCollectionTrigger(
    obj: Any,
    onCollected: () -> Unit,
): KGECleanableHandle = WebCollectionTrigger(obj, onCollected)

private class WebCollectionTrigger(
    obj: Any,
    private val onCollected: () -> Unit,
) : KGECleanableHandle {
    private val registry = FinalizationRegistry<JsReference<String>> { onCollected() }

    // Held strongly by the registry while registered; unregister drops it.
    private val token: JsAny = this.toJsReference()

    init {
        registry.register(obj.toJsReference(), "leaked".toJsReference(), token)
    }

    override fun unregister() {
        registry.unregister(token)
    }
}
