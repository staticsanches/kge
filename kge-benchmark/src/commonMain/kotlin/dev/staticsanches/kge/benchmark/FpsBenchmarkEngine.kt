package dev.staticsanches.kge.benchmark

import dev.staticsanches.kge.engine.Engine
import dev.staticsanches.kge.engine.WindowConfig
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.SpriteService
import kotlin.time.Duration

/**
 * An [Engine] that runs for the configured window under [workload] and exposes
 * its metrics. The [BenchmarkWorkload.Render] workload blits one sprite,
 * created with the run and released when it stops.
 */
internal class FpsBenchmarkEngine(
    config: WindowConfig,
    warmup: Duration,
    measure: Duration,
    private val workload: BenchmarkWorkload,
) : Engine(config),
    SceneTarget {
    private val sampler = FrameSampler(warmup, measure)
    private var sprite: Sprite? = null

    override suspend fun onUserCreate(): Boolean {
        if (workload == BenchmarkWorkload.Render) {
            sprite = createBlitSprite()
        }
        return true
    }

    override suspend fun onUserUpdate(elapsed: Duration): Boolean {
        val blitSource = sprite
        if (blitSource != null) {
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
