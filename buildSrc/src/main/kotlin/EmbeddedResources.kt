import java.io.Serializable

/** A family's file set to embed, with the provenance the generator records. */
data class EmbeddedFamilySpec(
    val accessorName: String,
    val family: String,
    val version: String,
    val licenseId: String,
    val source: String,
    val font: String,
    val license: String,
) : Serializable

/** A family whose files were read and hashed, ready to render. */
data class EmbeddedFontFamily(
    val accessorName: String,
    val family: String,
    val version: String,
    val licenseId: String,
    val source: String,
    val fontPath: String,
    val fontBytes: Int,
    val fontSha256: String,
    val fontBase64: String,
    val licensePath: String,
    val licenseBytes: Int,
    val licenseSha256: String,
    val licenseText: String,
)

/** Splits [base64] into [chunkSize]-character chunks; the last chunk carries the remainder. */
fun chunkBase64(
    base64: String,
    chunkSize: Int,
): List<String> {
    require(chunkSize > 0) { "chunkSize must be positive: $chunkSize" }
    return base64.chunked(chunkSize)
}

/**
 * Renders one Kotlin accessor object per family into a single source file for
 * [packageName]. Families are emitted sorted by accessor name, so the product
 * is independent of the input order; a duplicate accessor name fails.
 */
fun renderEmbeddedFonts(
    packageName: String,
    families: List<EmbeddedFontFamily>,
    chunkSize: Int,
): String {
    require(packageName.isNotBlank()) { "packageName must not be blank" }
    val duplicates = families.groupBy { it.accessorName }.filterValues { it.size > 1 }.keys.sorted()
    check(duplicates.isEmpty()) { "duplicate accessor name(s): $duplicates" }
    val ordered = families.sortedBy { it.accessorName }

    return buildString {
        appendLine("// Generated from the committed resource inputs - do not edit.")
        ordered.forEach { family ->
            appendLine("//")
            appendLine("// ${family.family} ${family.version} (${family.licenseId})")
            appendLine("//   ${family.fontPath} - ${family.fontBytes} B - sha256 ${family.fontSha256}")
            appendLine("//   ${family.licensePath} - ${family.licenseBytes} B - sha256 ${family.licenseSha256}")
        }
        appendLine()
        appendLine("package $packageName")
        appendLine()
        appendLine("import kotlinx.collections.immutable.persistentListOf")
        ordered.forEach { family ->
            appendLine()
            append(renderFamily(family, chunkSize))
        }
    }
}

private fun renderFamily(
    family: EmbeddedFontFamily,
    chunkSize: Int,
): String =
    buildString {
        appendLine("object ${family.accessorName} {")
        appendLine("    const val FAMILY: String = ${literal(family.family)}")
        appendLine("    const val VERSION: String = ${literal(family.version)}")
        appendLine("    const val LICENSE_ID: String = ${literal(family.licenseId)}")
        appendLine("    const val SOURCE: String = ${literal(family.source)}")
        appendLine("    val variableFont: List<String> =")
        appendLine("        persistentListOf(")
        chunkBase64(family.fontBase64, chunkSize).forEach { chunk ->
            appendLine("            ${literal(chunk)},")
        }
        appendLine("        )")
        appendLine("    val licenseText: String = ${literal(family.licenseText)}")
        appendLine("}")
    }

/** Renders [value] as a double-quoted Kotlin literal, escaping control characters. */
private fun literal(value: String): String =
    buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '$' -> append("\\\$")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (character.code < 0x20) {
                        append("\\u").append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        append(character)
                    }
                }
            }
        }
        append('"')
    }
