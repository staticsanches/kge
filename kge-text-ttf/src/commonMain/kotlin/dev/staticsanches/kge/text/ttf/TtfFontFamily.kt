package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.letClosingIfFailed
import dev.staticsanches.kge.text.KGEFont
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/** Reads every payload before publishing one family; a failure releases all of them. */
internal fun createTtfFamily(
    scope: ResourceScope,
    payloads: List<ByteArray>,
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

        val family = TtfFontFamily(familyName, loaded)
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
) : KGEFont.Family {
    private var closed = false
    private val faceList: List<TtfFace> =
        persistentListOf(*loaded.map { (payload, sfnt) -> TtfFace(this, payload, sfnt) }.toTypedArray())
    private val payloads: List<TtfPayload> = loaded.map { it.first }

    override val faces: List<KGEFont.Face>
        get() = checkOpen().let { faceList }

    override val defaultFace: KGEFont.Face
        get() = checkOpen().let { faceList.first() }

    fun checkOpen() {
        check(!closed) { "The TrueType font family is closed" }
    }

    override fun close() {
        if (closed) return
        closed = true
        payloads.closeAll()
    }
}

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
        val coordinates = coordinatesInFvarOrder(descriptors, canonical)

        return TtfFont(owner, this, size, canonical, wrapNativeFace(createNativeFace(payload, coordinates)))
            .letClosingIfFailed { scope.register(TtfFontKey(), it) }
    }
}

/** A configured font: one face, size and coordinate set over the family's payload. */
private class TtfFont(
    private val owner: TtfFontFamily,
    private val configuredFace: KGEFont.Face,
    private val configuredSize: KGEFont.Size,
    override val axisCoordinates: Map<KGEFont.Axis.Tag, KGEFont.Axis.Value>,
    private val native: ResourceWrapper<NativeFace>,
) : KGEFont {
    private var closed = false
    private var atlas: GlyphAtlas? = null
    private var gpuAtlas: GlyphAtlasGpu? = null

    override val face: KGEFont.Face
        get() = checkOpen().let { configuredFace }

    override val size: KGEFont.Size
        get() = configuredSize

    private fun checkOpen() {
        check(!closed) { "The configured font is closed" }
        owner.checkOpen()
    }

    private fun atlasFor(sizePx: Int): GlyphAtlas {
        checkOpen()
        atlas?.let { return it }
        return GlyphAtlas(sizePx) { native.resource.rasterize(it, sizePx) }.also { atlas = it }
    }

    private fun gpuAtlasFor(sizePx: Int): GlyphAtlasGpu {
        checkOpen()
        gpuAtlas?.let { return it }
        return GlyphAtlasGpu(atlasFor(sizePx)).also { gpuAtlas = it }
    }

    override fun measureText(
        text: String,
        tabSizeInSpaces: Int,
    ): Int2D {
        checkOpen()
        check(tabSizeInSpaces > 0) { "Invalid tab size: $tabSizeInSpaces" }
        if (text.isEmpty()) return Int2D(0, 0)

        return measureText(native.resource, text, configuredSize.px, tabSizeInSpaces)
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
            native.resource,
            ::atlasFor,
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
            native.resource,
            ::atlasFor,
            ::gpuAtlasFor,
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

    override fun close() {
        if (closed) return
        closed = true
        val toClose = mutableListOf<KGEResource>()
        gpuAtlas?.let { toClose += it }
        atlas?.let { toClose += it }
        toClose += native
        gpuAtlas = null
        atlas = null
        toClose.closeAll()
    }
}

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
