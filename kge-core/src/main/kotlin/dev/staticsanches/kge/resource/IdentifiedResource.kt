package dev.staticsanches.kge.resource

class IdentifiedResource<T>(
    type: String,
    idCreator: () -> T,
    idDeleter: (T) -> Unit,
) : KGEResource {
    val id: T
        get() {
            check(!cleanable.cleaned) { "$representation has already been released and can not be used" }
            return field
        }

    private val representation: String
    private val cleanable: KGECleanable

    init {
        val id = idCreator()
        this.id = id
        representation = "$type $id"
        cleanable = KGELeakDetector.register(this, representation, ResourceCleanAction(id, idDeleter))
    }

    override fun close() = cleanable.clean()

    override fun toString(): String = if (cleanable.cleaned) "$representation (released)" else representation
}

private class ResourceCleanAction<T>(
    val id: T,
    val idDeleter: (T) -> Unit,
) : KGECleanAction {
    override fun invoke() = idDeleter(id)
}
