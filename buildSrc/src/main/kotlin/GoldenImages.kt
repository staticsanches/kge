import java.util.Base64

/** One golden image's decoded identity, ready to render. */
data class GoldenImageSpec(
    val name: String,
    val width: Int,
    val height: Int,
    val rgbaBase64: String,
)

/**
 * Renders the golden-image accessor for [packageName]: one `GoldenImage` per
 * spec, sorted by name, plus the one-argument binder onto the shared matcher.
 * Deterministic — the input order does not reach the product.
 */
fun renderGoldenImages(
    packageName: String,
    images: List<GoldenImageSpec>,
): String {
    require(packageName.isNotBlank()) { "packageName must not be blank" }
    images.forEach { spec ->
        require(spec.name.isNotBlank()) { "golden image name must not be blank" }
        require(spec.width > 0 && spec.height > 0) {
            "golden image \"${spec.name}\" needs positive dimensions: ${spec.width}x${spec.height}"
        }
        require(isBase64(spec.rgbaBase64)) { "golden image \"${spec.name}\" carries malformed base64" }
    }
    val duplicates = images.groupBy { it.name }.filterValues { it.size > 1 }.keys
    check(duplicates.isEmpty()) { "duplicate golden image name(s): ${duplicates.sorted()}" }

    val entries =
        images
            .sortedBy { it.name }
            .joinToString(",\n") { spec ->
                "            GoldenImage(\"${spec.name}\", ${spec.width}, ${spec.height}, \"${spec.rgbaBase64}\")"
            }
    // A bare listOf() cannot infer its element type, so the empty set names it.
    val accessor =
        if (entries.isEmpty()) {
            "        emptyList<GoldenImage>()"
        } else {
            "        listOf(\n$entries\n        )"
        }

    return buildString {
        appendLine("package $packageName")
        appendLine()
        appendLine("import dev.staticsanches.kge.image.Pixmap")
        appendLine("import dev.staticsanches.kge.testsupport.golden.GoldenImage")
        appendLine("import dev.staticsanches.kge.testsupport.golden.shouldMatchGolden")
        appendLine()
        appendLine("object GoldenImages {")
        appendLine("    val byName: Map<String, GoldenImage> =")
        appendLine("$accessor.associateBy { it.name }")
        appendLine("}")
        appendLine()
        appendLine("/** Asserts this surface against this module's golden [name]. */")
        appendLine("fun Pixmap.shouldMatchGolden(name: String) = this.shouldMatchGolden(GoldenImages.byName, name)")
    }
}

private fun isBase64(value: String): Boolean =
    try {
        Base64.getDecoder().decode(value)
        true
    } catch (exception: IllegalArgumentException) {
        false
    }
