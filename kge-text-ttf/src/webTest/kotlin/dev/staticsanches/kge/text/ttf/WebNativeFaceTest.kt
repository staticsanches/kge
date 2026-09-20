package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.font.roboto.Roboto
import dev.staticsanches.kge.resource.ResourceWrapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The web-only face behavior: `harfbuzzjs` copies the payload into wasm memory,
 * so the engine buffer is staging and is released as soon as the face exists.
 */
@OptIn(KGESensitiveAPI::class)
class WebNativeFaceTest :
    FunSpec({
        test("loading releases the staging payload before the font is closed") {
            var payload: ResourceWrapper<ByteBuffer>? = null
            BufferService.override(
                object : BufferService {
                    override fun allocate(
                        sizeInBytes: Int,
                        name: String?,
                    ): ResourceWrapper<ByteBuffer> =
                        BufferService.original.allocate(sizeInBytes, name).also { payload = it }
                },
            )
            try {
                val font = Font.load(Roboto.variableFont)
                try {
                    // No close yet: the staging buffer is already gone because
                    // the bytes live in wasm memory.
                    val allocated = checkNotNull(payload) { "load must allocate the payload" }
                    allocated.cleaned shouldBe true
                } finally {
                    font.close()
                }
            } finally {
                // kge-core resets overrides between its own tests only, so this
                // module restores the engine default itself.
                BufferService.override(BufferService.original)
            }
        }
    })
