package dev.staticsanches.kge.resource

import dev.staticsanches.kge.utils.pointerRepresentation

internal class PointerResource(
    type: String,
    extraInfo: String,
    handleCreator: () -> Long,
    handleDeleter: (Long) -> KGECleanAction,
) : KGEInternalResource {
    constructor(
        type: String,
        handleCreator: () -> Long,
        handleDeleter: (Long) -> KGECleanAction,
    ) : this(type, "", handleCreator, handleDeleter)

    val handle: Long
        get() {
            check(!cleanable.cleaned) { "$representation has already been closed and can not be used" }
            return field
        }

    private val representation: String
    private val cleanable: KGECleanable

    init {
        val id = handleCreator()
        this.handle = id
        representation = id.pointerRepresentation(type) + if (extraInfo.isNotBlank()) " $extraInfo" else ""
        cleanable = KGELeakDetector.register(this, representation, handleDeleter(id))
    }

    override fun close() = cleanable.clean()

    override fun toString(): String = if (cleanable.cleaned) "$representation (closed)" else representation
}
