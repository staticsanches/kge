package dev.staticsanches.kge.resource

import dev.staticsanches.kge.utils.pointerRepresentation

class PointerResource(
    type: String,
    extraInfo: String,
    handleCreator: () -> Long,
    handleDeleter: (Long) -> KGECleanAction,
) : KGEResource {
    constructor(
        type: String,
        handleCreator: () -> Long,
        handleDeleter: (Long) -> KGECleanAction,
    ) : this(type, "", handleCreator, handleDeleter)

    val handle: Long
        get() {
            check(!cleanable.cleaned) { "$representation has already been released and can not be used" }
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

    override fun toString(): String = if (cleanable.cleaned) "$representation (released)" else representation
}
