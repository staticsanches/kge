package dev.staticsanches.kge.benchmark

import dev.staticsanches.kge.image.Colors.WHITE
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.extension.create
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.rasterizer.service.DrawLineService
import dev.staticsanches.kge.utils.invokeForAll
import kotlin.math.roundToInt
import kotlin.time.measureTime

/**
 * Baseline benchmark for the software rasterizer, shared by the JVM and JS targets.
 *
 * It measures the current state of the rasterizer so the impact of optimizations can be evaluated.
 * It does not require a window: it draws on plain [Sprite]s.
 *
 * Run it on the JVM via `./gradlew :kge-core:benchmark` (or `--quick` for a smoke test), or as a
 * test on both targets via [RasterizerBenchmarkTest].
 */
object RasterizerBenchmark {
    private val COLOR = WHITE
    private val PIXEL_MODE = Pixel.Mode.Normal

    fun run(quick: Boolean) {
        println("=== KGE Rasterizer Benchmark ===")
        println("quick mode: $quick")
        println()

        val target600x400 = Sprite.create(width = 600, height = 400)
        val target1920x1080 = Sprite.create(width = 1920, height = 1080)
        val sprite256x256 = Sprite.create(width = 256, height = 256)
        val sprite32x32 = Sprite.create(width = 32, height = 32)

        try {
            println(
                "  ${"case".padEnd(42)} ${"pixels/op".padStart(10)} ${"µs/op".padStart(12)} ${"Mpx/s".padStart(12)}",
            )
            println("  ${"-".repeat(42)} ${"-".repeat(10)} ${"-".repeat(12)} ${"-".repeat(12)}")

            runCase("fillRect 600x400 (full screen)", 600L * 400, 50, 300, quick) {
                Rasterizer.fillRect(0, 0, 599, 399, COLOR, target600x400, PIXEL_MODE)
            }

            runCase("fillRect 1920x1080 (full screen)", 1920L * 1080, 10, 30, quick) {
                Rasterizer.fillRect(0, 0, 1919, 1079, COLOR, target1920x1080, PIXEL_MODE)
            }

            runCase("drawSprite 256x256 scale=1", 256L * 256, 50, 300, quick) {
                Rasterizer.drawSprite(10, 10, sprite256x256, 1, Sprite.Flip.NONE, target600x400, PIXEL_MODE)
            }

            runCase("drawSprite 32x32 scale=8", 32L * 8 * 32 * 8, 50, 300, quick) {
                Rasterizer.drawSprite(10, 10, sprite32x32, 8, Sprite.Flip.NONE, target600x400, PIXEL_MODE)
            }

            runCase("fillTriangle large (~74.5k px)", 74_500L, 50, 300, quick) {
                Rasterizer.fillTriangle(
                    50, 20, 550, 380,
                    150, 390,
                    COLOR, target600x400, PIXEL_MODE,
                )
            }

            runCase("drawLine (0,0)-(599,399)", 720L, 100, 2_000, quick) {
                Rasterizer.drawLine(
                    0, 0, 599, 399,
                    COLOR, DrawLineService.LinePattern.Filled,
                    target600x400, PIXEL_MODE,
                )
            }

            runCase("draw single pixel", 1L, 10_000, 100_000, quick) {
                Rasterizer.draw(0, 0, COLOR, target600x400, PIXEL_MODE)
            }
        } finally {
            invokeForAll(target600x400, target1920x1080, sprite256x256, sprite32x32) { it.close() }
        }
    }

    private fun runCase(
        label: String,
        pixelsPerOp: Long,
        warmupIterations: Int,
        measuredIterations: Int,
        quick: Boolean,
        run: () -> Unit,
    ) {
        val warmup = if (quick) maxOf(2, warmupIterations / 10) else warmupIterations
        val iterations = if (quick) maxOf(5, measuredIterations / 10) else measuredIterations

        repeat(warmup) { run() }

        val elapsedNs =
            measureTime {
                repeat(iterations) { run() }
            }.inWholeNanoseconds

        val nsPerOp = elapsedNs.toDouble() / iterations
        val usPerOp = nsPerOp / 1_000.0
        val mpxPerSec = pixelsPerOp / nsPerOp * 1_000.0

        println(
            "${"  " + label.padEnd(42)} ${pixelsPerOp.toString().padStart(10)} " +
                "${format2(usPerOp).padStart(12)} ${format2(mpxPerSec).padStart(12)}",
        )
    }

    private fun format2(value: Double): String {
        val scaled = (value * 100).roundToInt()
        return "${scaled / 100}.${(scaled % 100).toString().padStart(2, '0')}"
    }
}
