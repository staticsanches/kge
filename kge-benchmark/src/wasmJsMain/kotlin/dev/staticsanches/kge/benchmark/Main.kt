@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.staticsanches.kge.benchmark

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import web.dom.ElementId
import web.dom.document
import web.html.HtmlSource
import web.window.window
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.Promise

/**
 * Runs the web sweep, paced by `requestAnimationFrame`, selecting cells through
 * the query string like the JVM arguments and `report=<url>` reporting them.
 */
fun main() {
    val output = document.getElementById(ElementId("output")) ?: return
    val raw = document.getElementById(ElementId("raw"))
    val query =
        window.location.search
            .removePrefix("?")
            .split('&')
            .filter { it.isNotEmpty() }
    // Read leniently, so a query the parser rejects can still report its own failure.
    val fallbackReport = query.firstOrNull { it.startsWith("report=") }?.removePrefix("report=")
    var reportUrl: String? = null
    MainScope().launch {
        try {
            val options = parseBenchmarkOptions(query, webModes)
            reportUrl = options.reportUrl
            reportUrl?.let { report(it, "START ${options.workloads.joinToString { workload -> workload.label }}") }
            val results = mutableListOf<BenchmarkResult>()
            runSweep(
                sizes = options.sizes,
                modes = options.modes,
                workloads = options.workloads,
                warmup = options.warmup,
                measure = options.measure,
            ) { result ->
                results += result
                val table = formatTable(results)
                output.innerHTML = HtmlSource(formatHtmlTable(results))
                raw?.textContent = table
                reportUrl?.let { report(it, table) }
            }
        } catch (failure: Throwable) {
            val message = "ERROR ${failure::class.simpleName}: ${failure.message}"
            // A headless run has no console; both the page and the collector carry the failure.
            output.textContent = message
            raw?.textContent = message
            (reportUrl ?: fallbackReport)?.let { report(it, message) }
        }
    }
}

/** Fires the report without awaiting it, so a slow collector never paces the sweep. */
private fun report(
    url: String,
    table: String,
) {
    val separator = if ('?' in url) '&' else '?'
    fetch("$url$separator" + "data=" + encodeQueryValue(table))
}

private fun encodeQueryValue(value: String): String {
    val hex = "0123456789ABCDEF"
    val encoded = StringBuilder()
    for (byte in value.encodeToByteArray()) {
        val code = byte.toInt() and 0xFF
        val char = code.toChar()
        if (char.isLetterOrDigit() || char in "-_.~") {
            encoded.append(char)
        } else {
            encoded.append('%').append(hex[(code shr 4) and 0xF]).append(hex[code and 0xF])
        }
    }
    return encoded.toString()
}

/** The browser `fetch` global; Kotlin/Wasm resolves the name to the JS binding. */
private external fun fetch(url: String): Promise<JsAny>
