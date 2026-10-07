package dev.staticsanches.kge.engine.console

import dev.staticsanches.kge.engine.ScriptedEngine
import dev.staticsanches.kge.engine.input.KeyboardKey
import dev.staticsanches.kge.engine.input.escape
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.renderer.decal.service.DrawPartialDecalService
import dev.staticsanches.kge.renderer.decal.service.DrawPolygonDecalService
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.testsupport.engine.RecordingDriver
import dev.staticsanches.kge.testsupport.engine.installDriver
import dev.staticsanches.kge.testsupport.engine.installGl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The console's drawing over one scripted frame: the shadow and the cursor
 * through the polygon seam, the transcript and the prompt through the glyph seam.
 */
class ConsoleDrawTest :
    FunSpec({
        test("the shadow is one quad over the whole screen with the four corner tints") {
            val frame = runConsoleFrame()

            val shadow = frame.polygons.calls.first()
            shadow.pos shouldBe
                listOf(
                    Float2D(0f, 0f),
                    Float2D(0f, 240f),
                    Float2D(320f, 240f),
                    Float2D(320f, 0f),
                )
            shadow.tint shouldBe
                listOf(
                    Pixel.rgba(0x00007F7Fu),
                    Pixel.rgba(0x00003F7Fu),
                    Pixel.rgba(0x00003F7Fu),
                    Pixel.rgba(0x00003F7Fu),
                )
            shadow.decal.sprite.width shouldBe 1
            shadow.decal.sprite.height shouldBe 1
        }

        test("the cursor quad sits at the entry's cursor cell in DARK_CYAN over a 1x1 sprite") {
            val frame = runConsoleFrame(entryText = "ab")

            val cursor = frame.polygons.calls.single { call -> call.tint.all { it == Colors.DARK_CYAN } }
            cursor.pos shouldBe
                listOf(
                    Float2D(32f, 176f),
                    Float2D(32f, 192f),
                    Float2D(40f, 192f),
                    Float2D(40f, 176f),
                )
            cursor.decal.sprite.width shouldBe 1
            cursor.decal.sprite.height shouldBe 1
        }

        test("the prompt line is drawn in YELLOW at the last grid line") {
            val frame = runConsoleFrame(entryText = "ab")

            val prompt = frame.partials.calls.filter { it.tint == Colors.YELLOW }
            prompt.size shouldBe 3
            prompt.first().position shouldBe Float2D(8f, 176f)
            prompt.forEach { it.position.y shouldBe 176f }
        }

        test("every transcript line is drawn in WHITE, one quad per glyph") {
            val frame = runConsoleFrame(text = "hi\nyo")

            val transcript = frame.partials.calls.filter { it.tint == Colors.WHITE }
            transcript.map { it.position } shouldBe
                listOf(
                    Float2D(8f, 16f),
                    Float2D(16f, 16f),
                    Float2D(8f, 32f),
                    Float2D(16f, 32f),
                )
        }

        test("the console's decal instances are queued on layer 0 and drawn after the other layers'") {
            val polygons = PolygonRecorder(DrawPolygonDecalService.original)
            DrawPolygonDecalService.override(polygons)
            val partials = PartialRecorder(DrawPartialDecalService.original)
            DrawPartialDecalService.override(partials)
            val gl = installGl()
            installDriver(RecordingDriver(scriptedFramebufferSize = Int2D(320, 240)))
            var frames = 0
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        frames++
                        if (frames == 1) {
                            e.layers.createLayer()
                            e.layers[1].show = true
                            e.console.show(KeyboardKey.escape)
                        }
                        frames < 1
                    },
                )

            engine.start()

            val draws = gl.calls.filter { it.name == "drawArrays" }.map { it.arguments }
            draws.size shouldBe 2 + polygons.calls.size + partials.calls.size
            draws.take(2).forEach { it[0] shouldBe GL.TRIANGLE_STRIP }
            draws.drop(2).forEach { it[0] shouldBe GL.TRIANGLE_FAN }
        }
    })

private class ConsoleFrame(
    val polygons: PolygonRecorder,
    val partials: PartialRecorder,
)

/** Runs one 320x240 engine frame with the console showing and the requested content. */
private suspend fun runConsoleFrame(
    text: String? = null,
    entryText: String? = null,
): ConsoleFrame {
    val polygons = PolygonRecorder(DrawPolygonDecalService.original)
    DrawPolygonDecalService.override(polygons)
    val partials = PartialRecorder(DrawPartialDecalService.original)
    DrawPartialDecalService.override(partials)
    installGl()
    installDriver(RecordingDriver(scriptedFramebufferSize = Int2D(320, 240)))
    var frames = 0
    val engine =
        ScriptedEngine(
            onUpdate = { e, _ ->
                frames++
                if (frames == 1) {
                    e.console.show(KeyboardKey.escape)
                    text?.let { e.console.write(it) }
                    entryText?.let { e.textEntry.enable(it) }
                }
                frames < 1
            },
        )

    engine.start()

    return ConsoleFrame(polygons, partials)
}

private class PolygonCall(
    val decal: Decal,
    val pos: List<Float2D>,
    val tint: List<Pixel>,
)

private class PolygonRecorder(
    private val delegate: DrawPolygonDecalService,
) : DrawPolygonDecalService {
    val calls = mutableListOf<PolygonCall>()

    override fun drawPolygonDecal(
        decal: Decal,
        pos: List<Float2D>,
        uv: List<Float2D>,
        tint: List<Pixel>,
        mode: Decal.Mode,
        structure: Decal.Structure,
        viewport: Int2D,
    ): DecalInstance {
        calls += PolygonCall(decal, pos, tint)
        return delegate.drawPolygonDecal(decal, pos, uv, tint, mode, structure, viewport)
    }
}

private class PartialCall(
    val position: Float2D,
    val tint: Pixel,
)

private class PartialRecorder(
    private val delegate: DrawPartialDecalService,
) : DrawPartialDecalService {
    val calls = mutableListOf<PartialCall>()

    override fun drawPartialDecal(
        position: Float2D,
        decal: Decal,
        sourcePosition: Float2D,
        sourceSize: Float2D,
        scale: Float2D,
        tint: Pixel,
        mode: Decal.Mode,
        structure: Decal.Structure,
        viewport: Int2D,
    ): DecalInstance {
        calls += PartialCall(position, tint)
        return delegate.drawPartialDecal(
            position,
            decal,
            sourcePosition,
            sourceSize,
            scale,
            tint,
            mode,
            structure,
            viewport,
        )
    }
}
