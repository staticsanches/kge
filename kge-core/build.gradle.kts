import java.awt.image.BufferedImage
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp.gradle)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotest.plugin)
}

kotlin {
    // Project-wide opt-in: engine code uses sensitive members without repeating
    // the annotation; external consumers still face the compile-time error.
    compilerOptions {
        optIn.add("dev.staticsanches.kge.annotations.KGESensitiveAPI")
        // ByteBuffer is an expect/actual class (JDK-NIO typealias on JVM,
        // TypedArray emulation on web) — a Beta feature (KT-61573) that the
        // compiler recommends silencing with this flag.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
            // java.nio.ByteBuffer is `sealed` since JDK 21; the expect/actual
            // modality check fails against the running JDK's metadata, so the
            // JVM API surface is taken from the JDK 11 release (plain abstract
            // class). Runtime stays the JDK 21 class.
            freeCompilerArgs.add("-Xjdk-release=11")
        }
    }
    // The web targets are browser-only: the engine runs in the browser and the
    // Karma/ChromeHeadless tasks are the web suites. Node is intentionally not
    // a target — browser-only capabilities such as createImageBitmap must stay
    // reachable and testable.
    js(IR) {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
    }
    wasmJs {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
    }

    val lwjglNatives = lwjglNativesClassifier()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlin.logging)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kotlinx.coroutines.core)
        }
        webMain.dependencies {
            implementation(libs.kotlin.js)
            implementation(libs.kotlinx.browser)
            // The GL handles and the WebGL2 backend are kotlin-wrappers
            // `web.gl` DOM types, not the kotlinx browser ones.
            implementation(libs.kotlin.browser)
        }
        jvmMain.dependencies {
            // kotlin-logging 8.0.4 (jvm variant) dropped its compile-scope
            // slf4j-api dependency, but its JVM logger factory needs it at
            // runtime; declare it explicitly.
            implementation(libs.slf4j.api)
            // BOM in `platform()` form supplies the versionless lwjgl-core and
            // lwjgl-stb (PNG via STB).
            implementation(project.dependencies.platform(libs.lwjgl.bom))
            implementation(libs.lwjgl.core)
            implementation(libs.lwjgl.stb)
            implementation(libs.lwjgl.opengl)
            // The device seam's JVM implementation drives a GLFW window's
            // context; the window itself is created by the owner (window
            // concept, later).
            implementation(libs.lwjgl.glfw)
            // Host natives go on the production runtime classpath so a downstream
            // JVM consumer can load LWJGL (the test source set inherits them).
            runtimeOnly(libs.lwjgl.core.get()) {
                artifact {
                    classifier = lwjglNatives
                }
            }
            runtimeOnly(libs.lwjgl.stb.get()) {
                artifact {
                    classifier = lwjglNatives
                }
            }
            runtimeOnly(libs.lwjgl.opengl.get()) {
                artifact {
                    classifier = lwjglNatives
                }
            }
            runtimeOnly(libs.lwjgl.glfw.get()) {
                artifact {
                    classifier = lwjglNatives
                }
            }
        }
        commonTest.dependencies {
            implementation(libs.kotest.framework)
            implementation(libs.kotest.assertions)
        }
        // The WebGL2 smoke probe uses kotlin-wrappers `web.*`; the test source
        // set does not inherit webMain's `implementation` deps.
        webTest.dependencies {
            implementation(libs.kotlin.browser)
        }
        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // macOS/AppKit requires GLFW (and GL context work) on the process's first
    // thread; without this the JVM GL smoke test aborts in glfwInit.
    if (System.getProperty("os.name").startsWith("Mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
}

ktlint {
    filter {
        // KSP-generated test discovery code (io.kotest.framework.runtime) under
        // build/generated/ is third-party generated — not ours to lint.
        exclude { element -> element.file.invariantSeparatorsPath.contains("/build/generated/") }
    }
}

// ktlint-gradle 14.2.0 wires the extension filter only into the check tasks;
// the format tasks need the same exclusion per task (both are PatternFilterable).
tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
    exclude { element -> element.file.invariantSeparatorsPath.contains("/build/generated/") }
}

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

abstract class GenerateGoldenImagesTask : DefaultTask() {
    @get:InputDirectory
    abstract val inputDir: DirectoryProperty

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

        val file = File(outputDir.get().asFile, "dev/staticsanches/kge/golden/GoldenImages.kt")
        file.parentFile.mkdirs()
        file.writeText(render(images))
    }

    private fun decodeGolden(
        file: File,
        name: String,
    ): GoldenImage {
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
        return GoldenImage(name, width, height, rgba)
    }

    private fun render(images: List<GoldenImage>): String {
        val encoder = Base64.getEncoder()
        val entries =
            images.joinToString(",\n") { golden ->
                "            GoldenImage(\"${golden.name}\", ${golden.width}, ${golden.height}, " +
                    "\"${encoder.encodeToString(golden.rgba)}\")"
            }
        return buildString {
            appendLine("package dev.staticsanches.kge.golden")
            appendLine()
            appendLine("object GoldenImages {")
            appendLine("    val byName: Map<String, GoldenImage> =")
            appendLine("        listOf(")
            appendLine(entries)
            appendLine("        ).associateBy { it.name }")
            appendLine("}")
            appendLine()
            appendLine("class GoldenImage(")
            appendLine("    val name: String,")
            appendLine("    val width: Int,")
            appendLine("    val height: Int,")
            appendLine("    val rgbaBase64: String,")
            appendLine(")")
        }
    }

    private class GoldenImage(
        val name: String,
        val width: Int,
        val height: Int,
        val rgba: ByteArray,
    )

    private companion object {
        const val MAX_DIMENSION = 4096
    }
}

val generateGoldenImages =
    tasks.register<GenerateGoldenImagesTask>("generateGoldenImages") {
        description = "Decodes the committed golden PNGs into a typed Kotlin accessor."
        group = "build"
        inputDir.set(layout.projectDirectory.dir("src/commonTest/golden"))
        outputDir.set(layout.buildDirectory.dir("generated/golden/commonTest/kotlin"))
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

tasks.register<GoldenActualToPngTask>("goldenActualToPng") {
    description = "Renders a shouldMatchGolden 'WxH:<base64>' token to an RGBA PNG under build/."
    group = "verification"
    token.set(providers.gradleProperty("goldenActual"))
    providers.gradleProperty("goldenActualFile").orNull?.let { tokenFile.fileValue(File(it)) }
    outputPng.set(layout.buildDirectory.file("golden-actual.png"))
}

kotlin {
    sourceSets {
        commonTest {
            kotlin.srcDir(generateGoldenImages.flatMap { it.outputDir })
        }
    }
}
