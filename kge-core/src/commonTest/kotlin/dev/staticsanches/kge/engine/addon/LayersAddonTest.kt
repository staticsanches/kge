package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.engine.HasLayers
import dev.staticsanches.kge.engine.installGl
import dev.staticsanches.kge.engine.layer.LayerStack
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The layers addon: a default method that appends the next screen-sized layer
 * to the engine's stack.
 */
class LayersAddonTest :
    FunSpec({
        test("createLayer returns the next index and grows the stack with a screen-sized layer") {
            installGl()
            LayerStack(8, 4).use { stack ->
                val host = LayersAddonHost(stack)

                host.layers.size shouldBe 1
                host.createLayer() shouldBe 1
                host.createLayer() shouldBe 2

                host.layers.size shouldBe 3
                host.layers[0].target.width shouldBe 8
                host.layers[0].target.height shouldBe 4
                host.layers[1].target.width shouldBe 8
                host.layers[1].target.height shouldBe 4
                host.layers[2].target.width shouldBe 8
                host.layers[2].target.height shouldBe 4
            }
        }
    })

private class LayersAddonHost(
    override val layers: LayerStack,
) : HasLayers,
    LayersAddon
