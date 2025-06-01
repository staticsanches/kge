package dev.staticsanches.kge.engine

import dev.staticsanches.kge.engine.addon.CallbacksAddon
import dev.staticsanches.kge.engine.addon.ClearAddon
import dev.staticsanches.kge.engine.addon.DrawAddon
import dev.staticsanches.kge.engine.addon.DrawCircleAddon
import dev.staticsanches.kge.engine.addon.DrawDecalAddon
import dev.staticsanches.kge.engine.addon.DrawLineAddon
import dev.staticsanches.kge.engine.addon.DrawPartialDecalAddon
import dev.staticsanches.kge.engine.addon.DrawRectAddon
import dev.staticsanches.kge.engine.addon.DrawSpriteAddon
import dev.staticsanches.kge.engine.addon.DrawStringAddon
import dev.staticsanches.kge.engine.addon.DrawTriangleAddon
import dev.staticsanches.kge.engine.addon.FillCircleAddon
import dev.staticsanches.kge.engine.addon.FillRectAddon
import dev.staticsanches.kge.engine.addon.FillTriangleAddon
import dev.staticsanches.kge.engine.addon.LayersAddon

expect abstract class KotlinGameEngine :
    CallbacksAddon,
    ClearAddon,
    DrawAddon,
    DrawCircleAddon,
    DrawDecalAddon,
    DrawLineAddon,
    DrawPartialDecalAddon,
    DrawRectAddon,
    DrawSpriteAddon,
    DrawStringAddon,
    DrawTriangleAddon,
    FillCircleAddon,
    FillRectAddon,
    FillTriangleAddon,
    LayersAddon {
    final override val kgeWindow: Window
}
