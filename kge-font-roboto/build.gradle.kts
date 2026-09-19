import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp.gradle)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotest.plugin)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

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

    sourceSets {
        commonMain.dependencies {
            // The generated accessors return persistentListOf; the public type
            // stays List<String>, so this is not part of the module's ABI.
            implementation(libs.kotlinx.collections.immutable)
        }
        commonTest.dependencies {
            implementation(libs.kotest.framework)
            implementation(libs.kotest.assertions)
        }
        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

ktlint {
    filter {
        exclude { element -> element.file.invariantSeparatorsPath.contains("/build/generated/") }
    }
}

// ktlint-gradle 14.2.0 wires the extension filter only into the check tasks;
// the format tasks need the same exclusion per task (both are PatternFilterable).
tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
    exclude { element -> element.file.invariantSeparatorsPath.contains("/build/generated/") }
}

abstract class VerifyJarLicenseEntriesTask : DefaultTask() {
    @get:InputFile
    abstract val jar: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val licenseFiles: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        ZipFile(jar.get().asFile).use { archive ->
            licenseFiles.files.sortedBy { it.path }.forEach { committed ->
                val family = committed.parentFile.name
                val entryName = "META-INF/licenses/$family/${committed.name}"
                val entry = archive.getEntry(entryName)
                check(entry != null) { "jvmJar is missing license entry: $entryName" }
                val shipped = archive.getInputStream(entry).use { it.readBytes().decodeToString() }
                check(shipped == committed.readText()) {
                    "jvmJar entry $entryName does not match ${committed.path}"
                }
            }
        }
    }
}

val generateEmbeddedFonts =
    embedResources(
        taskName = "generateEmbeddedFonts",
        packageName = "dev.staticsanches.kge.font.roboto",
        resourceDir = layout.projectDirectory.dir("fonts"),
        outputDir = layout.buildDirectory.dir("generated/font/commonMain/kotlin"),
        families =
            listOf(
                EmbeddedFamilySpec(
                    accessorName = "Roboto",
                    family = "Roboto",
                    version = "3.015",
                    licenseId = "OFL-1.1",
                    source = "https://github.com/google/fonts/tree/main/ofl/roboto",
                    font = "roboto/Roboto[wdth,wght].ttf",
                    license = "roboto/OFL.txt",
                ),
                EmbeddedFamilySpec(
                    accessorName = "RobotoMono",
                    family = "Roboto Mono",
                    version = "3.001",
                    licenseId = "OFL-1.1",
                    source = "https://github.com/google/fonts/tree/main/ofl/robotomono",
                    font = "roboto-mono/RobotoMono[wght].ttf",
                    license = "roboto-mono/OFL.txt",
                ),
            ),
    )

private val verifyJvmLicenseJar =
    tasks.register<VerifyJarLicenseEntriesTask>("verifyJvmLicenseJar") {
        description = "Asserts the JVM jar carries both family licenses verbatim."
        group = "verification"
        jar.set(tasks.named<org.gradle.jvm.tasks.Jar>("jvmJar").flatMap { it.archiveFile })
        licenseFiles.from(
            layout.projectDirectory.file("fonts/roboto/OFL.txt"),
            layout.projectDirectory.file("fonts/roboto-mono/OFL.txt"),
        )
    }

tasks.named("check") {
    dependsOn(verifyJvmLicenseJar)
}
verifyJvmLicenseJar.configure {
    dependsOn(tasks.named("jvmJar"))
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir(generateEmbeddedFonts.flatMap { it.outputDir })
        }
    }
}
