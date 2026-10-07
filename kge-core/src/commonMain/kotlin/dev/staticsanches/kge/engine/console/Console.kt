package dev.staticsanches.kge.engine.console

import dev.staticsanches.kge.engine.input.InputState
import dev.staticsanches.kge.engine.input.KeyboardKey
import dev.staticsanches.kge.engine.input.TextInputEvent
import dev.staticsanches.kge.engine.layer.LayerStack
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.renderer.decal.service.DrawPolygonDecalService
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.resource.letClosingIfFailed
import dev.staticsanches.kge.text.KGEFont

/**
 * The built-in console over [entry]: a showing flag, a transcript fed by
 * [write] and a command history. Every mutator is engine-thread only.
 */
class Console internal constructor(
    private val entry: TextEntry,
    private val input: InputState,
    private val onCommand: suspend (String) -> Boolean,
    private val onComplete: suspend (String) -> Unit,
    private val requireEngineThread: () -> Unit = {},
) {
    /** Whether the console is showing. */
    var isShowing: Boolean = false
        private set

    /** Whether the frame time is suspended: showing with the show's suspend flag. */
    val isTimeSuspended: Boolean get() = isShowing && suspendTime

    private var suspendTime = false
    private var exitKey: KeyboardKey? = null
    private var grid = Int2D(0, 0)
    private val lines = mutableListOf<String>()
    private var outputColumn = 0
    private var outputLine = 0
    private val backlog = StringBuilder()
    private val history = mutableListOf<String>()
    private var historyIndex = 0
    private var fillOwner: ResourceScope? = null
    private var fill: Decal? = null

    /** Shows the console and re-enables the entry; a second call while showing changes nothing. */
    fun show(
        exitKey: KeyboardKey,
        suspendTime: Boolean = true,
    ) {
        requireEngineThread()
        if (isShowing) return
        isShowing = true
        this.suspendTime = suspendTime
        this.exitKey = exitKey
        entry.enable()
        // Forced release, as olc: the exit key's own press must not close the
        // console on the frame that opened it.
        input.keyHeld[exitKey.ordinal] = false
        input.keyPressed[exitKey.ordinal] = false
        input.keyReleased[exitKey.ordinal] = true
    }

    /** Hides the console and disables the entry; the transcript and the history survive. */
    fun hide() {
        requireEngineThread()
        if (!isShowing) return
        isShowing = false
        suspendTime = false
        entry.disable()
    }

    /** Empties the transcript; the next frame re-sizes it to the grid. */
    fun clear() {
        requireEngineThread()
        lines.clear()
    }

    /** Appends [text] to the console's output; a hidden console holds it in a bounded backlog. */
    fun write(text: String) {
        requireEngineThread()
        backlog.append(text)
        val excess = backlog.length - OUTPUT_BACKLOG_LIMIT
        if (excess > 0) backlog.deleteRange(0, excess)
    }

    /** The console's per-frame part: the exit key closes it, else the grid and the output advance. */
    internal fun update(viewport: Int2D) {
        requireEngineThread()
        val key = exitKey
        if (key != null && input.key(key).pressed) {
            hide()
            return
        }
        grid = Int2D(viewport.x / 8 - 2, viewport.y / 16 - 4)
        if (grid.y != lines.size) resetOutput()
        consumeOutput()
    }

    /** Queues the console's frame on layer 0: the shadow, the transcript, the cursor and the prompt. */
    internal fun draw(
        viewport: Int2D,
        screenSize: Int2D,
        scope: ResourceScope,
        layers: LayerStack,
        font: KGEFont,
    ) {
        requireEngineThread()
        val scale = Float2D(screenSize.x.toFloat() / viewport.x, 2f * screenSize.y / viewport.y)
        val fill = fillDecal(scope)
        val queue: (DecalInstance) -> Unit = { layers[0].decalInstances.add(it) }

        val screenWidth = screenSize.x.toFloat()
        val screenHeight = screenSize.y.toFloat()
        queue(
            DrawPolygonDecalService.drawPolygonDecal(
                decal = fill,
                pos =
                    listOf(
                        Float2D(0f, 0f),
                        Float2D(0f, screenHeight),
                        Float2D(screenWidth, screenHeight),
                        Float2D(screenWidth, 0f),
                    ),
                uv = UV_CORNERS,
                tint = SHADOW_TINTS,
                mode = Decal.Mode.NORMAL,
                structure = Decal.Structure.FAN,
                viewport = screenSize,
            ),
        )

        for (index in lines.indices) {
            font.drawTextDecal(
                position = Float2D(1f, 1f + index) * scale * CELL_SIZE,
                text = lines[index],
                color = Colors.WHITE,
                scale = scale,
                tabSizeInSpaces = TAB_SIZE,
                screenSize = screenSize,
                decalMode = Decal.Mode.NORMAL,
                decalStructure = Decal.Structure.FAN,
                decalInstanceCollector = queue,
            )
        }

        val cursor =
            Float2D((entry.cursor + 2) * scale.x * CELL_SIZE, grid.y * scale.y * CELL_SIZE)
        queue(
            DrawPolygonDecalService.drawPolygonDecal(
                decal = fill,
                pos =
                    listOf(
                        cursor,
                        Float2D(cursor.x, cursor.y + CELL_SIZE * scale.y),
                        Float2D(cursor.x + CELL_SIZE * scale.x, cursor.y + CELL_SIZE * scale.y),
                        Float2D(cursor.x + CELL_SIZE * scale.x, cursor.y),
                    ),
                uv = UV_CORNERS,
                tint = CURSOR_TINTS,
                mode = Decal.Mode.NORMAL,
                structure = Decal.Structure.FAN,
                viewport = screenSize,
            ),
        )

        font.drawTextDecal(
            position = Float2D(1f, grid.y.toFloat()) * scale * CELL_SIZE,
            text = ">" + entry.text,
            color = Colors.YELLOW,
            scale = scale,
            tabSizeInSpaces = TAB_SIZE,
            screenSize = screenSize,
            decalMode = Decal.Mode.NORMAL,
            decalStructure = Decal.Structure.FAN,
            decalInstanceCollector = queue,
        )
    }

    /** The console's 1x1 white fill texture, created once per run and released with [scope]. */
    private fun fillDecal(scope: ResourceScope): Decal {
        val cached = fill
        if (cached != null && fillOwner === scope) return cached
        val sprite = SpriteService.create(1, 1, Pixmap.SampleMode.NORMAL, FILL_SPRITE_NAME)
        sprite.clear(Colors.WHITE)
        val registered = sprite.letClosingIfFailed { scope.register(FillSpriteKey(), it) }
        val decal =
            Decal(registered, Decal.Filter.NEAREST, Decal.Wrap.CLAMP_TO_EDGE)
                .letClosingIfFailed { scope.register(FillDecalKey(), it) }
        fillOwner = scope
        fill = decal
        return decal
    }

    /** Applies one console-owned event; the entry's characters and edits are ignored. */
    internal suspend fun handleEdit(event: TextInputEvent) {
        requireEngineThread()
        if (!isShowing) {
            if (event != TextInputEvent.Edit.ENTER) return
            onComplete(entry.text)
            entry.disable()
            return
        }
        when (event) {
            TextInputEvent.Edit.UP -> historyPrevious()
            TextInputEvent.Edit.DOWN -> historyNext()
            TextInputEvent.Edit.ENTER -> submit()
            else -> Unit
        }
    }

    /** The transcript lines and the grid, as one snapshot; the console's buffer pins read it. */
    internal fun bufferSnapshot(): Pair<List<String>, Int2D> = lines.toList() to grid

    private fun resetOutput() {
        outputColumn = 0
        outputLine = 0
        lines.clear()
        repeat(grid.y.coerceAtLeast(0)) { lines += "" }
    }

    private fun consumeOutput() {
        if (!isShowing || backlog.isEmpty()) return
        backlog.forEach { typeCharacter(it) }
        backlog.clear()
    }

    private fun typeCharacter(character: Char) {
        if (grid.y <= 0) return
        if (character.code in 32..126) {
            lines[outputLine] += character
            outputColumn++
        }
        if (character == '\n' || outputColumn >= grid.x) {
            outputLine++
            outputColumn = 0
        }
        if (outputLine >= grid.y) {
            outputLine = grid.y - 1
            for (index in 1 until grid.y) lines[index - 1] = lines[index]
            lines[outputLine] = ""
        }
    }

    private suspend fun submit() {
        write(">" + entry.text + "\n")
        if (onCommand(entry.text)) {
            history += entry.text
            historyIndex = history.size
        }
        entry.enable()
    }

    private fun historyPrevious() {
        if (history.isEmpty()) return
        if (historyIndex > 0) historyIndex--
        entry.enable(history[historyIndex])
    }

    private fun historyNext() {
        if (historyIndex >= history.size) return
        historyIndex++
        if (historyIndex < history.size) entry.enable(history[historyIndex]) else entry.enable()
    }

    private companion object {
        /** The hidden console's output cap, oldest dropped first. */
        const val OUTPUT_BACKLOG_LIMIT = 4096
    }
}

/** The console's 8x16 cell, in art pixels. */
private const val CELL_SIZE = 8f

private const val TAB_SIZE = 4

private const val FILL_SPRITE_NAME = "console fill"

/** The four decal corners of a fill quad, in the TL, BL, BR, TR vertex order. */
private val UV_CORNERS =
    listOf(Float2D(0f, 0f), Float2D(0f, 1f), Float2D(1f, 1f), Float2D(1f, 0f))

/** olc's shadow colors, truncating `PixelF(0, 0, 0.5, 0.5)` and `PixelF(0, 0, 0.25, 0.5)`. */
private val SHADOW_TINTS =
    listOf(
        Pixel.rgba(0x00007F7Fu),
        Pixel.rgba(0x00003F7Fu),
        Pixel.rgba(0x00003F7Fu),
        Pixel.rgba(0x00003F7Fu),
    )

private val CURSOR_TINTS = List(4) { Colors.DARK_CYAN }

private class FillSpriteKey : ResourceScope.Key<Sprite>

private class FillDecalKey : ResourceScope.Key<Decal>
