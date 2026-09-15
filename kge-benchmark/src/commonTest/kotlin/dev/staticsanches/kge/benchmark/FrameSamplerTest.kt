package dev.staticsanches.kge.benchmark

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class FrameSamplerTest :
    FunSpec({
        test("discards the warmup and averages the measured window") {
            val sampler = FrameSampler(warmup = 1.seconds, measure = 2.seconds)

            var running = true
            var fed = 0
            while (running) {
                running = sampler.sample(100.milliseconds, fps = 0)
                fed++
            }

            // 10 warmup frames (t=0.1..1.0) plus 20 measured frames (t=1.1..3.0).
            fed shouldBe 30
            sampler.metrics().avgFps shouldBe 10.0
            sampler.metrics().msPerFrame shouldBe 100.0
        }

        test("reports the slowest distinct window as the minimum FPS") {
            val sampler = FrameSampler(warmup = Duration.ZERO, measure = 1.seconds)

            val windows = listOf(60, 60, 60, 57, 57, 61, 61, 55, 55, 59, 59)
            var running = true
            var index = 0
            while (running) {
                running = sampler.sample(100.milliseconds, fps = windows[index % windows.size])
                index++
            }

            sampler.metrics().minFps shouldBe 55
        }

        test("reports no minimum when the measured window never published FPS") {
            val sampler = FrameSampler(warmup = Duration.ZERO, measure = 1.seconds)

            var running = true
            while (running) {
                running = sampler.sample(100.milliseconds, fps = 0)
            }

            sampler.metrics().minFps shouldBe 0
        }
    })
