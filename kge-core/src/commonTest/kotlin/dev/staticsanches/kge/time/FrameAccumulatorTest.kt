package dev.staticsanches.kge.time

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class FrameAccumulatorTest :
    FunSpec({
        test("elapsed is the delta between consecutive ticks") {
            val accumulator = FrameAccumulator()

            accumulator.tick(100.milliseconds) shouldBe Duration.ZERO
            accumulator.tick(350.milliseconds) shouldBe 250.milliseconds
            accumulator.tick(400.milliseconds) shouldBe 50.milliseconds
        }

        test("the first tick returns zero and publishes no FPS") {
            val accumulator = FrameAccumulator()

            accumulator.tick(Duration.ZERO) shouldBe Duration.ZERO

            accumulator.fps shouldBe 0
        }

        test("frameCount accumulates across ticks") {
            val accumulator = FrameAccumulator()

            accumulator.tick(Duration.ZERO)
            accumulator.tick(100.milliseconds)
            accumulator.tick(200.milliseconds)

            accumulator.frameCount shouldBe 3L
        }

        test("fps publishes the crossed window frame count and carries the drift") {
            val accumulator = FrameAccumulator()

            accumulator.tick(Duration.ZERO)
            accumulator.tick(600.milliseconds)
            accumulator.tick(1_200.milliseconds)

            accumulator.fps shouldBe 3

            // The 200ms drift is carried, so the next window closes 200ms early.
            accumulator.tick(1_400.milliseconds)
            accumulator.tick(2_000.milliseconds)

            accumulator.fps shouldBe 2
        }
    })
