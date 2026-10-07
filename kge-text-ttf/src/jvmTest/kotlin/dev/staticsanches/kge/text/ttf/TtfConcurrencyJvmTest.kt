@file:OptIn(KGESensitiveAPI::class)

package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.gl.service.GLService
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.testsupport.engine.installGl
import dev.staticsanches.kge.text.KGEFont
import dev.staticsanches.kge.text.fontPx
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val RACERS = 8
private const val SIZE_PX = 16
private const val OTHER_SIZE_PX = 32
private const val TAB_SIZE = 4

/** The construction window the race needs: long enough that every racer queues on the cache lock. */
private const val BUILD_MILLIS = 50L

/**
 * The JVM single-flight race: real parallel callers on [Dispatchers.Default]
 * converge on one construction on success and publish no entry on failure.
 */
class TtfConcurrencyJvmTest :
    FunSpec({
        test("N racing callers converge on success and leave no entry behind on failure") {
            val constructions = AtomicInteger(0)
            val failure = IllegalStateException("native construction failed")
            val failing = AtomicBoolean(false)
            val gl = installGl()
            // one scope per racer: the contract keeps ResourceScope single-threaded
            val scopes = (1..RACERS).map { ResourceScope() }
            try {
                val family =
                    createTtfFamily(scopes.first(), listOf(robotoFontBytes())) { payload, coordinates ->
                        constructions.incrementAndGet()
                        delay(BUILD_MILLIS)
                        if (failing.get()) throw failure
                        createNativeFace(payload, coordinates)
                    }
                val face = family.defaultFace

                // success leg: the racers share one construction and one GPU carrier
                val leases = race(scopes) { scope -> face.font(scope, SIZE_PX.fontPx) }
                constructions.get() shouldBe 1
                leases.forEach { it.drawDecal("A") }
                gl.calls.count { it.name == "createTexture" } shouldBe 1

                // failure leg: every racer observes the injected failure, nothing is published
                failing.set(true)
                val beforeFailures = constructions.get()
                val observed =
                    race(scopes) { scope ->
                        runCatching { face.font(scope, OTHER_SIZE_PX.fontPx) }.exceptionOrNull()
                    }
                observed.forEach { it shouldBeSameInstanceAs failure }
                val afterFailures = constructions.get()
                afterFailures shouldBeGreaterThan beforeFailures

                // no negative cache and no partial entry: a healthy later request rebuilds and draws
                failing.set(false)
                val recovered = face.font(scopes.first(), OTHER_SIZE_PX.fontPx)
                constructions.get() shouldBeGreaterThan afterFailures
                recovered.measureText("A", TAB_SIZE).x shouldBeGreaterThan 0
                recovered.drawDecal("A")
            } finally {
                // the close releases the GPU carrier, so the recording GL must stay installed
                try {
                    scopes.forEach { it.close() }
                } finally {
                    GLService.override(GLService.original)
                }
            }
        }
    })

/** Releases one racer per scope on [Dispatchers.Default] together, each running [block]. */
private suspend fun <T> race(
    scopes: List<ResourceScope>,
    block: suspend (ResourceScope) -> T,
): List<T> {
    val arrivals = AtomicInteger(0)
    val go = CompletableDeferred<Unit>()
    return coroutineScope {
        val jobs =
            scopes.map { scope ->
                async(Dispatchers.Default) {
                    arrivals.incrementAndGet()
                    go.await()
                    block(scope)
                }
            }
        while (arrivals.get() < scopes.size) delay(1)
        go.complete(Unit)
        jobs.awaitAll()
    }
}

/** Draws "A" through the decal path, building the shared configuration's atlas and carrier. */
private fun KGEFont.drawDecal(text: String) {
    drawTextDecal(
        Float2D(2f, 3f),
        text,
        Colors.WHITE,
        Float2D(1f, 1f),
        TAB_SIZE,
        Int2D(30, 24),
        Decal.Mode.NORMAL,
        Decal.Structure.FAN,
        {},
    )
}
