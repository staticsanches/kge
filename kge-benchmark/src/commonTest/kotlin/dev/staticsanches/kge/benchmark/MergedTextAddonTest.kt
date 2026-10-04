@file:OptIn(KGESensitiveAPI::class)

package dev.staticsanches.kge.benchmark

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.Engine
import dev.staticsanches.kge.engine.WindowConfig
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.Renderer
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.renderer.decal.service.DrawPartialDecalService
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.testsupport.engine.RecordingDriver
import dev.staticsanches.kge.testsupport.engine.installDriver
import dev.staticsanches.kge.testsupport.engine.installGl
import dev.staticsanches.kge.text.KGECoreFontService
import dev.staticsanches.kge.text.KGEFont
import dev.staticsanches.kge.text.fontPx
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration

private const val MERGED_TEST_WIDTH = 240
private const val MERGED_TEST_HEIGHT = 48

/** Two triangles per glyph; [SCENE_TEXT] has no newline or tab, so every character is a glyph. */
private val MERGED_TEST_VERTS = SCENE_TEXT.length * 6

/** The scene's merged runs at 240x48: three [SCENE_TEXT] rows of three columns. */
private const val MERGED_TEST_RUNS = 9

/** Queues one merged run per frame and snapshots what the decorator enqueued. */
private class MergedProbeEngine :
    Engine(WindowConfig(screenWidth = MERGED_TEST_WIDTH, screenHeight = MERGED_TEST_HEIGHT)),
    TextSceneTarget {
    private lateinit var merged: MergedTextAddon
    val queued = mutableListOf<Pair<Decal.Structure, Int>>()
    private var frames = 0

    override suspend fun onUserCreate(): Boolean {
        decalStructure = Decal.Structure.LIST
        merged = MergedTextAddon(this)
        return true
    }

    override suspend fun onUserUpdate(elapsed: Duration): Boolean {
        merged.drawTextDecal(Float2D(2f, 2f), SCENE_TEXT)
        layers.target.decalInstances.forEach { queued += it.structure to it.vertexCount }
        frames++
        return frames < 2
    }
}

/** Draws "A" through two configured fonts so the decorator's decal cache is exercised. */
private class TwoFontMergedProbeEngine :
    Engine(WindowConfig(screenWidth = MERGED_TEST_WIDTH, screenHeight = MERGED_TEST_HEIGHT)),
    TextSceneTarget {
    private lateinit var merged: MergedTextAddon
    private lateinit var prop: KGEFont
    val runs = mutableListOf<DecalInstance>()

    override suspend fun onUserCreate(): Boolean {
        decalStructure = Decal.Structure.LIST
        merged = MergedTextAddon(this)
        val family = KGECoreFontService.createResources(resourceScope)
        prop = family.proportional.font(resourceScope, 8.fontPx)
        return true
    }

    override suspend fun onUserUpdate(elapsed: Duration): Boolean {
        merged.drawTextDecal(Float2D(2f, 2f), "A", Colors.WHITE, Float2D(1f, 1f), textFont)
        merged.drawTextDecal(Float2D(2f, 2f), "A", Colors.WHITE, Float2D(1f, 1f), prop)
        runs += layers.target.decalInstances
        return false
    }
}

/** Delegates to the real renderer and records the instances the frame flush consumes. */
private class RecordingRenderer(
    private val delegate: Renderer,
) : Renderer by delegate {
    val decals = mutableListOf<DecalInstance>()

    override fun drawDecal(
        scope: ResourceScope,
        instance: DecalInstance,
    ) {
        decals += instance
        delegate.drawDecal(scope, instance)
    }
}

/** Counts the decal probes the decorator issues through the public partial-decal seam. */
private class ProbeCountingDecalService(
    private val delegate: DrawPartialDecalService,
) : DrawPartialDecalService {
    var probes = 0
        private set

    override fun drawPartialDecal(
        position: Float2D,
        decal: Decal,
        sourcePosition: Float2D,
        sourceSize: Float2D,
        scale: Float2D,
        tint: Pixel,
        mode: Decal.Mode,
        structure: Decal.Structure,
        viewport: Int2D,
    ): DecalInstance {
        probes++
        return delegate.drawPartialDecal(
            position,
            decal,
            sourcePosition,
            sourceSize,
            scale,
            tint,
            mode,
            structure,
            viewport,
        )
    }
}

/** The merged lever's observable shape: one triangle-list instance per run, not one per glyph. */
class MergedTextAddonTest :
    FunSpec({
        test("the merged cell queues one triangle-list instance per run") {
            installGl()
            installDriver(RecordingDriver())
            val probes = ProbeCountingDecalService(DrawPartialDecalService.original)
            DrawPartialDecalService.override(probes)
            val engine = MergedProbeEngine()

            engine.start()

            // Two frames of one font: the identity-keyed cache serves the second
            // frame, so exactly one probe runs; a cache-less decorator probes twice.
            probes.probes shouldBe 1
            engine.queued shouldBe
                listOf(
                    Decal.Structure.LIST to MERGED_TEST_VERTS,
                    Decal.Structure.LIST to MERGED_TEST_VERTS,
                )
        }

        test("a second configured font probes and keeps its own decal") {
            installGl()
            installDriver(RecordingDriver())
            val probes = ProbeCountingDecalService(DrawPartialDecalService.original)
            DrawPartialDecalService.override(probes)
            val engine = TwoFontMergedProbeEngine()

            engine.start()

            // One probe per distinct font; the stale cache would reuse the mono decal.
            probes.probes shouldBe 2
            engine.runs.size shouldBe 2
            engine.runs.map { it.structure } shouldBe listOf(Decal.Structure.LIST, Decal.Structure.LIST)
            engine.runs.map { it.vertexCount } shouldBe listOf(6, 6)
            // Each font answers the decal it was probed from, and the UV scale follows
            // that decal's sprite; the two core faces share a sheet within one family,
            // so the fonts come from two families to make the decal identity visible.
            (engine.runs[0].decal !== engine.runs[1].decal) shouldBe true
            engine.runs.map { it.decal.sprite.width to it.decal.sprite.height } shouldBe
                listOf(128 to 48, 128 to 48)
        }

        test("the real engine installs the merged lever for the merged workload") {
            installGl()
            installDriver(RecordingDriver())
            val renderer = RecordingRenderer(Renderer.original)
            Renderer.override(renderer)

            val engine =
                FpsBenchmarkEngine(
                    config = WindowConfig(screenWidth = MERGED_TEST_WIDTH, screenHeight = MERGED_TEST_HEIGHT),
                    warmup = Duration.ZERO,
                    measure = Duration.ZERO,
                    workload = BenchmarkWorkload.TextMergedList,
                )

            engine.start()

            // renderTextScene queues SCENE_TEXT three times per row; at 240x48 that is
            // three rows, and the merged lever turns each call into one list instance
            // of SCENE_TEXT.length * 6 vertices instead of one quad per glyph.
            renderer.decals.map { it.structure to it.vertexCount } shouldBe
                List(MERGED_TEST_RUNS) { Decal.Structure.LIST to MERGED_TEST_VERTS }
        }
    })
