package dev.staticsanches.kge.benchmark

/** The per-frame drawing load applied in a benchmark cell. */
internal enum class BenchmarkWorkload(
    val label: String,
) {
    /** The loop and the layer upload only: the target is cleared but nothing is drawn. */
    Empty("empty"),

    /** A mixed scene of raster primitives and sprite blits. */
    Render("render"),

    /** A screen of text decals as the engine default submits it: one instance per glyph. */
    TextPerGlyphStrip("text-perglyph-strip"),

    /** The same per-glyph submission as `GL_TRIANGLES`: a quad's fourth vertex is unused. */
    TextPerGlyphList("text-perglyph-list"),

    /** The same text as one triangle-list instance per string — the batching candidate. */
    TextMergedList("text-merged-list"),

    /** The per-glyph text through the pass-through decorator: the wrapper's own cost. */
    RendererPassthrough("renderer-passthrough"),

    /** Only the olc/`main` decal-mode guard: `blendFunc` issued on a mode change. */
    RendererBlend("renderer-blend"),

    /** The per-glyph text with renderer-side state dedupe only (lever 4). */
    RendererDedupe("renderer-dedupe"),

    /** The per-glyph text with one persistent vertex buffer only (lever 2). */
    RendererUpload("renderer-upload"),

    /** The per-glyph text with both renderer levers. */
    RendererBatched("renderer-batched"),
}

/** True for the text loads, which share one scene and differ in submission only. */
internal val BenchmarkWorkload.isText: Boolean
    get() =
        this == BenchmarkWorkload.TextPerGlyphStrip ||
            this == BenchmarkWorkload.TextPerGlyphList ||
            this == BenchmarkWorkload.TextMergedList ||
            this == BenchmarkWorkload.RendererPassthrough ||
            this == BenchmarkWorkload.RendererBlend ||
            this == BenchmarkWorkload.RendererDedupe ||
            this == BenchmarkWorkload.RendererUpload ||
            this == BenchmarkWorkload.RendererBatched

/** The loads every sweep cell crosses, from the empty baseline to rendering. */
internal val benchmarkWorkloads: List<BenchmarkWorkload> = BenchmarkWorkload.entries
