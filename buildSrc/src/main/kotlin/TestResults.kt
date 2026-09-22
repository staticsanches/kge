import java.io.File

/** Sums the `tests` attribute of the JUnit XML files directly inside [resultsDir]. */
fun junitXmlTestCount(resultsDir: File): Int =
    resultsDir
        .listFiles()
        .orEmpty()
        .filter { it.isFile && it.extension == "xml" }
        .sumOf { xmlTestCount(it) }

private val testsAttribute = Regex("""tests="(\d+)"""")

private fun xmlTestCount(file: File): Int =
    testsAttribute
        .find(file.readText())
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()
        ?: 0
