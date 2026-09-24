package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.resource.ResourceScope

/** The TrueType text capability: it adopts a loaded font into a caller scope. */
interface TtfTextService : KGEOverridable {
    /** Adopts [font] into [scope]: the scope owns and closes it. */
    fun createResources(
        scope: ResourceScope,
        font: Font,
    )

    @OptIn(KGESensitiveAPI::class)
    companion object :
        KGEOverridable.Proxy<TtfTextService>(TtfTextService::class, TtfTextServiceDefault),
        TtfTextService {
        override fun createResources(
            scope: ResourceScope,
            font: Font,
        ) = delegate.createResources(scope, font)
    }
}

private object TtfTextServiceDefault : TtfTextService {
    override fun createResources(
        scope: ResourceScope,
        font: Font,
    ) {
        scope.register(FontKey(), font)
    }
}

/** A fresh key per adoption; identity matching lets one scope own several fonts. */
private class FontKey : ResourceScope.Key<Font>
