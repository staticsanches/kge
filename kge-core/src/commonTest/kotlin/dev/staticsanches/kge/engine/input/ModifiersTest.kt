package dev.staticsanches.kge.engine.input

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** The modifier snapshot: shift/ctrl/alt/super and the two lock states. */
class ModifiersTest :
    FunSpec({
        test("no modifier reads as unset") {
            val modifiers = Modifiers.of()
            modifiers.shift shouldBe false
            modifiers.ctrl shouldBe false
            modifiers.alt shouldBe false
            modifiers.superKey shouldBe false
            modifiers.capsLock shouldBe false
            modifiers.numLock shouldBe false
        }

        test("each flag reads independently") {
            Modifiers.of(shift = true).shift shouldBe true
            Modifiers.of(shift = true).ctrl shouldBe false
            Modifiers.of(ctrl = true).ctrl shouldBe true
            Modifiers.of(alt = true).alt shouldBe true
            Modifiers.of(superKey = true).superKey shouldBe true
            Modifiers.of(capsLock = true).capsLock shouldBe true
            Modifiers.of(numLock = true).numLock shouldBe true
        }

        test("the flags combine in one snapshot") {
            val modifiers =
                Modifiers.of(
                    shift = true,
                    ctrl = true,
                    alt = true,
                    superKey = true,
                    capsLock = true,
                    numLock = true,
                )
            modifiers.shift shouldBe true
            modifiers.ctrl shouldBe true
            modifiers.alt shouldBe true
            modifiers.superKey shouldBe true
            modifiers.capsLock shouldBe true
            modifiers.numLock shouldBe true
        }
    })
