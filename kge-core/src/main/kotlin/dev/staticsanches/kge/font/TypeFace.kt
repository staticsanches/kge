@file:Suppress("MemberVisibilityCanBePrivate")

package dev.staticsanches.kge.font

import dev.staticsanches.kge.font.FreeTypeFace.SizedFace
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.KGEInternalResource
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.closeIfFailed
import dev.staticsanches.kge.utils.invokeForAll
import dev.staticsanches.kge.utils.invokeForAllRemoving
import java.io.InputStream
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Represents a typeface (with a [faceIndex] on a loaded file) and does not have size information.
 *
 * Since the font data is shared to improve performance and memory usage, ONLY release this resource when you are done
 * manipulating text using this font face.
 */
class TypeFace private constructor(
    private val face: FreeTypeFace,
) : KGEResource {
    val faceIndex: Long by face::faceIndex
    val name: String by face::name

    private val atlases = ConcurrentHashMap<Int, GlyphAtlas>()

    internal fun glyphIndex(codepoint: Int): Int = face.glyphIndex(codepoint)

    internal fun atlas(size: Int): GlyphAtlas = atlases.computeIfAbsent(size) { GlyphAtlas(this, it) }

    @OptIn(ExperimentalContracts::class)
    internal inline fun <R> withSize(
        size: Int,
        block: (face: SizedFace) -> R,
    ): R {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }
        return face.withSize(size, block)
    }

    override fun close() =
        invokeForAll(
            RelatedResourcesCleaner(atlases.values)::invoke,
            face::close,
        ) { it() }

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
private value class RelatedResourcesCleaner(
    val resources: MutableCollection<out KGEInternalResource>,
) : KGECleanAction {
    override fun invoke() = resources.invokeForAllRemoving { it.close() }
}
