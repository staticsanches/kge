package dev.staticsanches.kge.benchmark

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import web.dom.ElementId
import web.dom.document
import web.html.HtmlSource

fun main() {
    val output = document.getElementById(ElementId("output")) ?: return
    MainScope().launch {
        val results = mutableListOf<BenchmarkResult>()
        runSweep(
            sizes = benchmarkSizes,
            modes = webModes,
            workloads = benchmarkWorkloads,
            warmup = benchmarkWarmup,
            measure = benchmarkMeasure,
        ) { result ->
            results += result
            output.innerHTML = HtmlSource(formatHtmlTable(results))
        }
    }
}
