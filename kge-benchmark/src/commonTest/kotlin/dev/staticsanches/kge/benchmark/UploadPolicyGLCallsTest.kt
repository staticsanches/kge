package dev.staticsanches.kge.benchmark

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.buffer.byteAt
import dev.staticsanches.kge.buffer.putByte
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.GLTexture
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.testsupport.gl.RecordingGLService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val CHART_WIDTH = 8
private const val CHART_HEIGHT = 4
private const val BOX_X = 1
private const val BOX_Y = 1
private const val BOX_WIDTH = 3
private const val BOX_HEIGHT = 2

/** The default `GL_UNPACK_ALIGNMENT`: every client row starts 4-byte aligned. */
private fun clientStride(
    width: Int,
    bytesPerPixel: Int,
): Int = (width * bytesPerPixel + 3) and 3.inv()

/** One byte per pixel, row-major: the pixel `(1 + row * BOX_WIDTH + column)`. */
private fun boxBytes(): ResourceWrapper<ByteBuffer> {
    val stride = clientStride(BOX_WIDTH, 1)
    val buffer = BufferService.allocate(stride * BOX_HEIGHT, "upload policy test box")
    for (row in 0 until BOX_HEIGHT) {
        for (column in 0 until BOX_WIDTH) {
            buffer.resource.putByte(row * stride + column, 1 + row * BOX_WIDTH + column)
        }
    }
    return buffer
}

/** Seeds a carrier-style chart: bind, uninitialized `texImage2D`, then the box region update. */
private fun seed(
    decorator: UploadPolicyGLCalls,
    texture: GLTexture,
    box: ByteBuffer,
) {
    decorator.bindTexture(GL.TEXTURE_2D, texture)
    decorator.texImage2D(GL.TEXTURE_2D, 0, GL.RED, CHART_WIDTH, CHART_HEIGHT, 0, GL.RED, GL.UNSIGNED_BYTE, null)
    decorator.texSubImage2D(GL.TEXTURE_2D, 0, BOX_X, BOX_Y, BOX_WIDTH, BOX_HEIGHT, GL.RED, GL.UNSIGNED_BYTE, box)
}

/**
 * The replay policies: the full one must re-upload the whole shadow with each
 * recorded box at its chart offset, the region one must re-issue the box.
 */
class UploadPolicyGLCallsTest :
    FunSpec({
        test("full replay carries the box bytes in the whole shadow and region replay re-issues the box") {
            val recorder = RecordingGLService()
            val box = boxBytes()
            try {
                val full = UploadPolicyGLCalls(recorder, UploadPolicy.FULL)
                try {
                    seed(full, recorder.createTexture(), box.resource)
                    recorder.clear()

                    full.replayFrame()

                    val image = recorder.calls.filter { it.name == "texImage2D" }.single()
                    image.arguments[2] shouldBe GL.RED
                    image.arguments[3] shouldBe CHART_WIDTH
                    image.arguments[4] shouldBe CHART_HEIGHT
                    image.arguments[6] shouldBe GL.RED
                    image.arguments[7] shouldBe GL.UNSIGNED_BYTE
                    val whole = image.arguments[8] as ByteBuffer
                    val chartStride = clientStride(CHART_WIDTH, 1)
                    for (row in 0 until CHART_HEIGHT) {
                        for (column in 0 until CHART_WIDTH) {
                            val inside =
                                column in BOX_X until BOX_X + BOX_WIDTH &&
                                    row in BOX_Y until BOX_Y + BOX_HEIGHT
                            val expected = if (inside) 1 + (row - BOX_Y) * BOX_WIDTH + (column - BOX_X) else 0
                            whole.byteAt(row * chartStride + column) shouldBe expected
                        }
                    }
                } finally {
                    full.close()
                }

                val region = UploadPolicyGLCalls(recorder, UploadPolicy.REGION)
                try {
                    seed(region, recorder.createTexture(), box.resource)
                    recorder.clear()

                    region.replayFrame()

                    val replayed = recorder.calls.filter { it.name == "texSubImage2D" }.single()
                    replayed.arguments[2] shouldBe BOX_X
                    replayed.arguments[3] shouldBe BOX_Y
                    replayed.arguments[4] shouldBe BOX_WIDTH
                    replayed.arguments[5] shouldBe BOX_HEIGHT
                    replayed.arguments[6] shouldBe GL.RED
                    val bytes = replayed.arguments[8] as ByteBuffer
                    val boxStride = clientStride(BOX_WIDTH, 1)
                    for (row in 0 until BOX_HEIGHT) {
                        for (column in 0 until BOX_WIDTH) {
                            bytes.byteAt(row * boxStride + column) shouldBe
                                box.resource.byteAt(row * boxStride + column)
                        }
                    }
                } finally {
                    region.close()
                }
            } finally {
                box.close()
            }
        }
    })
