@file:OptIn(KGESensitiveAPI::class)

package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.renderer.gl.service.GLService
import dev.staticsanches.kge.resource.LeakReporterService
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.applyClosingIfFailed
import dev.staticsanches.kge.testsupport.engine.installGl
import dev.staticsanches.kge.testsupport.gl.RecordingGLService
import dev.staticsanches.kge.text.KGEFont
import dev.staticsanches.kge.text.axisValue
import dev.staticsanches.kge.text.fontPx
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield

private const val SIZE_PX = 16
private const val TAB_SIZE = 4

/** The concurrent callers a single-flight pin races against one key. */
private const val RACERS = 8

/** One 512x512 RGBA chart. */
private const val CHART_BYTES = 512 * 512 * Int.SIZE_BYTES

/** The carrier's upload scratch, the only buffer the GPU half allocates. */
private const val SCRATCH_NAME = "glyph coverage scratch"

/** The one chart a size's atlas opens. */
private fun chartName(sizePx: Int): String = "glyph atlas (${sizePx}px) #0"

/**
 * The configuration cache: equal keys share the atlas and the GPU carrier while
 * different face, size or axes do not, and an undrawn lease stays CPU-only.
 */
class TtfConfigurationCacheTest :
    FunSpec({
        test("two equal leases share one atlas and one carrier while remaining distinct leases") {
            withRecordedGpu { gl, allocations ->
                ResourceScope().use { scope ->
                    val face = KGETtfFontService.createResources(scope, robotoFontBytes()).defaultFace
                    val first = face.font(scope, SIZE_PX.fontPx)
                    val second = face.font(scope, SIZE_PX.fontPx)
                    (first === second) shouldBe false

                    val firstDecal = first.collectDecals("A").single().decal
                    allocations.charts.size shouldBe 1
                    gl.calls.count { it.name == "createTexture" } shouldBe 1
                    gl.clear()

                    val secondDecal = second.collectDecals("A").single().decal

                    secondDecal shouldBeSameInstanceAs firstDecal
                    allocations.charts.size shouldBe 1
                    gl.calls shouldBe emptyList()
                }
            }
        }

        test("leases at different sizes do not share an atlas or a carrier") {
            withRecordedGpu { gl, allocations ->
                ResourceScope().use { scope ->
                    val face = KGETtfFontService.createResources(scope, robotoFontBytes()).defaultFace
                    val small = face.font(scope, SIZE_PX.fontPx)
                    val large = face.font(scope, 32.fontPx)

                    small.collectDecals("A")
                    large.collectDecals("A")

                    allocations.charts.size shouldBe 2
                    gl.calls.count { it.name == "createTexture" } shouldBe 2
                    gl.calls.count { it.name == "texSubImage2D" } shouldBe 2
                }
            }
        }

        test("leases over different canonical axis sets do not share an atlas") {
            withRecordedGpu { gl, allocations ->
                ResourceScope().use { scope ->
                    val face = KGETtfFontService.createResources(scope, robotoFontBytes()).defaultFace
                    val regular = face.font(scope, SIZE_PX.fontPx, mapOf(KGEFont.Axis.Tag.Weight to 400.axisValue))
                    val bold = face.font(scope, SIZE_PX.fontPx, mapOf(KGEFont.Axis.Tag.Weight to 900.axisValue))
                    regular.axisCoordinates shouldNotBe bold.axisCoordinates

                    regular.collectDecals("A")
                    bold.collectDecals("A")

                    allocations.charts.size shouldBe 2
                    gl.calls.count { it.name == "createTexture" } shouldBe 2
                }
            }
        }

        test("leases over different faces do not share an atlas") {
            withRecordedGpu { gl, allocations ->
                ResourceScope().use { scope ->
                    val family = KGETtfFontService.createResources(scope, robotoFontBytes(), robotoItalicBytes())
                    val roman = family.faces[0].font(scope, SIZE_PX.fontPx)
                    val italic = family.faces[1].font(scope, SIZE_PX.fontPx)

                    roman.collectDecals("A")
                    italic.collectDecals("A")

                    allocations.charts.size shouldBe 2
                    gl.calls.count { it.name == "createTexture" } shouldBe 2
                }
            }
        }

        test("a lease that never draws creates no GPU object") {
            withRecordedGpu { gl, allocations ->
                ResourceScope().use { scope ->
                    KGETtfFontService.createResources(scope, robotoFontBytes()).defaultFace.font(scope, SIZE_PX.fontPx)

                    gl.calls shouldBe emptyList()
                    allocations.charts shouldBe emptyList()
                }
            }
        }

        test("two equal leases allocate exactly one chart for the same glyph") {
            withRecordedGpu { _, allocations ->
                ResourceScope().use { scope ->
                    val face = KGETtfFontService.createResources(scope, robotoFontBytes()).defaultFace
                    val first = face.font(scope, SIZE_PX.fontPx)
                    val second = face.font(scope, SIZE_PX.fontPx)

                    first.drawCpu("A")
                    allocations.charts.size shouldBe 1

                    second.drawCpu("A")

                    allocations.charts.size shouldBe 1
                }
            }
        }

        test("closing one lease leaves an equal live lease usable") {
            withRecordedGpu { gl, allocations ->
                ResourceScope().use { scope ->
                    val face = KGETtfFontService.createResources(scope, robotoFontBytes()).defaultFace
                    val first = face.font(scope, SIZE_PX.fontPx)
                    val second = face.font(scope, SIZE_PX.fontPx)
                    val decal = second.collectDecals("A").single().decal

                    first.close()

                    // the survivor holds the shared entry open: same carrier, no release
                    second.collectDecals("A").single().decal shouldBeSameInstanceAs decal
                    second.measureText("A", TAB_SIZE).x shouldBeGreaterThan 0
                    allocations.charts.single().cleaned shouldBe false
                    allocations.releases shouldBe emptyList()
                    gl.calls.count { it.name == "deleteTexture" } shouldBe 0
                }
            }
        }

        test("closing the last lease releases the carrier, then the atlas, then the native face") {
            withRecordedGpu { gl, allocations ->
                ResourceScope().use { scope ->
                    val face = KGETtfFontService.createResources(scope, robotoFontBytes()).defaultFace
                    val first = face.font(scope, SIZE_PX.fontPx)
                    val second = face.font(scope, SIZE_PX.fontPx)
                    first.collectDecals("A")
                    second.collectDecals("A")

                    first.close()

                    // a live sibling still holds the entry, so nothing is released yet
                    allocations.releases shouldBe emptyList()
                    gl.calls.count { it.name == "deleteTexture" } shouldBe 0

                    second.close()

                    // the GPU carrier (its texture, then its scratch), then the CPU atlas
                    gl.calls.count { it.name == "deleteTexture" } shouldBe 1
                    allocations.releases shouldBe listOf(SCRATCH_NAME, chartName(SIZE_PX))
                    // the entry release stops at the native face: the shared payload survives it
                    allocations.payloads.forEach { it.cleaned shouldBe false }
                }
            }
        }

        test("the released key rebuilds with a new native face and a new atlas") {
            withRecordedGpu { gl, allocations ->
                ResourceScope().use { scope ->
                    val face = KGETtfFontService.createResources(scope, robotoFontBytes()).defaultFace
                    val first = face.font(scope, SIZE_PX.fontPx)
                    first.collectDecals("A")

                    first.close()

                    allocations.charts.single().cleaned shouldBe true
                    gl.calls.count { it.name == "deleteTexture" } shouldBe 1

                    val second = face.font(scope, SIZE_PX.fontPx)
                    second.collectDecals("A")

                    // a rebuilt native face and atlas, not the dead entry's resources
                    allocations.charts.size shouldBe 2
                    gl.calls.count { it.name == "createTexture" } shouldBe 2
                }
            }
        }

        test("the family's close releases every live entry and invalidates its leases") {
            withRecordedGpu { gl, allocations ->
                ResourceScope().use { scope ->
                    val family = KGETtfFontService.createResources(scope, robotoFontBytes())
                    val face = family.defaultFace
                    val small = face.font(scope, SIZE_PX.fontPx)
                    val large = face.font(scope, 32.fontPx)
                    small.collectDecals("A")
                    large.collectDecals("A")
                    allocations.charts.size shouldBe 2

                    // one key is already a resource-free tombstone
                    small.close()
                    allocations.releaseCount(chartName(SIZE_PX)) shouldBe 1
                    gl.calls.count { it.name == "deleteTexture" } shouldBe 1

                    family.close()

                    // the live entry is released once, the tombstone is not re-released
                    allocations.charts.forEach { it.cleaned shouldBe true }
                    allocations.releaseCount(chartName(32)) shouldBe 1
                    gl.calls.count { it.name == "deleteTexture" } shouldBe 2
                    shouldThrow<IllegalStateException> { small.drawCpu("A") }
                    shouldThrow<IllegalStateException> { large.drawCpu("A") }
                }
            }
        }

        test("a failed lease registration gives the reference count back") {
            withRecordedGpu { _, allocations ->
                ResourceScope().use { scope ->
                    val face = KGETtfFontService.createResources(scope, robotoFontBytes()).defaultFace
                    val live = face.font(scope, SIZE_PX.fontPx)
                    live.drawCpu("A")

                    // a closed scope fails the registration after the count was taken
                    val closedScope = ResourceScope().apply { close() }
                    // adopts the live 16 px entry, then fails the registration
                    shouldThrow<IllegalStateException> { face.font(closedScope, SIZE_PX.fontPx) }
                    // builds the 32 px entry, then fails the registration
                    shouldThrow<IllegalStateException> { face.font(closedScope, 32.fontPx) }

                    live.close()

                    // the adopted entry is not stranded at the failed attempt's reference
                    allocations.charts.single().cleaned shouldBe true
                    allocations.releaseCount(chartName(SIZE_PX)) shouldBe 1

                    // the freshly built entry was released by its own failed registration,
                    // so this request rebuilds it and its close releases the new entry
                    val rebuilt = face.font(scope, 32.fontPx)
                    rebuilt.drawCpu("A")
                    rebuilt.close()

                    allocations.releaseCount(chartName(32)) shouldBe 1
                }
            }
        }

        test("lease close is idempotent and a scope close after it releases exactly once") {
            withRecordedGpu { gl, allocations ->
                val scope = ResourceScope()
                val family = KGETtfFontService.createResources(scope, robotoFontBytes())
                val lease = family.defaultFace.font(scope, SIZE_PX.fontPx)
                lease.collectDecals("A")

                lease.close()
                lease.close()

                allocations.releaseCount(chartName(SIZE_PX)) shouldBe 1
                gl.calls.count { it.name == "deleteTexture" } shouldBe 1

                scope.close()

                // the scope's close adds no second release of the same entry
                allocations.releaseCount(chartName(SIZE_PX)) shouldBe 1
                gl.calls.count { it.name == "deleteTexture" } shouldBe 1
            }
        }

        test("an unclosed lease is reported as a configured font") {
            val reports = mutableListOf<String>()
            withReportedLeaks(reports) {
                withRecordedGpu { _, _ ->
                    ResourceScope().use { scope ->
                        val face = KGETtfFontService.createResources(scope, robotoFontBytes()).defaultFace
                        face.font(scope, SIZE_PX.fontPx).onCollectionObserved()

                        reports.single() shouldContain "configured font"
                    }
                }
            }
        }

        test("a closed lease is not reported") {
            val reports = mutableListOf<String>()
            withReportedLeaks(reports) {
                withRecordedGpu { _, _ ->
                    ResourceScope().use { scope ->
                        val face = KGETtfFontService.createResources(scope, robotoFontBytes()).defaultFace
                        val lease = face.font(scope, SIZE_PX.fontPx)
                        lease.close()

                        lease.onCollectionObserved()

                        reports shouldBe emptyList()
                    }
                }
            }
        }

        test("axes outside the face's descriptors or range fail before any entry is published") {
            withRecordedGpu { _, allocations ->
                ResourceScope().use { scope ->
                    val face = KGETtfFontService.createResources(scope, robotoFontBytes()).defaultFace

                    shouldThrow<IllegalArgumentException> {
                        face.font(scope, SIZE_PX.fontPx, mapOf(KGEFont.Axis.Tag.Slant to 0.axisValue))
                    }
                    shouldThrow<IllegalArgumentException> {
                        face.font(scope, SIZE_PX.fontPx, mapOf(KGEFont.Axis.Tag.Weight to 901.axisValue))
                    }

                    // nothing was published: the next valid request builds the key once and shares it
                    face.font(scope, SIZE_PX.fontPx).drawCpu("A")
                    allocations.charts.size shouldBe 1

                    face.font(scope, SIZE_PX.fontPx).drawCpu("A")
                    allocations.charts.size shouldBe 1
                }
            }
        }

        test("a leaked lease does not decrement and the family's close releases the entry") {
            withRecordedGpu { gl, allocations ->
                val scope = ResourceScope()
                val family = KGETtfFontService.createResources(scope, robotoFontBytes())
                val lease = family.defaultFace.font(scope, SIZE_PX.fontPx)
                lease.collectDecals("A")

                lease.onCollectionObserved()

                // the report path never runs the clean action: the entry outlives the lease
                allocations.releases shouldBe emptyList()
                gl.calls.count { it.name == "deleteTexture" } shouldBe 0
                shouldThrow<IllegalStateException> { lease.drawCpu("A") }

                family.close()

                allocations.releaseCount(chartName(SIZE_PX)) shouldBe 1
                gl.calls.count { it.name == "deleteTexture" } shouldBe 1
            }
        }

        test("two equal leases construct the native face once") {
            var constructions = 0
            ResourceScope().use { scope ->
                val family =
                    createTtfFamily(scope, listOf(robotoFontBytes())) { payload, coordinates ->
                        constructions++
                        createNativeFace(payload, coordinates)
                    }
                val face = family.defaultFace

                face.font(scope, SIZE_PX.fontPx).measureText("A", TAB_SIZE)
                face.font(scope, SIZE_PX.fontPx).measureText("A", TAB_SIZE)

                constructions shouldBe 1
            }
        }

        test("N concurrent requests for one key construct once and every caller shares the entry") {
            var constructions = 0
            withRecordedGpu { _, allocations ->
                ResourceScope().use { scope ->
                    val family =
                        createTtfFamily(scope, listOf(robotoFontBytes())) { payload, coordinates ->
                            constructions++
                            // the first caller holds the cache lock through this suspension
                            yield()
                            createNativeFace(payload, coordinates)
                        }
                    val face = family.defaultFace

                    val leases =
                        coroutineScope {
                            (1..RACERS).map { async { face.font(scope, SIZE_PX.fontPx) } }.awaitAll()
                        }

                    constructions shouldBe 1
                    // every caller holds a lease over the one entry: they share one atlas
                    leases.forEach { it.drawCpu("A") }
                    allocations.charts.size shouldBe 1
                }
            }
        }

        test("N concurrent failing requests each observe the failure and leave no entry behind") {
            val failure = IllegalStateException("native construction failed")
            var constructions = 0
            var failing = true
            withRecordedGpu { _, allocations ->
                ResourceScope().use { scope ->
                    val family =
                        createTtfFamily(scope, listOf(robotoFontBytes())) { payload, coordinates ->
                            constructions++
                            // the first caller holds the cache lock through this suspension
                            yield()
                            if (failing) throw failure
                            createNativeFace(payload, coordinates)
                        }
                    val face = family.defaultFace

                    val observed =
                        coroutineScope {
                            (1..RACERS)
                                .map { async { runCatching { face.font(scope, SIZE_PX.fontPx) }.exceptionOrNull() } }
                                .awaitAll()
                        }

                    // every concurrent caller observes the injected failure
                    observed.size shouldBe RACERS
                    observed.forEach { it shouldBeSameInstanceAs failure }
                    val failedAttempts = constructions
                    failedAttempts shouldBeGreaterThan 0

                    // no entry was published: a healthy later request rebuilds and returns a usable lease
                    failing = false
                    val recovered = face.font(scope, SIZE_PX.fontPx)
                    constructions shouldBeGreaterThan failedAttempts
                    recovered.measureText("A", TAB_SIZE).x shouldBeGreaterThan 0
                    recovered.drawCpu("A")
                    allocations.charts.size shouldBe 1
                }
            }
        }
    })

/** The engine buffers allocated while its override is installed, with their byte sizes. */
private class RecordedAllocations {
    private val entries = mutableListOf<Pair<Int, TrackedBuffer>>()
    private val released = mutableListOf<String>()

    /** Every tracked buffer's release, in close order. */
    val releases: List<String> get() = released.toList()

    val service: BufferService =
        object : BufferService {
            override fun allocate(
                sizeInBytes: Int,
                name: String?,
            ): ResourceWrapper<ByteBuffer> =
                TrackedBuffer(BufferService.original.allocate(sizeInBytes, name), name.orEmpty(), released)
                    .also { entries += sizeInBytes to it }
        }

    /** One entry per 512x512 atlas chart. */
    val charts: List<TrackedBuffer>
        get() = entries.filter { (size, _) -> size == CHART_BYTES }.map { (_, wrapper) -> wrapper }

    /** One entry per font payload, which only the family's close releases. */
    val payloads: List<TrackedBuffer>
        get() = entries.filter { (_, wrapper) -> wrapper.name == "font" }.map { (_, wrapper) -> wrapper }

    /** How many times the buffer named [name] was released. */
    fun releaseCount(name: String): Int = released.count { it == name }
}

/** One engine buffer whose every release is recorded before it delegates. */
private class TrackedBuffer(
    private val delegate: ResourceWrapper<ByteBuffer>,
    val name: String,
    private val released: MutableList<String>,
) : ResourceWrapper<ByteBuffer> by delegate {
    override fun close() {
        released += name
        delegate.close()
    }
}

/** Runs [block] over the recording GL and buffer services, restoring both engine defaults after. */
private suspend fun withRecordedGpu(block: suspend (RecordingGLService, RecordedAllocations) -> Unit) {
    val gl = installGl()
    val allocations = RecordedAllocations()
    BufferService.override(allocations.service)
    try {
        block(gl, allocations)
    } finally {
        // kge-core resets overrides between its own tests only, so this module
        // restores the engine defaults itself.
        BufferService.override(BufferService.original)
        GLService.override(GLService.original)
    }
}

/** Runs [block] over a recording leak reporter, restoring the engine default after. */
private inline fun withReportedLeaks(
    reports: MutableList<String>,
    block: () -> Unit,
) {
    LeakReporterService.override(
        object : LeakReporterService {
            override fun report(representation: String) {
                reports += representation
            }
        },
    )
    try {
        block()
    } finally {
        // kge-core resets overrides between its own tests only, so this module
        // restores the engine default itself.
        LeakReporterService.override(LeakReporterService.original)
    }
}

/** Draws "A" through the decal path, building the shared configuration's atlas and carrier. */
private fun KGEFont.collectDecals(text: String): List<DecalInstance> {
    val collected = mutableListOf<DecalInstance>()
    drawTextDecal(
        Float2D(2f, 3f),
        text,
        Colors.WHITE,
        Float2D(1f, 1f),
        TAB_SIZE,
        Int2D(30, 24),
        Decal.Mode.NORMAL,
        Decal.Structure.FAN,
        collected::add,
    )
    return collected
}

/** Draws [text] into a throwaway surface, building the shared configuration's CPU atlas. */
private fun KGEFont.drawCpu(text: String) {
    SpriteService
        .create(48, 24, Pixmap.SampleMode.NORMAL, "configuration cache draw")
        .applyClosingIfFailed { clear(Colors.TRANSPARENT) }
        .use { target -> drawText(target, 0, 0, text, Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal) }
}
