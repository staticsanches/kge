package dev.staticsanches.kge.engine

/** A carrier of the current frame snapshot. */
interface HasTime {
    /** The snapshot of the last rendered frame; zero before the first frame. */
    val frame: FrameInfo
}
