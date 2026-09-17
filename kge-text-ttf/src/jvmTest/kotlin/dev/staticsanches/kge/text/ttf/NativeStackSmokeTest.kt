package dev.staticsanches.kge.text.ttf

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.freetype.FreeType
import org.lwjgl.util.harfbuzz.HarfBuzz

/**
 * The JVM native-stack smoke: the HarfBuzz and FreeType LWJGL bindings load
 * their native libraries and initialize (HarfBuzz reports a version string;
 * FreeType initializes and tears down error-free). No shaping or rasterization.
 */
class NativeStackSmokeTest :
    FunSpec({
        test("the HarfBuzz native library reports a version") {
            HarfBuzz.hb_version_string().shouldNotBeBlank()
        }

        test("FreeType initializes and tears down without error") {
            MemoryStack.stackPush().use { stack ->
                val library = stack.mallocPointer(1)
                FreeType.FT_Init_FreeType(library) shouldBe FreeType.FT_Err_Ok
                val handle = library[0]
                try {
                    handle shouldNotBe 0L
                } finally {
                    if (handle != 0L) {
                        FreeType.FT_Done_FreeType(handle) shouldBe FreeType.FT_Err_Ok
                    }
                }
            }
        }
    })
