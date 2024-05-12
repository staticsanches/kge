package dev.staticsanches.kge.engine

import dev.staticsanches.kge.engine.window.Window
import dev.staticsanches.kge.image.Pixel
import org.junit.jupiter.api.Timeout
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource


class SimpleKotlinGameEngineTest {

	@Test
	@Timeout(10)
	fun checkRun_GL11() =
		SimpleKotlinGameEngine().start { }

	private class SimpleKotlinGameEngine : KotlinGameEngine("Simple Test") {

		private val start = TimeSource.Monotonic.markNow()

		context(Window)
		override fun onUserUpdate(): Boolean {
			(0..<screenSize.x).forEach { x ->
				(0..<screenSize.y).forEach { y ->
					draw(x, y, Pixel.rgba(randomComponent(), randomComponent(), randomComponent()))
				}
			}
			return (TimeSource.Monotonic.markNow() - start) < 5.seconds
		}

		companion object {

			private fun randomComponent(): Int = Random.nextInt(0, 256)

		}

	}

}
