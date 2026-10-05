package dev.staticsanches.kge.benchmark

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.Engine
import dev.staticsanches.kge.engine.WindowConfig
import dev.staticsanches.kge.font.roboto.Roboto
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.service.GLService
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.text.ttf.Font
import kotlin.time.Duration

/**
 * An [Engine] that runs [workload] for the configured window and exposes its
 * metrics; the sprite it blits and every service override live for one run.
 */
@OptIn(KGESensitiveAPI::class)
internal class FpsBenchmarkEngine(
    config: WindowConfig,
    warmup: Duration,
    measure: Duration,
    private val workload: BenchmarkWorkload,
) : Engine(config),
    SceneTarget,
    TextSceneTarget,
    TtfTextSceneTarget {
    private val sampler = FrameSampler(warmup, measure)
    private var sprite: Sprite? = null
    private var mergedText: MergedTextAddon? = null

    /** Both text addons' default, made explicit because the engine carries both. */
    override var tabSizeInSpaces: Int
        get() = super<Engine>.tabSizeInSpaces
        set(value) {
            super<Engine>.tabSizeInSpaces = value
        }

    private var font: Font? = null
    private var uploads: UploadPolicyGLCalls? = null

    /** The driver's maximum texture side, queried while the context is current. */
    var maxTextureSize: Int = 0
        private set

    override suspend fun onUserCreate(): Boolean {
        maxTextureSize = GLService.getInteger(GL.MAX_TEXTURE_SIZE)
        when (workload) {
            BenchmarkWorkload.Render -> sprite = createBlitSprite()
            BenchmarkWorkload.Empty -> Unit
            BenchmarkWorkload.TextPerGlyphStrip -> decalStructure = Decal.Structure.STRIP
            BenchmarkWorkload.TextPerGlyphList -> decalStructure = Decal.Structure.LIST
            BenchmarkWorkload.TextMergedList -> {
                decalStructure = Decal.Structure.LIST
                mergedText = MergedTextAddon(this)
            }
            BenchmarkWorkload.TextTtfRegion -> loadTtfText(UploadPolicy.REGION)
            BenchmarkWorkload.TextTtfFull -> loadTtfText(UploadPolicy.FULL)
            BenchmarkWorkload.RendererPassthrough -> installRendererLevers()
            BenchmarkWorkload.RendererBlend -> installRendererLevers(dedupeBlend = true)
            BenchmarkWorkload.RendererDedupe ->
                installRendererLevers(dedupeBlend = true, dedupeDisable = true, dedupeTexture = true)
            BenchmarkWorkload.RendererUpload -> installRendererLevers(appendUploads = true)
            BenchmarkWorkload.RendererBatched ->
                installRendererLevers(
                    dedupeBlend = true,
                    dedupeDisable = true,
                    dedupeTexture = true,
                    appendUploads = true,
                )
        }
        return true
    }

    /** Loads the bundled font and installs the upload-policy decorator of [policy]. */
    private suspend fun loadTtfText(policy: UploadPolicy) {
        val loaded = Font.load(Roboto.romanFont)
        font = loaded
        val decorator = UploadPolicyGLCalls(GLService.original, policy)
        try {
            resourceScope.register(UploadPolicyKey, decorator)
            GLService.override(decorator)
            uploads = decorator
        } catch (failure: Throwable) {
            decorator.close()
            loaded.close()
            font = null
            throw failure
        }
    }

    /** Runs the per-glyph text scene under the measured renderer levers. */
    private fun installRendererLevers(
        dedupeBlend: Boolean = false,
        dedupeDisable: Boolean = false,
        dedupeTexture: Boolean = false,
        appendUploads: Boolean = false,
    ) {
        decalStructure = Decal.Structure.STRIP
        val decorator =
            BatchGLCalls(
                delegate = GLService.original,
                dedupeBlend = dedupeBlend,
                dedupeDisable = dedupeDisable,
                dedupeTexture = dedupeTexture,
                appendUploads = appendUploads,
            )
        try {
            resourceScope.register(LeverDecoratorKey, decorator)
            GLService.override(decorator)
        } catch (failure: Throwable) {
            decorator.close()
            throw failure
        }
    }

    override suspend fun onUserUpdate(elapsed: Duration): Boolean {
        val blitSource = sprite
        val textFont = font
        if (textFont != null) {
            renderTtfTextScene(this, textFont, window.screenSize.x, window.screenSize.y)
            uploads?.replayFrame()
        } else if (workload.isText) {
            renderTextScene(mergedText ?: this, window.screenSize.x, window.screenSize.y)
        } else if (blitSource != null) {
            renderScene(this, window.screenSize.x, window.screenSize.y, blitSource)
        } else {
            // The draw target holds uninitialized native memory until written;
            // even the empty workload must present a defined background.
            clear(SCENE_BACKGROUND)
        }
        return sampler.sample(elapsed, frame.fps)
    }

    override suspend fun onUserDestroy(): Boolean {
        uploads?.let { decorator ->
            val boxes = decorator.recordedBoxCount
            println("${workload.label}: replayedBoxes=$boxes touchedCharts=${decorator.touchedChartCount}")
        }
        // Closed while the context is current, before the scope releases the
        // decorator the carrier's textures were created through.
        font?.close()
        font = null
        uploads = null
        sprite?.close()
        sprite = null
        return true
    }

    fun metrics(): BenchmarkMetrics = sampler.metrics()

    private fun createBlitSprite(): Sprite {
        val result =
            SpriteService.create(
                SCENE_SPRITE_SIZE,
                SCENE_SPRITE_SIZE,
                Pixmap.SampleMode.NORMAL,
                "benchmark-blit",
            )
        for (y in 0 until result.height) {
            for (x in 0 until result.width) {
                result.uncheckedSet(x, y, if ((x + y) % 2 == 0) Colors.WHITE else Colors.BLUE)
            }
        }
        return result
    }
}

/** The run's lever decorator, so the scope releases its persistent vertex buffer. */
private object LeverDecoratorKey : ResourceScope.Key<BatchGLCalls>

/** The run's upload-policy decorator, so the scope releases its shadows. */
private object UploadPolicyKey : ResourceScope.Key<UploadPolicyGLCalls>
