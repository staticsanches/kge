import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import java.awt.image.BufferedImage
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO

// ImageIO exposes non-premultiplied 0xAARRGGBB; the harness stores R,G,B,A to
// match Pixel.nativeRGBA's little-endian byte order.
private object GoldenRgba {
    fun argbToRgba(
        argb: Int,
        out: ByteArray,
        offset: Int,
    ) {
        out[offset] = ((argb ushr 16) and 0xFF).toByte()
        out[offset + 1] = ((argb ushr 8) and 0xFF).toByte()
        out[offset + 2] = (argb and 0xFF).toByte()
        out[offset + 3] = ((argb ushr 24) and 0xFF).toByte()
    }

    fun rgbaToArgb(
        bytes: ByteArray,
        offset: Int,
    ): Int =
        ((bytes[offset].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            (bytes[offset + 2].toInt() and 0xFF) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}

/**
 * Decodes every PNG under [inputDir] (name = path relative to it, without
 * extension) into the [packageName] accessor under [outputDir].
 */
abstract class GenerateGoldenImagesTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDir: DirectoryProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val root = inputDir.get().asFile
        val images =
            root
                .walkTopDown()
                .filter { it.isFile && it.extension.equals("png", ignoreCase = true) }
                .sortedBy { it.invariantSeparatorsPath }
                .map { decodeGolden(it, it.relativeTo(root).invariantSeparatorsPath.removeSuffix(".png")) }
                .toList()
        val duplicates = images.groupBy { it.name }.filterValues { it.size > 1 }.keys
        check(duplicates.isEmpty()) { "Golden images map to duplicate names: ${duplicates.sorted()}" }

        val output = outputDir.get().asFile
        val file = File(output, packageName.get().replace('.', '/') + "/GoldenImages.kt")
        file.parentFile.mkdirs()
        file.writeText(renderGoldenImages(packageName.get(), images))
    }

    private fun decodeGolden(
        file: File,
        name: String,
    ): GoldenImageSpec {
        val image = ImageIO.read(file) ?: error("Golden image is not decodable: ${file.path}")
        val width = image.width
        val height = image.height
        check(width > 0 && height > 0) { "Golden image has non-positive dimensions: ${file.path} (${width}x$height)" }
        check(width <= MAX_DIMENSION && height <= MAX_DIMENSION) {
            "Golden image exceeds the ${MAX_DIMENSION}px guard: ${file.path} (${width}x$height)"
        }
        val rgba = ByteArray(width * height * 4)
        var index = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                GoldenRgba.argbToRgba(image.getRGB(x, y), rgba, index)
                index += 4
            }
        }
        return GoldenImageSpec(name, width, height, Base64.getEncoder().encodeToString(rgba))
    }

    private companion object {
        const val MAX_DIMENSION = 4096
    }
}

abstract class GoldenActualToPngTask : DefaultTask() {
    @get:Input
    @get:Optional
    abstract val token: Property<String>

    @get:InputFile
    @get:Optional
    abstract val tokenFile: RegularFileProperty

    @get:OutputFile
    abstract val outputPng: RegularFileProperty

    @TaskAction
    fun convert() {
        val raw =
            token.orNull?.trim()?.ifEmpty { null }
                ?: tokenFile.orNull
                    ?.asFile
                    ?.readText()
                    ?.trim()
                ?: error("""missing -PgoldenActual="WxH:<base64>" (or -PgoldenActualFile=<path>)""")

        val separator = raw.indexOf(':')
        check(separator > 0) { "malformed golden token, expected 'WxH:<base64>': \"$raw\"" }
        val dimensions = raw.substring(0, separator)
        val x = dimensions.indexOf('x')
        check(x > 0) { "malformed golden token dimensions \"$dimensions\" in \"$raw\"" }
        val width = dimensions.substring(0, x).toIntOrNull()
        val height = dimensions.substring(x + 1).toIntOrNull()
        check(width != null && height != null) { "malformed golden token dimensions \"$dimensions\" in \"$raw\"" }
        check(width > 0 && height > 0) { "golden token dimensions must be positive: \"$dimensions\"" }

        val bytes =
            try {
                Base64.getDecoder().decode(raw.substring(separator + 1))
            } catch (exception: IllegalArgumentException) {
                error("malformed golden token base64: ${exception.message}")
            }
        check(bytes.size == width * height * 4) {
            "golden token \"$dimensions\" needs ${width * height * 4} bytes but carries ${bytes.size}"
        }

        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        var offset = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                image.setRGB(x, y, GoldenRgba.rgbaToArgb(bytes, offset))
                offset += 4
            }
        }
        val output = outputPng.get().asFile
        output.parentFile.mkdirs()
        ImageIO.write(image, "png", output)
    }
}

/**
 * Registers the golden PNG generator as [taskName] plus the token-to-PNG
 * diagnostic, returning the generator so a source set can consume its output.
 */
fun Project.goldenImages(
    taskName: String,
    packageName: String,
    inputDir: Directory,
    outputDir: Provider<Directory>,
): TaskProvider<GenerateGoldenImagesTask> {
    tasks.register<GoldenActualToPngTask>("goldenActualToPng") {
        description = "Renders a shouldMatchGolden 'WxH:<base64>' token to an RGBA PNG under build/."
        group = "verification"
        token.set(providers.gradleProperty("goldenActual"))
        providers.gradleProperty("goldenActualFile").orNull?.let { tokenFile.fileValue(File(it)) }
        outputPng.set(layout.buildDirectory.file("golden-actual.png"))
    }
    return tasks.register<GenerateGoldenImagesTask>(taskName) {
        description = "Decodes the committed golden PNGs into a typed Kotlin accessor."
        group = "build"
        this.packageName.set(packageName)
        this.inputDir.set(inputDir)
        this.outputDir.set(outputDir)
    }
}
