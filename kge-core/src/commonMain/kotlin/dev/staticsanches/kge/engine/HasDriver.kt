package dev.staticsanches.kge.engine

import dev.staticsanches.kge.annotations.KGESensitiveAPI

/** A carrier of the platform driver of a running engine. */
interface HasDriver {
    /** The platform driver of the running engine; reading it outside a run fails fast. */
    @KGESensitiveAPI
    val driver: Driver
}
