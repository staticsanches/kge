@file:Suppress("MemberVisibilityCanBePrivate")

package dev.staticsanches.kge.font

import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.closeIfFailed
import dev.staticsanches.kge.utils.invokeForAll
import dev.staticsanches.kge.utils.invokeForAllRemoving
import java.io.InputStream
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents a typeface (with a [faceIndex] on a loaded file) and does not have size information.
 *
 * Since the font data is shared to improve performance and memory usage, ONLY release this resource when you are done
 * manipulating text using this font face.
 */
class TypeFace private constructor(
    internal val internalFace: FreeTypeFace,
) : KGEResource {
    val faceIndex: Long by internalFace::faceIndex
    val name: String by internalFace::name

    private val shapers = ConcurrentHashMap<Int, HarfBuzzShaper>()

    internal fun shaper(size: Int): HarfBuzzShaper = shapers.computeIfAbsent(size) { HarfBuzzShaper(this, it) }

    override fun close() = invokeForAll(ShapersCleanAction(shapers.values)::invoke, internalFace::close) { it() }

    override fun toString(): String = name

    companion object {
        fun load(
            fileName: String,
            faceIndex: Long = 0,
        ): TypeFace = FreeTypeFace.load(fileName, faceIndex).closeIfFailed(::TypeFace)

        fun load(
            url: URL,
            faceIndex: Long = 0,
        ): TypeFace = FreeTypeFace.load(url, faceIndex).closeIfFailed(::TypeFace)

        fun load(
            isProvider: () -> InputStream,
            faceIndex: Long = 0,
        ): TypeFace = FreeTypeFace.load(isProvider, faceIndex).closeIfFailed(::TypeFace)
    }
}

@JvmInline
private value class ShapersCleanAction(
    val shapers: MutableCollection<HarfBuzzShaper>,
) : KGECleanAction {
    override fun invoke() = shapers.invokeForAllRemoving { it.close() }
}
