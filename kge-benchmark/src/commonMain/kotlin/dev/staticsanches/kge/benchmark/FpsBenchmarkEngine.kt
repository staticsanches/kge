package dev.staticsanches.kge.benchmark

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.Engine
import dev.staticsanches.kge.engine.WindowConfig
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.gl.service.GLService
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.text.DrawStringService
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
    TextSceneTarget {
    private val sampler = FrameSampler(warmup, measure)
    private var sprite: Sprite? = null

    override suspend fun onUserCreate(): Boolean {
        when (workload) {
            BenchmarkWorkload.Render -> sprite = createBlitSprite()
            BenchmarkWorkload.Empty -> Unit
            BenchmarkWorkload.TextPerGlyphStrip -> decalStructure = Decal.Structure.STRIP
            BenchmarkWorkload.TextPerGlyphList -> decalStructure = Decal.Structure.LIST
            BenchmarkWorkload.TextMergedList -> {
                decalStructure = Decal.Structure.LIST
                DrawStringService.override(MergedDrawStringService(DrawStringService.original))
            }
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
        if (workload.isText) {
            renderTextScene(this, window.screenSize.x, window.screenSize.y)
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
