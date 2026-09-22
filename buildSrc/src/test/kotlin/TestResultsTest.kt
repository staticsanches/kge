import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class TestResultsTest {
    @Test
    fun `a missing directory counts zero`() {
        withTempDir { dir ->
            assertEquals(0, junitXmlTestCount(File(dir, "absent")))
        }
    }

    @Test
    fun `an empty directory counts zero`() {
        withTempDir { dir ->
            assertEquals(0, junitXmlTestCount(dir))
        }
    }

    @Test
    fun `non-XML files are ignored`() {
        withTempDir { dir ->
            File(dir, "results.txt").writeText(suiteXml("""name="a" tests="22""""))
            File(dir, "SUMMARY").writeText("tests=\"22\"")

            assertEquals(0, junitXmlTestCount(dir))
        }
    }

    @Test
    fun `one XML file contributes its tests attribute`() {
        withTempDir { dir ->
            File(dir, "TEST-a.xml").writeText(suiteXml("""name="a" tests="22""""))

            assertEquals(22, junitXmlTestCount(dir))
        }
    }

    @Test
    fun `two XML files are summed`() {
        withTempDir { dir ->
            File(dir, "TEST-a.xml").writeText(suiteXml("""name="a" tests="20""""))
            File(dir, "TEST-b.xml").writeText(suiteXml("""name="b" tests="2""""))

            assertEquals(22, junitXmlTestCount(dir))
        }
    }

    @Test
    fun `an XML reporting zero counts zero`() {
        withTempDir { dir ->
            File(dir, "TEST-a.xml").writeText(suiteXml("""name="a" tests="0""""))

            assertEquals(0, junitXmlTestCount(dir))
        }
    }

    @Test
    fun `an XML without a tests attribute counts zero`() {
        withTempDir { dir ->
            File(dir, "TEST-a.xml").writeText(suiteXml("""name="a""""))

            assertEquals(0, junitXmlTestCount(dir))
        }
    }

    @Test
    fun `an XML in a subdirectory is not counted`() {
        withTempDir { dir ->
            File(dir, "binary").mkdirs()
            File(dir, "binary/TEST-a.xml").writeText(suiteXml("""name="a" tests="22""""))

            assertEquals(0, junitXmlTestCount(dir))
        }
    }
}

private fun suiteXml(attributes: String) =
    """<?xml version="1.0" encoding="UTF-8"?>
       |<testsuite $attributes>
       |</testsuite>
    """.trimMargin()

private fun withTempDir(block: (File) -> Unit) {
    val dir = Files.createTempDirectory("test-results").toFile()
    try {
        block(dir)
    } finally {
        dir.deleteRecursively()
    }
}
