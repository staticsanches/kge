package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.engine.KotlinGameEngine
import dev.staticsanches.kge.engine.extension.KGEX

interface ExtensionsAddon<KGE : KotlinGameEngine<KGE>> {
    fun registerExtension(extensionProvider: (KGE) -> KGEX): KGE
}
