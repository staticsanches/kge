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
    fontPath: String = "fonts/$accessorName.ttf",
    fontBytes: Int = 4,
    fontSha256: String = "sha-$accessorName",
    fontBase64: String = "QUJDRA==",
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
    fontPath = fontPath,
    fontBytes = fontBytes,
    fontSha256 = fontSha256,
    fontBase64 = fontBase64,
    licensePath = licensePath,
    licenseBytes = licenseBytes,
    licenseSha256 = licenseSha256,
    licenseText = licenseText,
)
