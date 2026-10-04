package dev.staticsanches.kge.text

import dev.staticsanches.kge.text.KGEFont.Axis.Tag
import dev.staticsanches.kge.text.KGEFont.Axis.Value
import dev.staticsanches.kge.text.KGEFont.Size
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * The value vocabulary of the text API: positive-only pixel sizes, four
 * printable ASCII axis tags and canonical 16.16 axis coordinates.
 */
class CoreFontModelTest :
    FunSpec({
        test("Size.ofOrNull accepts a positive pixel size") {
            Size.ofOrNull(1)?.px shouldBe 1
            Size.ofOrNull(1024)?.px shouldBe 1024
        }

        test("Size.ofOrNull rejects zero and negative pixel sizes") {
            Size.ofOrNull(0) shouldBe null
            Size.ofOrNull(-1) shouldBe null
        }

        test("Int.fontPx wraps the receiver") {
            8.fontPx.px shouldBe 8
        }

        test("Int.fontPx throws for a non-positive size") {
            shouldThrow<IllegalArgumentException> { 0.fontPx }
            shouldThrow<IllegalArgumentException> { (-1).fontPx }
        }

        test("Size orders by px") {
            (Size.ofOrNull(8)!! < Size.ofOrNull(16)!!) shouldBe true
            Size.ofOrNull(8)!!.compareTo(Size.ofOrNull(8)!!) shouldBe 0
            (Size.ofOrNull(16)!! > Size.ofOrNull(8)!!) shouldBe true
        }

        test("the five Tag constants carry their registered tags") {
            Tag.Weight.raw shouldBe "wght"
            Tag.Width.raw shouldBe "wdth"
            Tag.OpticalSize.raw shouldBe "opsz"
            Tag.Slant.raw shouldBe "slnt"
            Tag.Italic.raw shouldBe "ital"
        }

        test("Tag.ofOrNull preserves a valid custom tag verbatim, including case") {
            Tag.ofOrNull("ABCD")?.raw shouldBe "ABCD"
            Tag.ofOrNull("aB3!")?.raw shouldBe "aB3!"
        }

        test("Tag.ofOrNull rejects anything but four printable ASCII characters") {
            Tag.ofOrNull("wg") shouldBe null
            Tag.ofOrNull("wght ") shouldBe null
            Tag.ofOrNull("wg\th") shouldBe null
            Tag.ofOrNull("wgät") shouldBe null
            Tag.ofOrNull("wg\u0000t") shouldBe null
        }

        test("Value.of is the exact integer conversion") {
            Value.of(65536).floatValue shouldBe 1f
            Value.of(-65536).floatValue shouldBe -1f
            Value.of(1).raw shouldBe 1
            1.axisValue shouldBe Value.of(65536)
            1.axisValue shouldBe 1f.axisValue
            (-1).axisValue shouldBe Value.of(-65536)
            shouldThrow<IllegalArgumentException> { 32768.axisValue }
        }

        test("Float.axisValue quantizes to the nearest representable value") {
            1f.axisValue shouldBe Value.of(65536)
            0.5f.axisValue shouldBe Value.of(32768)
            0.00001f.axisValue shouldBe Value.of(1)
        }

        test("Value.ofOrNull rejects values outside the signed 16.16 range") {
            Value.ofOrNull(32768f) shouldBe null
            Value.ofOrNull(-32769f) shouldBe null
            Value.ofOrNull(32767.99f) shouldNotBe null
        }

        test("Value.ofOrNull rejects NaN and both infinities") {
            Value.ofOrNull(Float.NaN) shouldBe null
            Value.ofOrNull(Float.POSITIVE_INFINITY) shouldBe null
            Value.ofOrNull(Float.NEGATIVE_INFINITY) shouldBe null
        }

        test("Value orders by raw") {
            (Value.of(1) < Value.of(2)) shouldBe true
            Value.of(-1).compareTo(Value.of(-1)) shouldBe 0
            (Value.of(-65536) < Value.of(0)) shouldBe true
        }

        test("Value.toString emits the shortest plain decimal spelling") {
            Value.of(0).toString() shouldBe "0"
            Value.of(65536).toString() shouldBe "1"
            Value.of(32768).toString() shouldBe "0.5"
            Value.of(-65536).toString() shouldBe "-1"
        }

        test("Value.toString round-trips through ofOrNull") {
            for (raw in listOf(1, 32768, 65536, -65536, 0, 98304, Int.MIN_VALUE, -98304, -32768, -3, -1)) {
                Value.ofOrNull(Value.of(raw).toString().toFloat()) shouldBe Value.of(raw)
            }
        }
    })
