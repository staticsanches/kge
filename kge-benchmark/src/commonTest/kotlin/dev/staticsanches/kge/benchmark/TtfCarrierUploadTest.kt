@file:OptIn(KGESensitiveAPI::class)

package dev.staticsanches.kge.benchmark

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.DriverService
import dev.staticsanches.kge.engine.Engine
import dev.staticsanches.kge.engine.WindowConfig
import dev.staticsanches.kge.font.roboto.Roboto
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.gl.service.GLService
import dev.staticsanches.kge.testsupport.engine.RecordingDriver
import dev.staticsanches.kge.testsupport.engine.installDriver
import dev.staticsanches.kge.testsupport.engine.installGl
import dev.staticsanches.kge.testsupport.gl.RecordingGLService
import dev.staticsanches.kge.text.ttf.Font
import dev.staticsanches.kge.text.ttf.TtfDrawStringAddon
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration

/** The real cell's line, so the premise is pinned on the load the benchmark measures. */
private const val CARRIER_PROBE_TEXT = "SCORE 123456  FPS 60  TIME 12:34  HEALTH 100"

/** One build frame plus three steady-state frames. */
private const val CARRIER_PROBE_FRAMES = 4

/** Draws the TTF line once per frame and snapshots the carrier's upload count at each frame start. */
private class CarrierProbeEngine :
    Engine(WindowConfig(screenWidth = 240, screenHeight = 48)),
    TtfDrawStringAddon {
    /** Both text roles declare the name; the engine's tab-stop default backs it. */
    override var tabSizeInSpaces: Int
        get() = super<Engine>.tabSizeInSpaces
        set(value) {
            super<Engine>.tabSizeInSpaces = value
        }

    lateinit var font: Font
    lateinit var gl: RecordingGLService
    val uploadsAtFrameStart = mutableListOf<Int>()
    private var frames = 0

    override suspend fun onUserUpdate(elapsed: Duration): Boolean {
        uploadsAtFrameStart += gl.calls.count { it.name == "texSubImage2D" }
        drawStringDecal(font, Float2D(2f, 2f), CARRIER_PROBE_TEXT, 16)
        frames++
        return frames < CARRIER_PROBE_FRAMES
    }

    override suspend fun onUserDestroy(): Boolean {
        font.close()
        return true
    }
}

/**
 * The premise the upload-policy cell rests on: the carrier uploads each placement
 * once while it builds, so a steady-state frame issues no region update at all.
 */
class TtfCarrierUploadTest :
    FunSpec({
        test("the carrier uploads each placement once and a steady-state frame issues none") {
            val gl = installGl()
            installDriver(RecordingDriver(scriptedFramebufferSize = Int2D(240, 48)))
            try {
                val engine = CarrierProbeEngine()
                engine.gl = gl
                engine.font = Font.load(Roboto.romanFont)

                engine.start()

                val counts = engine.uploadsAtFrameStart + gl.calls.count { it.name == "texSubImage2D" }
                counts.size shouldBe CARRIER_PROBE_FRAMES + 1
                val perFrame = (0 until CARRIER_PROBE_FRAMES).map { counts[it + 1] - counts[it] }
                (perFrame.first() > 0) shouldBe true
                perFrame.drop(1) shouldBe List(CARRIER_PROBE_FRAMES - 1) { 0 }
            } finally {
                GLService.override(GLService.original)
                DriverService.override(DriverService.original)
            }
        }
    })
