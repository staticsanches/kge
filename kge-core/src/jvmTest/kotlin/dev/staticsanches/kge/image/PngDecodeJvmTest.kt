package dev.staticsanches.kge.image

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * JVM-only decode pin: on the JVM the engine buffer IS `java.nio.ByteBuffer`,
 * whose position must not influence [PngService.decode] — decode reads the
 * payload from its start, matching the position-less web buffer (uniform
 * contract, the parity floor).
 */
class PngDecodeJvmTest :
    FunSpec({
        test("decode reads the payload from the start, ignoring the buffer position") {
            tinyPngBytes.asEngineBuffer().use { wrapper ->
                wrapper.resource.position(4)

                PngService.decode(wrapper.resource).use { sprite ->
                    sprite.width shouldBe 2
                    sprite.height shouldBe 2
                    sprite.rowMajorPixels() shouldBe tinyPngPixels
                }
            }
        }
    })
