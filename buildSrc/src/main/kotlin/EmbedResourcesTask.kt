import java.io.File
import java.security.MessageDigest
import java.util.Base64
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register

private const val DEFAULT_CHUNK_SIZE = 32768

/**
 * Reads the files named by [families] under [resourceDir] and writes the
 * generated accessor to [outputDir]. Deterministic: only relative paths, byte
 * sizes and content hashes reach the product.
 */
@CacheableTask
abstract class EmbedResourcesTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val families: ListProperty<EmbeddedFamilySpec>

    @get:Input
    abstract val chunkSize: Property<Int>

    @TaskAction
    fun generate() {
        val root = resourceDir.get().asFile
        val resolved = families.get().map { spec -> resolve(root, spec) }
        val packageNameValue = packageName.get()
        val source = renderEmbeddedFonts(packageNameValue, resolved, chunkSize.get())
        val output = File(outputDir.get().asFile, packageNameValue.replace('.', '/') + "/EmbeddedFonts.kt")
        output.parentFile.mkdirs()
        output.writeText(source)
    }

    private fun resolve(
        root: File,
        spec: EmbeddedFamilySpec,
    ): EmbeddedFontFamily {
        require(spec.accessorName.isNotBlank()) { "family has a blank accessor name: $spec" }
        val font = read(root, spec.font, spec.accessorName)
        val license = read(root, spec.license, spec.accessorName)
        return EmbeddedFontFamily(
            accessorName = spec.accessorName,
            family = spec.family,
            version = spec.version,
            licenseId = spec.licenseId,
            source = spec.source,
            fontPath = spec.font,
            fontBytes = font.size,
            fontSha256 = sha256(font),
            fontBase64 = Base64.getEncoder().encodeToString(font),
            licensePath = spec.license,
            licenseBytes = license.size,
            licenseSha256 = sha256(license),
            licenseText = license.decodeToString(),
        )
    }

    private fun read(
        root: File,
        path: String,
        accessorName: String,
    ): ByteArray {
        require(path.isNotBlank()) { "family $accessorName has a blank file path" }
        val file = File(root, path)
        check(file.isFile) { "family $accessorName is missing resource file: $path" }
        return file.readBytes()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}

/**
 * Registers an [EmbedResourcesTask] named [taskName]. The caller wires the
 * generated directory into whichever source set needs it.
 */
fun Project.embedResources(
    taskName: String,
    packageName: String,
    resourceDir: Directory,
    outputDir: Provider<Directory>,
    families: List<EmbeddedFamilySpec>,
): TaskProvider<EmbedResourcesTask> =
    tasks.register<EmbedResourcesTask>(taskName) {
        description = "Embeds resource files into a generated Kotlin accessor."
        group = "build"
        this.packageName.set(packageName)
        this.resourceDir.set(resourceDir)
        this.outputDir.set(outputDir)
        this.families.set(families)
        chunkSize.convention(DEFAULT_CHUNK_SIZE)
    }
