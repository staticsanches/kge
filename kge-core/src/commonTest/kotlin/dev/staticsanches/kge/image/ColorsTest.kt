package dev.staticsanches.kge.image

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Pins [Colors] to the CSS Color Module Level 4 §6.1 table (values regenerated from
 * the spec, not copied from main). Generated fixture — source: spec extraction.
 */
class ColorsTest :
    FunSpec({
        context("named colors (CSS Color 4 §6.1 oracle)") {
            test("every named color constant matches the spec hex (RRGGBB, alpha FF)") {
                listOf(
                    Colors.ALICE_BLUE to "#F0F8FFFF",
                    Colors.ANTIQUE_WHITE to "#FAEBD7FF",
                    Colors.AQUA to "#00FFFFFF",
                    Colors.AQUAMARINE to "#7FFFD4FF",
                    Colors.AZURE to "#F0FFFFFF",
                    Colors.BEIGE to "#F5F5DCFF",
                    Colors.BISQUE to "#FFE4C4FF",
                    Colors.BLACK to "#000000FF",
                    Colors.BLANCHED_ALMOND to "#FFEBCDFF",
                    Colors.BLUE to "#0000FFFF",
                    Colors.BLUE_VIOLET to "#8A2BE2FF",
                    Colors.BROWN to "#A52A2AFF",
                    Colors.BURLY_WOOD to "#DEB887FF",
                    Colors.CADET_BLUE to "#5F9EA0FF",
                    Colors.CHARTREUSE to "#7FFF00FF",
                    Colors.CHOCOLATE to "#D2691EFF",
                    Colors.CORAL to "#FF7F50FF",
                    Colors.CORNFLOWER_BLUE to "#6495EDFF",
                    Colors.CORNSILK to "#FFF8DCFF",
                    Colors.CRIMSON to "#DC143CFF",
                    Colors.CYAN to "#00FFFFFF",
                    Colors.DARK_BLUE to "#00008BFF",
                    Colors.DARK_CYAN to "#008B8BFF",
                    Colors.DARK_GOLDENROD to "#B8860BFF",
                    Colors.DARK_GRAY to "#A9A9A9FF",
                    Colors.DARK_GREEN to "#006400FF",
                    Colors.DARK_GREY to "#A9A9A9FF",
                    Colors.DARK_KHAKI to "#BDB76BFF",
                    Colors.DARK_MAGENTA to "#8B008BFF",
                    Colors.DARK_OLIVE_GREEN to "#556B2FFF",
                    Colors.DARK_ORANGE to "#FF8C00FF",
                    Colors.DARK_ORCHID to "#9932CCFF",
                    Colors.DARK_RED to "#8B0000FF",
                    Colors.DARK_SALMON to "#E9967AFF",
                    Colors.DARK_SEA_GREEN to "#8FBC8FFF",
                    Colors.DARK_SLATE_BLUE to "#483D8BFF",
                    Colors.DARK_SLATE_GRAY to "#2F4F4FFF",
                    Colors.DARK_SLATE_GREY to "#2F4F4FFF",
                    Colors.DARK_TURQUOISE to "#00CED1FF",
                    Colors.DARK_VIOLET to "#9400D3FF",
                    Colors.DEEP_PINK to "#FF1493FF",
                    Colors.DEEP_SKY_BLUE to "#00BFFFFF",
                    Colors.DIM_GRAY to "#696969FF",
                    Colors.DIM_GREY to "#696969FF",
                    Colors.DODGER_BLUE to "#1E90FFFF",
                    Colors.FIREBRICK to "#B22222FF",
                    Colors.FLORAL_WHITE to "#FFFAF0FF",
                    Colors.FOREST_GREEN to "#228B22FF",
                    Colors.FUCHSIA to "#FF00FFFF",
                    Colors.GAINSBORO to "#DCDCDCFF",
                    Colors.GHOST_WHITE to "#F8F8FFFF",
                    Colors.GOLD to "#FFD700FF",
                    Colors.GOLDENROD to "#DAA520FF",
                    Colors.GRAY to "#808080FF",
                    Colors.GREEN to "#008000FF",
                    Colors.GREEN_YELLOW to "#ADFF2FFF",
                    Colors.GREY to "#808080FF",
                    Colors.HONEYDEW to "#F0FFF0FF",
                    Colors.HOT_PINK to "#FF69B4FF",
                    Colors.INDIAN_RED to "#CD5C5CFF",
                    Colors.INDIGO to "#4B0082FF",
                    Colors.IVORY to "#FFFFF0FF",
                    Colors.KHAKI to "#F0E68CFF",
                    Colors.LAVENDER to "#E6E6FAFF",
                    Colors.LAVENDER_BLUSH to "#FFF0F5FF",
                    Colors.LAWN_GREEN to "#7CFC00FF",
                    Colors.LEMON_CHIFFON to "#FFFACDFF",
                    Colors.LIGHT_BLUE to "#ADD8E6FF",
                    Colors.LIGHT_CORAL to "#F08080FF",
                    Colors.LIGHT_CYAN to "#E0FFFFFF",
                    Colors.LIGHT_GOLDENROD_YELLOW to "#FAFAD2FF",
                    Colors.LIGHT_GRAY to "#D3D3D3FF",
                    Colors.LIGHT_GREEN to "#90EE90FF",
                    Colors.LIGHT_GREY to "#D3D3D3FF",
                    Colors.LIGHT_PINK to "#FFB6C1FF",
                    Colors.LIGHT_SALMON to "#FFA07AFF",
                    Colors.LIGHT_SEA_GREEN to "#20B2AAFF",
                    Colors.LIGHT_SKY_BLUE to "#87CEFAFF",
                    Colors.LIGHT_SLATE_GRAY to "#778899FF",
                    Colors.LIGHT_SLATE_GREY to "#778899FF",
                    Colors.LIGHT_STEEL_BLUE to "#B0C4DEFF",
                    Colors.LIGHT_YELLOW to "#FFFFE0FF",
                    Colors.LIME to "#00FF00FF",
                    Colors.LIME_GREEN to "#32CD32FF",
                    Colors.LINEN to "#FAF0E6FF",
                    Colors.MAGENTA to "#FF00FFFF",
                    Colors.MAROON to "#800000FF",
                    Colors.MEDIUM_AQUAMARINE to "#66CDAAFF",
                    Colors.MEDIUM_BLUE to "#0000CDFF",
                    Colors.MEDIUM_ORCHID to "#BA55D3FF",
                    Colors.MEDIUM_PURPLE to "#9370DBFF",
                    Colors.MEDIUM_SEA_GREEN to "#3CB371FF",
                    Colors.MEDIUM_SLATE_BLUE to "#7B68EEFF",
                    Colors.MEDIUM_SPRING_GREEN to "#00FA9AFF",
                    Colors.MEDIUM_TURQUOISE to "#48D1CCFF",
                    Colors.MEDIUM_VIOLET_RED to "#C71585FF",
                    Colors.MIDNIGHT_BLUE to "#191970FF",
                    Colors.MINT_CREAM to "#F5FFFAFF",
                    Colors.MISTY_ROSE to "#FFE4E1FF",
                    Colors.MOCCASIN to "#FFE4B5FF",
                    Colors.NAVAJO_WHITE to "#FFDEADFF",
                    Colors.NAVY to "#000080FF",
                    Colors.OLD_LACE to "#FDF5E6FF",
                    Colors.OLIVE to "#808000FF",
                    Colors.OLIVE_DRAB to "#6B8E23FF",
                    Colors.ORANGE to "#FFA500FF",
                    Colors.ORANGE_RED to "#FF4500FF",
                    Colors.ORCHID to "#DA70D6FF",
                    Colors.PALE_GOLDENROD to "#EEE8AAFF",
                    Colors.PALE_GREEN to "#98FB98FF",
                    Colors.PALE_TURQUOISE to "#AFEEEEFF",
                    Colors.PALE_VIOLET_RED to "#DB7093FF",
                    Colors.PAPAYA_WHIP to "#FFEFD5FF",
                    Colors.PEACH_PUFF to "#FFDAB9FF",
                    Colors.PERU to "#CD853FFF",
                    Colors.PINK to "#FFC0CBFF",
                    Colors.PLUM to "#DDA0DDFF",
                    Colors.POWDER_BLUE to "#B0E0E6FF",
                    Colors.PURPLE to "#800080FF",
                    Colors.REBECCAPURPLE to "#663399FF",
                    Colors.RED to "#FF0000FF",
                    Colors.ROSY_BROWN to "#BC8F8FFF",
                    Colors.ROYAL_BLUE to "#4169E1FF",
                    Colors.SADDLE_BROWN to "#8B4513FF",
                    Colors.SALMON to "#FA8072FF",
                    Colors.SANDY_BROWN to "#F4A460FF",
                    Colors.SEA_GREEN to "#2E8B57FF",
                    Colors.SEASHELL to "#FFF5EEFF",
                    Colors.SIENNA to "#A0522DFF",
                    Colors.SILVER to "#C0C0C0FF",
                    Colors.SKY_BLUE to "#87CEEBFF",
                    Colors.SLATE_BLUE to "#6A5ACDFF",
                    Colors.SLATE_GRAY to "#708090FF",
                    Colors.SLATE_GREY to "#708090FF",
                    Colors.SNOW to "#FFFAFAFF",
                    Colors.SPRING_GREEN to "#00FF7FFF",
                    Colors.STEEL_BLUE to "#4682B4FF",
                    Colors.TAN to "#D2B48CFF",
                    Colors.TEAL to "#008080FF",
                    Colors.THISTLE to "#D8BFD8FF",
                    Colors.TOMATO to "#FF6347FF",
                    Colors.TURQUOISE to "#40E0D0FF",
                    Colors.VIOLET to "#EE82EEFF",
                    Colors.WHEAT to "#F5DEB3FF",
                    Colors.WHITE to "#FFFFFFFF",
                    Colors.WHITE_SMOKE to "#F5F5F5FF",
                    Colors.YELLOW to "#FFFF00FF",
                    Colors.YELLOW_GREEN to "#9ACD32FF",
                ).forEach { (pixel, hex) -> pixel.toString() shouldBe hex }
            }

            test("the 9 alias pairs share values") {
                Colors.GRAY shouldBe Colors.GREY
                Colors.AQUA shouldBe Colors.CYAN
                Colors.MAGENTA shouldBe Colors.FUCHSIA
                Colors.DARK_GRAY shouldBe Colors.DARK_GREY
                Colors.DIM_GRAY shouldBe Colors.DIM_GREY
                Colors.DARK_SLATE_GRAY shouldBe Colors.DARK_SLATE_GREY
                Colors.LIGHT_GRAY shouldBe Colors.LIGHT_GREY
                Colors.LIGHT_SLATE_GRAY shouldBe Colors.LIGHT_SLATE_GREY
                Colors.SLATE_GRAY shouldBe Colors.SLATE_GREY
            }

            test("green and lime keep the spec distinction") {
                Colors.GREEN.toString() shouldBe "#008000FF"
                Colors.LIME.toString() shouldBe "#00FF00FF"
            }

            test("TRANSPARENT is fully transparent black") {
                Colors.TRANSPARENT.r shouldBe 0
                Colors.TRANSPARENT.g shouldBe 0
                Colors.TRANSPARENT.b shouldBe 0
                Colors.TRANSPARENT.a shouldBe 0
                Colors.TRANSPARENT.toString() shouldBe "#00000000"
            }
        }
    })
