package dev.staticsanches.kge.benchmark

/** The per-frame drawing load applied in a benchmark cell. */
internal enum class BenchmarkWorkload(
    val label: String,
) {
    /** The loop and the layer upload only: the target is cleared but nothing is drawn. */
    Empty("empty"),

    /** A mixed scene of raster primitives and sprite blits. */
    Render("render"),
}

/** The loads every sweep cell crosses, from the empty baseline to rendering. */
internal val benchmarkWorkloads: List<BenchmarkWorkload> = BenchmarkWorkload.entries
