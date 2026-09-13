package dev.staticsanches.kge.time

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class TimeServiceTest :
    FunSpec({
        test("the default clock is monotonic and never decreases") {
            var previous = Time.elapsed()

            repeat(1_000) {
                val current = Time.elapsed()
                current shouldBeGreaterThanOrEqualTo previous
                previous = current
            }
        }

        test("a virtual clock override changes Time.elapsed() process-wide") {
            TimeService.override(FakeTimeService(step = 100.milliseconds))

            Time.elapsed() shouldBe Duration.ZERO
            Time.elapsed() shouldBe 100.milliseconds
            Time.elapsed() shouldBe 200.milliseconds
        }
    })
