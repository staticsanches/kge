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
import org.lwjgl.system.Configuration
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.freetype.FT_Face
import org.lwjgl.util.freetype.FreeType
import java.io.InputStream
import java.lang.ref.WeakReference
import java.net.URL
import java.util.Collections
import java.util.SortedMap
import java.util.TreeMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class FreeTypeFace private constructor(
    face: FT_Face,
    handleDeleter: (Long) -> KGECleanAction,
) : KGEInternalResource {
    val faceIndex: Long = face.face_index()
    val name: String = "${face.family_nameString()} (${face.style_nameString()})"

    val glyphIndexByCodePoint: SortedMap<Int, Int>

    private val resource = PointerResource("FT_Face", "($name)", { face.address() }, handleDeleter)
    private val currentSize = AtomicInteger()

    inline fun <R> withSize(
        size: Int,
        block: (face: FT_Face) -> R,
    ): R =
        synchronized(resource) {
            check(size > 0) { "Size must be greater than 0" }
            val mustSetSize = currentSize.compareAndSet(0, size)
            if (!mustSetSize) {
                check(currentSize.compareAndSet(size, size)) { "Unable to change size to $size" }
            }
            try {
                val face = FT_Face.create(resource.handle)
                if (mustSetSize) {
                    FreeType.FT_Set_Pixel_Sizes(face, 0, size).handleFTError { errorCode, errorString ->
                        "[$this]: Unable to set pixel size to $size (error code $errorCode): $errorString"
                    }
                }
                block(face)
            } finally {
                if (mustSetSize) {
                    currentSize.set(0)
                }
            }
        }

    @KGESensitiveAPI
    override fun close() = resource.close()

    override fun toString(): String = resource.toString()

    init {
        MemoryStack.stackPush().use { memoryStack ->
            val glyphIndexBuffer = memoryStack.mallocInt(1)
            var codePoint = FreeType.FT_Get_First_Char(face, glyphIndexBuffer)
            var glyphIndex = glyphIndexBuffer[0]
            val glyphIndexByCodePoint = TreeMap<Int, Int>()
            while (glyphIndex != 0) {
                glyphIndexByCodePoint[codePoint.toInt()] = glyphIndex
                codePoint = FreeType.FT_Get_Next_Char(face, codePoint, glyphIndexBuffer)
                glyphIndex = glyphIndexBuffer[0]
            }
            this.glyphIndexByCodePoint = Collections.unmodifiableSortedMap(glyphIndexByCodePoint)
        }
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

        init {
            Configuration.HARFBUZZ_LIBRARY_NAME.set(FreeType.getLibrary())
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
