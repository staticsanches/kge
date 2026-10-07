package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.applyClosingIfFailed
import dev.staticsanches.kge.resource.letClosingIfFailed
import dev.staticsanches.kge.resource.onCollectionObserved
import dev.staticsanches.kge.text.KGEFont
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Reads every payload before publishing one family; a failure releases all of them.
 * [createFace] is the native-face construction seam, defaulted to the platform factory.
 */
internal fun createTtfFamily(
    scope: ResourceScope,
    payloads: List<ByteArray>,
    createFace: suspend (TtfPayload, AxisCoordinates) -> NativeFace = { payload, coordinates ->
        createNativeFace(payload, coordinates)
    },
): KGEFont.Family {
    require(payloads.isNotEmpty()) { "at least one font payload is required" }
    val owned = ArrayList<TtfPayload>(payloads.size)
    try {
        val loaded =
            payloads.map { bytes ->
                val payload = TtfPayload(bytes)
                owned += payload
                payload to readSfntFace(bytes)
            }
        val familyName = loaded.first().second.familyName
        require(loaded.all { it.second.familyName == familyName }) {
            "the payloads are not one family: ${loaded.map { it.second.familyName }}"
        }
        val subfamilies = loaded.map { it.second.subfamilyName }
        require(subfamilies.size == subfamilies.toSet().size) {
            "two payloads report the same subfamily name: $subfamilies"
        }

        val family = TtfFontFamily(familyName, loaded, createFace)
        owned.clear()
        return family.letClosingIfFailed { scope.register(TtfFontFamilyKey(), it) }
    } catch (failure: Throwable) {
        owned.closeAll()
        throw failure
    }
}

/** The loaded family: one payload per face, owned here and shared by every lease. */
private class TtfFontFamily(
    override val name: String,
    loaded: List<Pair<TtfPayload, SfntFace>>,
    /** The native-face factory tests inject to count or fail one construction. */
    val createFace: suspend (TtfPayload, AxisCoordinates) -> NativeFace,
) : KGEFont.Family {
    private var closed = false
    private val faceList: List<TtfFace> =
        persistentListOf(*loaded.map { (payload, sfnt) -> TtfFace(this, payload, sfnt) }.toTypedArray())
    private val payloads: List<TtfPayload> = loaded.map { it.first }
    private val configurations = mutableMapOf<TtfConfigurationKey, TtfConfiguration>()
    private val configurationsLock = Mutex()

    override val faces: List<KGEFont.Face>
        get() = checkOpen().let { faceList }

    override val defaultFace: KGEFont.Face
        get() = checkOpen().let { faceList.first() }

    fun checkOpen() {
        check(!closed) { "The TrueType font family is closed" }
    }

    /** The configuration for [key], [build] running once under the cache lock; equal keys share it. */
    suspend fun configuration(
        key: TtfConfigurationKey,
        build: suspend () -> TtfConfiguration,
    ): TtfConfiguration =
        configurationsLock.withLock {
            checkOpen()
            configurations[key]?.let { existing ->
                if (existing.tryAcquire()) return@withLock existing
            }
            build().applyClosingIfFailed { configurations[key] = this }
        }

    override fun close() {
        if (closed) return
        closed = true
        val toClose = mutableListOf<KGEResource>()
        toClose += configurations.values
        configurations.clear()
        toClose += payloads
        toClose.closeAll()
    }
}

/** One shared native configuration: the native face and the lazily built atlases over it. */
@OptIn(ExperimentalAtomicApi::class)
private class TtfConfiguration(
    private val sizePx: Int,
    private val native: ResourceWrapper<NativeFace>,
) : KGEResource {
    private var atlas: GlyphAtlas? = null
    private var gpuAtlas: GlyphAtlasGpu? = null

    /** Lease references; the entry is born with the first and dies at the 1 -> 0 CAS. */
    private val references = AtomicInt(1)

    /** The native face every lease over the key rasterizes through. */
    val face: NativeFace
        get() = native.resource

    /** The size's CPU atlas, built on first use. */
    fun cpuAtlas(): GlyphAtlas =
        atlas ?: GlyphAtlas(sizePx) { glyphId -> face.rasterize(glyphId, sizePx) }.also { atlas = it }

    /** The GPU carrier over [cpuAtlas], built only on the first decal. */
    fun gpuCarrier(): GlyphAtlasGpu = gpuAtlas ?: GlyphAtlasGpu(cpuAtlas()).also { gpuAtlas = it }

    /** Takes one reference for a new lease; false once the entry is already released. */
    fun tryAcquire(): Boolean {
        while (true) {
            val current = references.load()
            if (current == 0) return false
            if (references.compareAndSet(current, current + 1)) return true
        }
    }

    /** Drops one lease's reference; the 1 -> 0 CAS releases the entry in place. */
    fun release() {
        while (true) {
            val current = references.load()
            if (current == 0) return
            if (references.compareAndSet(current, if (current == 1) 0 else current - 1)) {
                if (current == 1) releaseAll()
                return
            }
        }
    }

    /** Claims every remaining reference, so the family's close releases a live entry once. */
    override fun close() {
        while (true) {
            val current = references.load()
            if (current == 0) return
            if (references.compareAndSet(current, 0)) {
                releaseAll()
                return
            }
        }
    }

    /** Releases the carrier, then the atlas, then the native face, each at most once. */
    private fun releaseAll() {
        val toClose = mutableListOf<KGEResource>()
        gpuAtlas?.let { toClose += it }
        atlas?.let { toClose += it }
        toClose += native
        gpuAtlas = null
        atlas = null
        toClose.closeAll()
    }
}

/** The cache key: the face, the size and the complete canonical axis map. */
private data class TtfConfigurationKey(
    val face: TtfFace,
    val size: KGEFont.Size,
    val axes: Map<KGEFont.Axis.Tag, KGEFont.Axis.Value>,
)

/** One loaded design; the family owns its payload and callers never close it. */
@OptIn(KGESensitiveAPI::class)
private class TtfFace(
    private val owner: TtfFontFamily,
    private val payload: TtfPayload,
    sfnt: SfntFace,
) : KGEFont.Face {
    private val descriptors: List<KGEFont.Axis> = sfnt.axes

    override val name: String = sfnt.subfamilyName

    override val monospaced: Boolean = sfnt.monospaced

    override val axes: Map<KGEFont.Axis.Tag, KGEFont.Axis> =
        persistentMapOf(*descriptors.map { it.tag to it }.toTypedArray())

    override val family: KGEFont.Family
        get() = owner.checkOpen().let { owner }

    override suspend fun font(
        scope: ResourceScope,
        size: KGEFont.Size,
        axes: Map<KGEFont.Axis.Tag, KGEFont.Axis.Value>,
    ): KGEFont {
        owner.checkOpen()
        val canonical = canonicalAxisCoordinates(descriptors, axes)
        val configuration =
            owner.configuration(TtfConfigurationKey(this, size, canonical)) {
                val coordinates = coordinatesInFvarOrder(descriptors, canonical)
                TtfConfiguration(size.px, wrapNativeFace(owner.createFace(payload, coordinates)))
            }

        return TtfFont(owner, this, size, canonical, wrapConfiguredFont(configuration))
            .letClosingIfFailed { scope.register(TtfFontKey(), it) }
    }
}

/** Creates the lease's tracked identity; a wrapper that cannot register drops the reference it took. */
@OptIn(KGESensitiveAPI::class)
private fun wrapConfiguredFont(configuration: TtfConfiguration): ResourceWrapper<TtfConfiguration> =
    try {
        ResourceWrapper("configured font", configuration, KGECleanAction { configuration.release() })
    } catch (failure: Throwable) {
        try {
            configuration.release()
        } catch (releaseFailure: Throwable) {
            failure.addSuppressed(releaseFailure)
        }
        throw failure
    }

/** A configured font: one face, size and coordinate set over the family's shared configuration. */
private class TtfFont(
    private val owner: TtfFontFamily,
    private val configuredFace: KGEFont.Face,
    private val configuredSize: KGEFont.Size,
    override val axisCoordinates: Map<KGEFont.Axis.Tag, KGEFont.Axis.Value>,
    private val tracked: ResourceWrapper<TtfConfiguration>,
) : KGEFont {
    override val face: KGEFont.Face
        get() = checkOpen().let { configuredFace }

    override val size: KGEFont.Size
        get() = configuredSize

    private fun checkOpen() {
        check(!tracked.cleaned) { "The configured font is closed" }
        owner.checkOpen()
    }

    override fun measureText(
        text: String,
        tabSizeInSpaces: Int,
    ): Int2D {
        checkOpen()
        check(tabSizeInSpaces > 0) { "Invalid tab size: $tabSizeInSpaces" }
        if (text.isEmpty()) return Int2D(0, 0)

        return measureText(tracked.resource.face, text, configuredSize.px, tabSizeInSpaces)
    }

    override fun drawText(
        target: Pixmap.Mutable,
        x: Int,
        y: Int,
        text: String,
        color: Pixel,
        scale: Int,
        tabSizeInSpaces: Int,
        mode: Pixel.Mode,
    ) {
        checkOpen()
        drawText(
            tracked.resource.face,
            { tracked.resource.cpuAtlas() },
            target,
            x,
            y,
            text,
            configuredSize.px,
            color,
            scale,
            tabSizeInSpaces,
            mode,
        )
    }

    override fun drawTextDecal(
        position: Float2D,
        text: String,
        color: Pixel,
        scale: Float2D,
        tabSizeInSpaces: Int,
        screenSize: Int2D,
        decalMode: Decal.Mode,
        decalStructure: Decal.Structure,
        decalInstanceCollector: (DecalInstance) -> Unit,
    ) {
        checkOpen()
        drawStringDecalText(
            tracked.resource.face,
            { tracked.resource.cpuAtlas() },
            { tracked.resource.gpuCarrier() },
            position,
            text,
            configuredSize.px,
            color,
            scale,
            tabSizeInSpaces,
            screenSize,
            decalMode,
            decalStructure,
            decalInstanceCollector,
        )
    }

    /** Closes this lease and drops its reference; the last one releases the shared entry. */
    override fun close() {
        tracked.close()
    }

    /** Fires the platform collection trigger on this lease's tracked identity. */
    @OptIn(KGESensitiveAPI::class)
    fun onCollected() = tracked.onCollectionObserved()
}

/** Deterministic test seam: fires the collection path on [this] lease, as the platform would. */
internal fun KGEFont.onCollectionObserved() = (this as TtfFont).onCollected()

/**
 * The request with every omitted axis filled by its default, keyed in ascending
 * tag order; a tag or a value the face does not declare is rejected.
 */
internal fun canonicalAxisCoordinates(
    axes: List<KGEFont.Axis>,
    requested: Map<KGEFont.Axis.Tag, KGEFont.Axis.Value>,
): Map<KGEFont.Axis.Tag, KGEFont.Axis.Value> {
    val byTag = axes.associateBy { it.tag }
    requested.forEach { (tag, value) ->
        val axis = byTag[tag] ?: throw IllegalArgumentException("the face has no ${tag.raw} axis")
        require(value in axis.min..axis.max) {
            "the ${tag.raw} value $value is outside ${axis.min}..${axis.max}"
        }
    }

    val ordered = byTag.keys.sortedBy { it.raw }
    return persistentMapOf(
        *ordered.map { tag -> tag to (requested[tag] ?: byTag.getValue(tag).default) }.toTypedArray(),
    )
}

/** The canonical map in `fvar` record order, which the native seam's parallel arrays require. */
private fun coordinatesInFvarOrder(
    axes: List<KGEFont.Axis>,
    canonical: Map<KGEFont.Axis.Tag, KGEFont.Axis.Value>,
): AxisCoordinates {
    if (axes.isEmpty()) return AxisCoordinates.Empty

    return AxisCoordinates(
        tags = IntArray(axes.size) { axes[it].tag.packed },
        values = IntArray(axes.size) { canonical.getValue(axes[it].tag).raw },
    )
}

/** The axis tag's four ASCII bytes, big-endian: the engine's own tag encoding. */
private val KGEFont.Axis.Tag.packed: Int
    get() = (raw[0].code shl 24) or (raw[1].code shl 16) or (raw[2].code shl 8) or raw[3].code

private class TtfFontFamilyKey : ResourceScope.Key<TtfFontFamily>

private class TtfFontKey : ResourceScope.Key<TtfFont>
