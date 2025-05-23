import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Colors.BLANK
import dev.staticsanches.kge.image.Colors.BLUE
import dev.staticsanches.kge.image.Colors.LIME
import dev.staticsanches.kge.image.Colors.ORANGE
import dev.staticsanches.kge.image.Colors.RED
import dev.staticsanches.kge.image.Colors.YELLOW
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.extension.loadPNG
import dev.staticsanches.kge.image.extension.loadPNGFromBase64
import js.promise.catch
import web.dom.document

fun main() {
    document.body.style.backgroundColor = Colors.CADET_BLUE.toString()
    console.log("KGE JS")

    val xmas5x5Pixels =
        listOf(
            BLUE, BLUE, YELLOW, BLUE, BLUE,
            BLUE, RED, LIME, LIME, BLUE,
            BLUE, LIME, LIME, RED, BLUE,
            LIME, RED, LIME, LIME, RED,
            BLANK, BLANK, ORANGE, BLANK, BLANK,
        )

    Sprite
        .loadPNG("xmas_5x5.png")
        .then { sprite ->
            sprite.use {
                console.log(xmas5x5Pixels == sprite.toList())
            }
        }.catch(console::error)

    Sprite
        .loadPNGFromBase64(
            "iVBORw0KGgoAAAANSUhEUgAAAAUAAAAFCAYAAACNbyblAAAAPUlEQVR4XmNkYPj/n4EBRDIyMDKCmQxMYAEGoACYwQgiIYJQBQz" +
                "/IWJAJf+B+oEcdAmwlv9LGSAGMjAwAACtexCnoHY4qwAAAABJRU5ErkJggg==",
        ).then { sprite ->
            sprite.use {
                console.log(xmas5x5Pixels == sprite.toList())
            }
        }.catch(console::error)
}
