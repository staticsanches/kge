package dev.staticsanches.kge.engine

import dev.staticsanches.kge.engine.input.RawInput
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.device.GpuDevice

/**
 * The platform window/context seam the engine loop drives.
 *
 * [makeCurrent] and [present] are the renderer's context contract; the engine
 * owns the driver's lifecycle and closes it when the loop ends.
 */
interface Driver :
    GpuDevice,
    AutoCloseable {
    /** The raw input state the platform callbacks fill; the engine latches it each frame. */
    val input: RawInput

    /** Dispenses the platform events of the current frame. */
    fun pollEvents()

    /**
     * Suspends until the platform permits the next frame. A backend whose
     * [present] already paces the loop (vsync) returns immediately.
     */
    suspend fun awaitNextFrame()

    /** The logical window size, in points/CSS pixels. */
    fun windowSize(): Int2D

    /** The physical framebuffer size, in device pixels. */
    fun framebufferSize(): Int2D

    /** Whether the platform requested the window to close. */
    fun isClosing(): Boolean

    /**
     * Clears a pending platform close request so the loop can continue; a no-op
     * where the platform has no such state. Lets [Engine.onUserDestroy] veto a
     * close request.
     */
    fun cancelClose()

    /** Destroys the window/context; idempotent. */
    override fun close()
}
