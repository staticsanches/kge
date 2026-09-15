package dev.staticsanches.kge.engine.layer

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.installGl
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.resource.LeakReporterService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

/**
 * The layer stack: it owns each layer's `Sprite` + `Decal` as one unit,
 * allocates them at the stack's screen size, selects the target layer, resizes
 * every layer in place and releases them all on close.
 */
@OptIn(KGESensitiveAPI::class)
class LayerStackTest :
    FunSpec({
        test("a stack is created with layer 0 and createLayer appends the next index") {
            installGl()
            LayerStack(8, 4).use { stack ->
                stack.size shouldBe 1
                stack[0].target.width shouldBe 8
                stack[0].target.height shouldBe 4
                stack[0].decal.sprite shouldBe stack[0].target

                stack.createLayer() shouldBe 1
                stack.createLayer() shouldBe 2

                stack.size shouldBe 3
                stack[1].target.width shouldBe 8
                stack[1].target.height shouldBe 4
                stack[2].target.width shouldBe 8
                stack[2].target.height shouldBe 4
            }
        }

        test("a layer starts hidden, un-updated, untransformed and untinted") {
            installGl()
            LayerStack(8, 4).use { stack ->
                val layer = stack[0]
                layer.show shouldBe false
                layer.update shouldBe false
                layer.offset shouldBe Float2D(0f, 0f)
                layer.scale shouldBe Float2D(1f, 1f)
                layer.tint shouldBe Colors.WHITE
                layer.customRender shouldBe null
                layer.decalInstances shouldBe emptyList()
            }
        }

        test("the target defaults to the layer at targetIndex") {
            installGl()
            LayerStack(8, 4).use { stack ->
                stack.createLayer()

                stack.targetIndex shouldBe 0
                stack.target shouldBe stack[0]
            }
        }

        test("resizeAll replaces every layer's internals and forces update") {
            installGl()
            LayerStack(8, 4).use { stack ->
                stack.createLayer()
                val firstLayer = stack[0]
                val firstTarget = stack[0].target
                val secondDecal = stack[1].decal

                stack.resizeAll(16, 10)

                stack[0] shouldBeSameInstanceAs firstLayer
                firstTarget.width shouldBe 8
                stack[0].target shouldNotBeSameInstanceAs firstTarget
                stack[0].target.width shouldBe 16
                stack[0].target.height shouldBe 10
                stack[1].decal shouldNotBeSameInstanceAs secondDecal
                stack[1].target.width shouldBe 16
                stack[0].update shouldBe true
                stack[1].update shouldBe true
                shouldThrow<IllegalStateException> { firstTarget.get(0, 0) }
                shouldThrow<IllegalStateException> { secondDecal.update() }

                stack.createLayer()
                stack[2].target.width shouldBe 16
                stack[2].target.height shouldBe 10
            }
        }

        test("reads fail fast once the stack is closed") {
            installGl()
            val stack = LayerStack(8, 4)
            stack.createLayer()

            stack.close()

            shouldThrow<IllegalStateException> { stack.size }
                .message shouldBe "LayerStack is closed"
            shouldThrow<IllegalStateException> { stack[0] }
                .message shouldBe "LayerStack is closed"
            shouldThrow<IllegalStateException> { stack.target }
                .message shouldBe "LayerStack is closed"
        }

        test("createLayer on a closed stack fails fast without allocating a layer") {
            val gl = installGl()
            val stack = LayerStack(8, 4)
            stack.close()
            val textureCreations = gl.calls.count { it.name == "createTexture" }

            val thrown = shouldThrow<IllegalStateException> { stack.createLayer() }

            gl.calls.count { it.name == "createTexture" } shouldBe textureCreations
            thrown.message shouldBe "LayerStack is closed"
        }

        test("resizeAll on a closed stack fails fast without allocating a layer") {
            val gl = installGl()
            val stack = LayerStack(8, 4)
            stack.close()
            val textureCreations = gl.calls.count { it.name == "createTexture" }

            val thrown = shouldThrow<IllegalStateException> { stack.resizeAll(16, 10) }

            gl.calls.count { it.name == "createTexture" } shouldBe textureCreations
            thrown.message shouldBe "LayerStack is closed"
        }

        test("close releases every layer's sprite and decal, idempotently and without leak reports") {
            val gl = installGl()
            val reports = mutableListOf<String>()
            LeakReporterService.override(
                object : LeakReporterService {
                    override fun report(representation: String) {
                        reports += representation
                    }
                },
            )
            val stack = LayerStack(8, 4)
            stack.createLayer()
            val targets = listOf(stack[0].target, stack[1].target)
            val decals = listOf(stack[0].decal, stack[1].decal)

            stack.close()
            stack.close()

            targets.forEach { shouldThrow<IllegalStateException> { it.get(0, 0) } }
            decals.forEach { shouldThrow<IllegalStateException> { it.update() } }
            gl.calls.count { it.name == "deleteTexture" } shouldBe 2

            targets.forEach { it.onCollectionObserved() }
            reports shouldBe emptyList()
        }
    })
