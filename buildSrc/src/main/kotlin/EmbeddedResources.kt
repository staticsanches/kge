import java.io.Serializable

/** One payload of a family to embed, with the generated accessor member it renders as. */
data class EmbeddedFontSpec(
    val member: String,
    val path: String,
) : Serializable

/** A family's file set to embed, with the provenance the generator records. */
data class EmbeddedFamilySpec(
    val accessorName: String,
    val family: String,
    val version: String,
    val licenseId: String,
    val source: String,
    val fonts: List<EmbeddedFontSpec>,
    val license: String,
) : Serializable

/** One family payload whose bytes were read and hashed, ready to render. */
data class EmbeddedFont(
    val member: String,
    val path: String,
    val bytes: Int,
    val sha256: String,
    val base64: String,
)

/** A family whose files were read and hashed, ready to render. */
data class EmbeddedFontFamily(
    val accessorName: String,
    val family: String,
    val version: String,
    val licenseId: String,
    val source: String,
    val fonts: List<EmbeddedFont>,
    val licensePath: String,
    val licenseBytes: Int,
    val licenseSha256: String,
    val licenseText: String,
)

/** Emitted above a family's first payload member, whose order the loader relies on. */
private const val DEFAULT_FACE_KDOC =
    "    /** The family's default face; payload order is significant and the roman comes first. */"

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
    val duplicates =
        families
            .groupBy { it.accessorName }
            .filterValues { it.size > 1 }
            .keys
            .sorted()
    check(duplicates.isEmpty()) { "duplicate accessor name(s): $duplicates" }
    families.forEach { family ->
        check(family.fonts.isNotEmpty()) { "family ${family.accessorName} has no payloads" }
        family.fonts.forEach { font ->
            check(font.member.isNotBlank()) { "family ${family.accessorName} has a blank payload member: ${font.path}" }
        }
        val duplicateMembers =
            family.fonts
                .groupBy { it.member }
                .filterValues { it.size > 1 }
                .keys
                .sorted()
        check(duplicateMembers.isEmpty()) {
            "family ${family.accessorName} has duplicate payload member(s): $duplicateMembers"
        }
    }
    val ordered = families.sortedBy { it.accessorName }

    return buildString {
        appendLine("// Generated from the committed resource inputs - do not edit.")
        ordered.forEach { family ->
            appendLine("//")
            appendLine("// ${family.family} ${family.version} (${family.licenseId})")
            family.fonts.forEach { font ->
                appendLine("//   ${font.path} - ${font.bytes} B - sha256 ${font.sha256}")
            }
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
        family.fonts.forEachIndexed { index, font ->
            if (index == 0) appendLine(DEFAULT_FACE_KDOC)
            appendLine("    val ${font.member}: List<String> =")
            appendLine("        persistentListOf(")
            chunkBase64(font.base64, chunkSize).forEach { chunk ->
                appendLine("            ${literal(chunk)},")
            }
            appendLine("        )")
        }
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
