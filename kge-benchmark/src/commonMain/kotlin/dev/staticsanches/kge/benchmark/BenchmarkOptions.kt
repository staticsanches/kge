package dev.staticsanches.kge.benchmark

import dev.staticsanches.kge.math.vector.Int2D
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Every option the sweep selection consumes. */
private val benchmarkOptionKeys =
    setOf("sizes", "modes", "workloads", "highDpi", "warmup", "measure", "report")

/**
 * The sweep selection shared by both entry points: `key=value` tokens narrow the
 * sizes, modes, workloads and windows; [defaultModes] is the backend's modes.
 */
internal class BenchmarkOptions(
    private val values: Map<String, String>,
    private val defaultModes: List<BenchmarkMode>,
) {
    val sizes: List<Int2D> =
        values["sizes"]?.split(',')?.map { entry ->
            val (width, height) = entry.trim().split('x')
            Int2D(width.toInt(), height.toInt())
        } ?: benchmarkSizes

    private val highDpi: Boolean? = values["highDpi"]?.toBooleanStrict()

    /** The modes matching the label and HiDPI selection; never empty. */
    val modes: List<BenchmarkMode> = selectedModes()

    val workloads: List<BenchmarkWorkload> =
        values["workloads"]?.split(',')?.map { label ->
            val trimmed = label.trim()
            benchmarkWorkloads.firstOrNull { it.label == trimmed }
                ?: error("unknown workload '$trimmed', expected ${benchmarkWorkloads.map { it.label }}")
        } ?: benchmarkWorkloads

    val warmup: Duration = values["warmup"]?.toInt()?.seconds ?: benchmarkWarmup

    val measure: Duration = values["measure"]?.toInt()?.seconds ?: benchmarkMeasure

    /** Optional URL every finished cell is GET-reported to; only the web entry point sends it. */
    val reportUrl: String? = values["report"]

    private fun selectedModes(): List<BenchmarkMode> {
        val labels = values["modes"]?.split(',')?.map { it.trim() }
        val base =
            if (labels == null) {
                defaultModes
            } else {
                val known = defaultModes.map { it.label }.toSet()
                val unknown = labels.filterNot { it in known }
                check(unknown.isEmpty()) { "unknown modes $unknown, expected $known" }
                defaultModes.filter { it.label in labels }
            }
        val selected = base.filter { highDpi == null || it.highDpi == highDpi }
        check(selected.isNotEmpty()) { "the mode selection is empty" }
        return selected
    }
}

/** Parses `--key=value` (JVM) and `key=value` (web query) tokens. */
internal fun parseBenchmarkOptions(
    tokens: List<String>,
    defaultModes: List<BenchmarkMode>,
): BenchmarkOptions {
    val values = mutableMapOf<String, String>()
    for (token in tokens) {
        val normalized = token.removePrefix("--")
        val separator = normalized.indexOf('=')
        require(separator > 0) { "expected a key=value option, was '$token'" }
        val key = normalized.substring(0, separator)
        require(key in benchmarkOptionKeys) { "unknown benchmark option '$key'" }
        values[key] = normalized.substring(separator + 1)
    }
    return BenchmarkOptions(values, defaultModes)
}
