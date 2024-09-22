package dev.staticsanches.kge.resource

import dev.staticsanches.kge.extension.LoggerExtension
import org.junit.jupiter.api.extension.RegisterExtension
import java.lang.ref.WeakReference
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test for [KGELeakDetector].
 */
class KGELeakDetectorTest {
    @RegisterExtension
    val loggerExtension = LoggerExtension(KGELeakDetector::class)

    @Test
    fun checkLeakDetection() {
        fun objRepresentation(id: Int) = "LeakingResource(id=$id)"

        class LeakingResource(
            id: Int,
        ) : KGEResource {
            private val cleanable = KGELeakDetector.register(this, objRepresentation(id)) {}

            override fun close() = cleanable.clean()
        }

        LeakingResource(1)
        LeakingResource(2).close()
        LeakingResource(3)
        forceGC()

        assertEquals(
            listOf(
                KGELeakDetector.leakMessage(objRepresentation(1)),
                KGELeakDetector.leakMessage(objRepresentation(3)),
            ).sorted(),
            loggerExtension.loggingEvents
                .map { it.formattedMessage }
                .sorted(),
        )
    }

    private fun forceGC() {
        val ref = WeakReference(Any())
        while (ref.get() != null) {
            System.gc()
        }
        Thread.sleep(100)
    }
}
