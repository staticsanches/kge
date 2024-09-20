package dev.staticsanches.kge.resource

class IntResource(
    type: String,
    idCreator: () -> Int,
    idDeleter: (Int) -> KGECleanAction,
) : KGEResource {
    val id: Int
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
        cleanable = KGELeakDetector.register(this, representation, idDeleter(id))
    }

    override fun close() = cleanable.clean()

    override fun toString(): String = if (cleanable.cleaned) "$representation (released)" else representation
}
