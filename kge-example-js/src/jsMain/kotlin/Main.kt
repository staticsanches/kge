import dev.staticsaches.knes.KNES
import web.dom.document

fun main() {
    KNES().run(document.getElementById("canvas-holder")!!)
}
