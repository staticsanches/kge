package dev.staticsanches.kge.engine

import dev.staticsanches.kge.renderer.gl.glContext
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import web.dom.document
import kotlin.time.Duration

private const val SMOKE_FRAMES = 3

/**
 * The real-GL engine smoke for the web platform default: open a canvas with a
 * WebGL2 context through the production driver, run the production loop for a
 * few frames paced by `requestAnimationFrame`, and assert the loop advanced and
 * the context was released. Runs on both browser targets (js + wasmJs).
 */
class WebEngineSmokeTest :
    FunSpec({
        test("the real web loop runs K frames and releases the context") {
            val childrenBefore = document.body.childElementCount
            var contextDuringRun = false
            val engine =
                object : Engine(WindowConfig(screenWidth = 64, screenHeight = 48)) {
                    private var frames = 0

                    override suspend fun onUserUpdate(elapsed: Duration): Boolean {
                        contextDuringRun = glContext != null
                        return ++frames < SMOKE_FRAMES
                    }
                }

            engine.start()

            engine.frame.frameCount shouldBe SMOKE_FRAMES
            contextDuringRun shouldBe true
            glContext shouldBe null
            document.body.childElementCount shouldBe childrenBefore
        }
    })
