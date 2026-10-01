import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val PACKAGE = "dev.staticsanches.kge.golden"

private const val BINDER =
    "fun Pixmap.shouldMatchGolden(name: String) = " +
        "this.shouldMatchGolden(GoldenImages.byName, name)"

class GoldenImagesRenderingTest {
    @Test
    fun `emits the package the three imports the accessor and the binder`() {
        val rendered = renderGoldenImages(PACKAGE, listOf(golden("line/filled", width = 5, height = 3)))

        assertContains(rendered, "package $PACKAGE")
        assertEquals(
            listOf(
                "import dev.staticsanches.kge.image.Pixmap",
                "import dev.staticsanches.kge.testsupport.golden.GoldenImage",
                "import dev.staticsanches.kge.testsupport.golden.shouldMatchGolden",
            ),
            rendered.lines().filter { it.startsWith("import ") },
        )
        assertContains(rendered, "object GoldenImages {")
        assertContains(rendered, "val byName: Map<String, GoldenImage> =")
        assertContains(rendered, "listOf(")
        assertContains(rendered, """GoldenImage("line/filled", 5, 3, "AAAA")""")
        assertContains(rendered, ").associateBy { it.name }")
        assertContains(rendered, "/** Asserts this surface against this module's golden [name]. */")
        assertContains(rendered, BINDER)
    }

    @Test
    fun `emits a typed empty accessor when the golden set has no images`() {
        val rendered = renderGoldenImages(PACKAGE, emptyList())

        assertContains(rendered, "val byName: Map<String, GoldenImage> =")
        assertContains(rendered, "emptyList<GoldenImage>().associateBy { it.name }")
        assertContains(rendered, BINDER)
    }

    @Test
    fun `emits one entry per spec sorted by name regardless of input order`() {
        val rendered =
            renderGoldenImages(
                PACKAGE,
                listOf(
                    golden("circle/fill", width = 11, height = 11),
                    golden("blend/alpha", width = 4, height = 2),
                ),
            )

        val blend = rendered.indexOf("""GoldenImage("blend/alpha"""")
        val circle = rendered.indexOf("""GoldenImage("circle/fill"""")
        assertTrue(blend in 0 until circle)
    }

    @Test
    fun `renders byte-identically for the same specs in a different order`() {
        val alpha = golden("blend/alpha", width = 4, height = 2)
        val circle = golden("circle/fill", width = 11, height = 11)

        assertEquals(
            renderGoldenImages(PACKAGE, listOf(alpha, circle)),
            renderGoldenImages(PACKAGE, listOf(circle, alpha)),
        )
    }

    @Test
    fun `fails on a blank package name`() {
        assertFailsWith<IllegalArgumentException> {
            renderGoldenImages("  ", listOf(golden("line/filled", width = 5, height = 3)))
        }
    }

    @Test
    fun `fails on a blank golden name`() {
        assertFailsWith<IllegalArgumentException> {
            renderGoldenImages(PACKAGE, listOf(golden(" ", width = 5, height = 3)))
        }
    }

    @Test
    fun `fails on a non-positive dimension`() {
        assertFailsWith<IllegalArgumentException> {
            renderGoldenImages(PACKAGE, listOf(golden("line/filled", width = 0, height = 3)))
        }
        assertFailsWith<IllegalArgumentException> {
            renderGoldenImages(PACKAGE, listOf(golden("line/filled", width = 5, height = -1)))
        }
    }

    @Test
    fun `fails on a duplicate golden name naming the offender`() {
        val error =
            assertFailsWith<IllegalStateException> {
                renderGoldenImages(
                    PACKAGE,
                    listOf(
                        golden("line/filled", width = 5, height = 3),
                        golden("line/filled", width = 5, height = 3),
                    ),
                )
            }

        assertContains(error.message.orEmpty(), "line/filled")
    }

    @Test
    fun `fails on a malformed base64 payload`() {
        assertFailsWith<IllegalArgumentException> {
            renderGoldenImages(
                PACKAGE,
                listOf(golden("line/filled", width = 5, height = 3, rgbaBase64 = "not base64!")),
            )
        }
    }
}

private fun golden(
    name: String,
    width: Int = 1,
    height: Int = 1,
    rgbaBase64: String = "AAAA",
) = GoldenImageSpec(name = name, width = width, height = height, rgbaBase64 = rgbaBase64)
