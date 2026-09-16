package dev.staticsanches.kge.engine

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.resource.ResourceScope

/** A carrier of the resource scope of a running engine. */
interface HasResourceScope {
    /** The scope that owns the running engine's resources; reading it outside a run fails fast. */
    @KGESensitiveAPI
    val resourceScope: ResourceScope
}
