package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.engine.KotlinGameEngine
import dev.staticsanches.kge.engine.extension.KGEX

interface ExtensionsAddon {
    fun registerExtension(extensionProvider: (KotlinGameEngine) -> KGEX)
}
