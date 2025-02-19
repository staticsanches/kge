package dev.staticsanches.kge.font

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.KGEInternalResource
import dev.staticsanches.kge.resource.OffHeapByteBuffer
import dev.staticsanches.kge.resource.PointerResource
import dev.staticsanches.kge.resource.andThen
import dev.staticsanches.kge.resource.invokeIfFailed
import dev.staticsanches.kge.utils.pointerRepresentation
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.freetype.FT_Face
import org.lwjgl.util.freetype.FreeType
import java.io.InputStream
import java.lang.ref.WeakReference
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal class FreeTypeFace private constructor(
    face: FT_Face,
    handleDeleter: (Long) -> KGECleanAction,
) : KGEInternalResource {
    val faceIndex: Long = face.face_index()
    val name: String = "${face.family_nameString()} (${face.style_nameString()})"

    private val resource = PointerResource("FT_Face", "($name)", { face.address() }, handleDeleter)
    private val currentSize = AtomicInteger()

    private val glyphIndexByCodepoint = ConcurrentHashMap<Int, Int>(face.num_glyphs().toInt())

    init {
        MemoryStack.stackPush().use { memoryStack ->
            val indexBuffer = memoryStack.mallocInt(1)
            var codepoint = FreeType.FT_Get_First_Char(face, indexBuffer)
            var index = indexBuffer[0]
            while (index != 0) {
                glyphIndexByCodepoint[codepoint.toInt()] = index
                codepoint = FreeType.FT_Get_Next_Char(face, codepoint, indexBuffer)
                index = indexBuffer[0]
            }
        }
    }

    fun glyphIndex(codepoint: Int): Int = glyphIndexByCodepoint.computeIfAbsent(codepoint, ::findGlyphIndex)

    @OptIn(ExperimentalContracts::class)
    inline fun <R> withSize(
        size: Int,
        block: (face: SizedFace) -> R,
    ): R {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }
        synchronized(resource) {
            check(size > 0) { "[$this] Size must be greater than 0" }
            val mustSetSize = currentSize.compareAndSet(0, size)
            if (!mustSetSize) {
                check(currentSize.compareAndSet(size, size)) { "[$this] Unable to change size to $size" }
            }
            try {
                val face = SizedFace(size)
                if (mustSetSize) {
                    FreeType.FT_Set_Pixel_Sizes(face, 0, size).handleFTError { errorCode, errorString ->
                        "[$this] Unable to set pixel size to $size (error code $errorCode): $errorString"
                    }
                }
                return block(face)
            } finally {
                if (mustSetSize) {
                    currentSize.set(0)
                }
            }
        }
    }

    private fun findGlyphIndex(codepoint: Int): Int =
        synchronized(resource) { FreeType.FT_Get_Char_Index(FT_Face.create(resource.handle), codepoint.toLong()) }

    @KGESensitiveAPI
    override fun close() = resource.close()

    override fun toString(): String = resource.toString()

    inner class SizedFace(
        val size: Int,
    ) : FT_Face(resource.handle, null) {
        override fun toString(): String = "$name (${size}px)"
    }

    companion object {
        fun load(
            fileName: String,
            faceIndex: Long,
        ): FreeTypeFace = newFace { library, pointer -> FreeType.FT_New_Face(library, fileName, faceIndex, pointer) }

        fun load(
            url: URL,
            faceIndex: Long,
        ): FreeTypeFace = load(url::openStream, faceIndex)

        fun load(
            isProvider: () -> InputStream,
            faceIndex: Long,
        ): FreeTypeFace =
            OffHeapByteBuffer(isProvider).invokeIfFailed { (buffer, cleanAction) ->
                newFace(cleanAction) { library, pointer ->
                    FreeType.FT_New_Memory_Face(library, buffer, faceIndex, pointer)
                }
            }

        private fun newFace(
            extraCleanAction: KGECleanAction? = null,
            newFaceFunc: (Long, PointerBuffer) -> FTError,
        ): FreeTypeFace {
            val ftLibrary = FTLibrary.instance
            return ftLibrary
                .withLock { library ->
                    FreeTypeFace(
                        MemoryStack.stackPush().use { memoryStack ->
                            val pointer = memoryStack.mallocPointer(1)
                            newFaceFunc(library, pointer).handleFTError { errorCode, errorString ->
                                "Creation of FT_Face failed with error code $errorCode: $errorString"
                            }
                            FT_Face.create(pointer[0])
                        },
                    ) { face -> ftLibrary.incrementRefCount(face) andThen extraCleanAction }
                }
        }
    }
}

private class FTLibrary {
    private val resource = PointerResource("FT_Library", ::initFreeType, ::DoneFreeTypeAction)
    private val refCount = AtomicInteger(0)

    inline fun <T> withLock(action: (handle: Long) -> T): T =
        Lock.withLock {
            action(resource.handle)
        }

    fun incrementRefCount(face: Long): KGECleanAction {
        refCount.incrementAndGet()
        return {
            Lock.withLock {
                val refCount = refCount.decrementAndGet()
                var error: Throwable? = null
                try {
                    FreeType.nFT_Done_Face(face).handleFTError { errorCode, errorString ->
                        "Disposal of ${face.pointerRepresentation("FT_Face")} failed" +
                            " with error code $errorCode: $errorString"
                    }
                } catch (t: Throwable) {
                    error = t
                } finally {
                    if (refCount == 0) {
                        if (current?.get() == this) {
                            current = null
                        }
                        try {
                            resource.close()
                        } catch (t: Throwable) {
                            if (error == null) {
                                error = t
                            } else {
                                error.addSuppressed(t)
                            }
                        }
                    }
                }
                if (error != null) {
                    throw error
                }
            }
        }
    }

    override fun toString(): String = resource.toString()

    companion object Lock : ReentrantLock() {
        @Suppress("unused")
        private fun readResolve(): Any = Lock

        @Volatile
        private var current: WeakReference<FTLibrary>? = null

        val instance: FTLibrary
            get() {
                val l1 = current?.get()
                if (l1 != null) {
                    return l1
                }
                withLock {
                    var l2 = current?.get()
                    if (l2 != null) {
                        return l2
                    }
                    l2 = FTLibrary()
                    current = WeakReference(l2)
                    return l2
                }
            }

        private fun initFreeType(): Long =
            MemoryStack.stackPush().use { memoryStack ->
                val pointer = memoryStack.mallocPointer(1)
                FreeType.FT_Init_FreeType(pointer).handleFTError { errorCode, errorString ->
                    "Initialization of FT_Library failed with error code $errorCode: $errorString"
                }
                return pointer[0]
            }
    }
}

@JvmInline
private value class DoneFreeTypeAction(
    val handle: Long,
) : KGECleanAction {
    override fun invoke() =
        FreeType.FT_Done_FreeType(handle).handleFTError { errorCode, errorString ->
            "Disposal of ${handle.pointerRepresentation("FT_Library")} failed" +
                " with error code $errorCode: $errorString"
        }
}
