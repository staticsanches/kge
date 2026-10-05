package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.text.KGEFont
import kotlin.io.encoding.Base64

/**
 * Loads payloads into one family; payload order is face order and the first
 * payload is the default face. A bad, mixed or repeated payload publishes none.
 */
interface KGETtfFontService : KGEOverridable {
    /** Builds a family into [scope], which owns and closes it. */
    @KGESensitiveAPI
    suspend fun createResources(
        scope: ResourceScope,
        vararg bytes: ByteArray,
    ): KGEFont.Family

    /** The base64-decoded form of the payload overload. */
    @KGESensitiveAPI
    suspend fun createResources(
        scope: ResourceScope,
        vararg base64: List<String>,
    ): KGEFont.Family

    @OptIn(KGESensitiveAPI::class)
    companion object :
        KGEOverridable.Proxy<KGETtfFontService>(KGETtfFontService::class, Default),
        KGETtfFontService {
        override suspend fun createResources(
            scope: ResourceScope,
            vararg bytes: ByteArray,
        ): KGEFont.Family = delegate.createResources(scope, *bytes)

        override suspend fun createResources(
            scope: ResourceScope,
            vararg base64: List<String>,
        ): KGEFont.Family = delegate.createResources(scope, *base64)
    }
}

@OptIn(KGESensitiveAPI::class)
private object Default : KGETtfFontService {
    override suspend fun createResources(
        scope: ResourceScope,
        vararg bytes: ByteArray,
    ): KGEFont.Family = createTtfFamily(scope, bytes.asList())

    override suspend fun createResources(
        scope: ResourceScope,
        vararg base64: List<String>,
    ): KGEFont.Family = createTtfFamily(scope, base64.map { Base64.decode(it.joinToString("")) })
}
