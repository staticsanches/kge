package dev.staticsanches.kge.engine

import kotlin.time.Duration

private val testWindowConfig = WindowConfig(screenWidth = 320, screenHeight = 240)

/** An [Engine] whose callbacks are scripted by lambdas and counted. */
class ScriptedEngine(
    config: WindowConfig = testWindowConfig,
    private val onCreate: suspend (ScriptedEngine) -> Boolean = { true },
    private val onUpdate: suspend (ScriptedEngine, Duration) -> Boolean = { _, _ -> true },
    private val onDestroy: suspend (ScriptedEngine) -> Boolean = { true },
    private val onCommand: suspend (String) -> Boolean = { false },
    private val onComplete: suspend (String) -> Unit = {},
) : Engine(config) {
    var createCount = 0
        private set
    var destroyCount = 0
        private set
    val updateElapsed = mutableListOf<Duration>()

    override suspend fun onUserCreate(): Boolean {
        createCount++
        return onCreate(this)
    }

    override suspend fun onUserUpdate(elapsed: Duration): Boolean {
        updateElapsed += elapsed
        return onUpdate(this, elapsed)
    }

    override suspend fun onUserDestroy(): Boolean {
        destroyCount++
        return onDestroy(this)
    }

    override suspend fun onConsoleCommand(command: String): Boolean = onCommand(command)

    override suspend fun onTextEntryComplete(text: String) = onComplete(text)
}
