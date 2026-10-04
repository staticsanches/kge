import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EmbeddedResourcesTest {
    @Test
    fun `renders one object per family with provenance constants and a chunked payload`() {
        val rendered =
            renderEmbeddedFonts(
                packageName = "dev.staticsanches.kge.font.roboto",
                families = listOf(embeddedFamily(accessorName = "Roboto", family = "Roboto", version = "3.015")),
                chunkSize = 4,
            )

        assertContains(rendered, "package dev.staticsanches.kge.font.roboto")
        assertContains(rendered, "import kotlinx.collections.immutable.persistentListOf")
        assertContains(rendered, "object Roboto {")
        assertContains(rendered, "const val FAMILY: String = \"Roboto\"")
        assertContains(rendered, "const val VERSION: String = \"3.015\"")
        assertContains(rendered, "const val LICENSE_ID: String = \"OFL-1.1\"")
        assertContains(rendered, "const val SOURCE: String = \"https://example.test/Roboto\"")
        assertContains(rendered, "val variableFont: List<String>")
        assertContains(rendered, "persistentListOf(")
        assertContains(rendered, "\"QUJD\",")
        assertContains(rendered, "\"RA==\",")
        assertContains(rendered, "val licenseText: String = \"license of Roboto\"")
        assertContains(rendered, "fonts/Roboto.ttf")
        assertContains(rendered, "4 B")
        assertContains(rendered, "sha-Roboto")
        assertContains(rendered, "lsha-Roboto")
    }

    @Test
    fun `emits one member per payload in manifest order with its own provenance and chunks`() {
        val rendered =
            renderEmbeddedFonts(
                packageName = "p",
                families =
                    listOf(
                        embeddedFamily(
                            accessorName = "Roboto",
                            fonts =
                                listOf(
                                    embeddedFont(
                                        member = "variableFont",
                                        path = "fonts/Roboto.ttf",
                                        bytes = 4,
                                        sha256 = "sha-roman",
                                        base64 = "QUJDRA==",
                                    ),
                                    embeddedFont(
                                        member = "italicFont",
                                        path = "fonts/Roboto-Italic.ttf",
                                        bytes = 7,
                                        sha256 = "sha-italic",
                                        base64 = "RUZHSEk=",
                                    ),
                                ),
                        ),
                    ),
                chunkSize = 4,
            )

        assertContains(rendered, "val variableFont: List<String>")
        assertContains(rendered, "val italicFont: List<String>")
        assertTrue(rendered.indexOf("val variableFont") < rendered.indexOf("val italicFont"))
        assertContains(rendered, "fonts/Roboto.ttf - 4 B - sha256 sha-roman")
        assertContains(rendered, "fonts/Roboto-Italic.ttf - 7 B - sha256 sha-italic")
        assertTrue(rendered.indexOf("sha-roman") < rendered.indexOf("sha-italic"))
        assertTrue(rendered.indexOf("sha-italic") < rendered.indexOf("lsha-Roboto"))
        assertContains(rendered, "\"QUJD\",")
        assertContains(rendered, "\"RA==\",")
        assertContains(rendered, "\"RUZH\",")
        assertContains(rendered, "\"SEk=\",")
    }

    @Test
    fun `chunkBase64 splits on the seam and keeps the remainder last`() {
        assertEquals(listOf("abcd", "efgh", "ij"), chunkBase64("abcdefghij", 4))
        assertEquals(listOf("abcde", "fghij"), chunkBase64("abcdefghij", 5))
    }

    @Test
    fun `renders literal escaping for backslash quote dollar and control characters`() {
        val rendered =
            renderEmbeddedFonts(
                packageName = "p",
                families = listOf(embeddedFamily(accessorName = "F", licenseText = "q\"b\\d\$e\nf\rg\th\u0001i")),
                chunkSize = 4,
            )

        assertContains(rendered, "\"q\\\"b\\\\d\\\$e\\nf\\rg\\th\\u0001i\"")
    }

    @Test
    fun `emits families sorted by accessor name regardless of input order`() {
        val roboto = embeddedFamily(accessorName = "Roboto")
        val mono = embeddedFamily(accessorName = "RobotoMono")

        val forward = renderEmbeddedFonts("p", listOf(roboto, mono), 4)
        val reversed = renderEmbeddedFonts("p", listOf(mono, roboto), 4)

        assertEquals(forward, reversed)
        assertTrue(forward.indexOf("object Roboto {") < forward.indexOf("object RobotoMono {"))
    }

    @Test
    fun `emits the order-contract KDoc above the first payload member only`() {
        val rendered =
            renderEmbeddedFonts(
                "p",
                listOf(
                    embeddedFamily(
                        accessorName = "Roboto",
                        fonts =
                            listOf(
                                embeddedFont(member = "variableFont", path = "fonts/Roboto.ttf", sha256 = "a"),
                                embeddedFont(member = "italicFont", path = "fonts/Roboto-Italic.ttf", sha256 = "b"),
                            ),
                    ),
                ),
                4,
            )

        val kdoc = "/** The family's default face; payload order is significant and the roman comes first. */"
        assertEquals(1, rendered.split(kdoc).size - 1)
        assertTrue(rendered.indexOf(kdoc) > rendered.indexOf("const val SOURCE"))
        assertTrue(rendered.indexOf(kdoc) < rendered.indexOf("val variableFont"))
    }

    @Test
    fun `fails on a duplicate payload member within a family naming the offender`() {
        val roman = embeddedFont(member = "variableFont", path = "fonts/Roboto.ttf", sha256 = "a")
        val duplicate = embeddedFont(member = "variableFont", path = "fonts/Roboto-Italic.ttf", sha256 = "b")
        val error =
            assertFailsWith<IllegalStateException> {
                renderEmbeddedFonts(
                    "p",
                    listOf(embeddedFamily(accessorName = "Roboto", fonts = listOf(roman, duplicate))),
                    4,
                )
            }

        assertContains(error.message.orEmpty(), "Roboto")
        assertContains(error.message.orEmpty(), "variableFont")
    }

    @Test
    fun `fails on a blank payload member naming the family and the path`() {
        val error =
            assertFailsWith<IllegalStateException> {
                renderEmbeddedFonts(
                    "p",
                    listOf(
                        embeddedFamily(
                            accessorName = "Roboto",
                            fonts = listOf(embeddedFont(member = " ", path = "fonts/Roboto.ttf", sha256 = "sha")),
                        ),
                    ),
                    4,
                )
            }

        assertContains(error.message.orEmpty(), "Roboto")
        assertContains(error.message.orEmpty(), "fonts/Roboto.ttf")
    }

    @Test
    fun `fails on an empty payload list naming the family`() {
        val error =
            assertFailsWith<IllegalStateException> {
                renderEmbeddedFonts("p", listOf(embeddedFamily(accessorName = "Roboto", fonts = emptyList())), 4)
            }

        assertContains(error.message.orEmpty(), "Roboto")
    }

    @Test
    fun `fails on a duplicate accessor name naming the offender`() {
        val error =
            assertFailsWith<IllegalStateException> {
                renderEmbeddedFonts(
                    "p",
                    listOf(embeddedFamily(accessorName = "Roboto"), embeddedFamily(accessorName = "Roboto")),
                    4,
                )
            }

        assertContains(error.message.orEmpty(), "Roboto")
    }
}

private fun embeddedFamily(
    accessorName: String,
    family: String = accessorName,
    version: String = "1.000",
    licenseId: String = "OFL-1.1",
    source: String = "https://example.test/$accessorName",
    fonts: List<EmbeddedFont> =
        listOf(embeddedFont(path = "fonts/$accessorName.ttf", sha256 = "sha-$accessorName")),
    licensePath: String = "fonts/$accessorName/OFL.txt",
    licenseBytes: Int = 18,
    licenseSha256: String = "lsha-$accessorName",
    licenseText: String = "license of $accessorName",
) = EmbeddedFontFamily(
    accessorName = accessorName,
    family = family,
    version = version,
    licenseId = licenseId,
    source = source,
    fonts = fonts,
    licensePath = licensePath,
    licenseBytes = licenseBytes,
    licenseSha256 = licenseSha256,
    licenseText = licenseText,
)

private fun embeddedFont(
    path: String,
    sha256: String,
    member: String = "variableFont",
    bytes: Int = 4,
    base64: String = "QUJDRA==",
) = EmbeddedFont(member = member, path = path, bytes = bytes, sha256 = sha256, base64 = base64)
