package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.resource.IntResource
import dev.staticsanches.kge.resource.KGECleanAction

internal typealias Texture = IntResource

internal fun Texture(
    name: String,
    filtered: Boolean,
    clamp: Boolean,
): Texture =
    Texture(
        name,
        { Renderer.createTexture(filtered, clamp) },
        ::DeleteTextureAction,
    )

@JvmInline
private value class DeleteTextureAction(
    val id: Int,
) : KGECleanAction {
    override fun invoke() = Renderer.deleteTexture(id)
}
