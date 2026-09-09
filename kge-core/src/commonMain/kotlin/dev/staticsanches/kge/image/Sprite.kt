package dev.staticsanches.kge.image

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.buffer.fillInts
import dev.staticsanches.kge.buffer.formatBytes
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.letClosingIfFailed
import dev.staticsanches.kge.resource.onCollectionObserved

/**
 * The concrete 2D surface: pixel `(x, y)` is the little-endian RGBA int at
 * byte offset `(y * width + x) * 4`. The sprite owns its
 * [ResourceWrapper]: close releases the native memory and every access fails
 * fast afterwards — including the out-of-bounds paths, which never reach the
 * storage. Created content is unspecified until written; the wrapper contract
 * owns the lifetime, so an unclosed creation is reported on collection.
 *
 * The constructor is the resource seam; creation and duplication go through
 * [SpriteService]. A constructor rejection (dimensions, capacity)
 * means ownership never transferred: the caller keeps the wrapper and must
 * close it — [letClosingIfFailed] is the guard for allocate-then-construct
 * call sites.
 */
class Sprite(
    override val width: Int,
    override val height: Int,
    private val buffer: ResourceWrapper<ByteBuffer>,
    override var sampleMode: Pixmap.SampleMode = Pixmap.SampleMode.NORMAL,
    /** The surface's diagnostic label, shown by [toString] and the resource messages. */
    val name: String? = null,
) : MutablePixmap,
    KGEResource by buffer {
    init {
        require(width > 0 && height > 0) { "width and height must be positive: ${width}x$height" }
        require(buffer.resource.capacity() == width * height * Int.SIZE_BYTES) {
            "buffer capacity must be exactly ${formatBytes(width * height * Int.SIZE_BYTES)}"
        }
    }

    /** The out-of-bounds branches return without touching the storage, so the fail-fast check is first. */
    override fun get(
        x: Int,
        y: Int,
    ): Pixel {
        checkNotReleased()
        return super.get(x, y)
    }

    override fun set(
        x: Int,
        y: Int,
        pixel: Pixel,
    ): Boolean {
        checkNotReleased()
        return super.set(x, y, pixel)
    }

    private fun checkNotReleased() {
        if (buffer.cleaned) {
            throw IllegalStateException("the sprite has been released and can not be used")
        }
    }

    override fun uncheckedGet(
        x: Int,
        y: Int,
    ): Pixel = Pixel.fromNativeRGBA(buffer.resource.getInt((y * width + x) * Int.SIZE_BYTES))

    override fun uncheckedSet(
        x: Int,
        y: Int,
        pixel: Pixel,
    ) {
        buffer.resource.putInt((y * width + x) * Int.SIZE_BYTES, pixel.nativeRGBA)
    }

    /** Hot path: fills the whole buffer without per-pixel checks. */
    override fun clear(pixel: Pixel) {
        buffer.resource.fillInts(0, width * height, pixel.nativeRGBA)
    }

    /**
     * The raw storage, exposed for the raster fast paths and the future GPU
     * upload. The caller owns the bytes: writing outside the [Pixmap]
     * contract (layout, bounds, sample mode) is the caller's responsibility.
     * The fail-fast after close is preserved — this returns
     * `buffer.resource`, whose getter throws once released.
     */
    @KGESensitiveAPI
    val byteBuffer: ByteBuffer
        get() = buffer.resource

    /** Deterministic test seam: fires the platform collection trigger. */
    internal fun onCollectionObserved() = buffer.onCollectionObserved()

    override fun toString(): String {
        val tag = "${width}x$height, $sampleMode"
        return name?.let { "Sprite($tag, \"$it\")" } ?: "Sprite($tag)"
    }

    /**
     * The sprite-read policy for a blit: which axes of the source are read in
     * reverse. [NONE] blits top-left to top-left; each flip mirrors the source
     * inside the same destination footprint.
     */
    enum class Flip { NONE, HORIZONTAL, VERTICAL, BOTH }
}
