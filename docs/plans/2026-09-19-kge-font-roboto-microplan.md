# `kge-font-roboto` — bundled fonts data module + `buildSrc` converter: micro-plan

**Date:** 2026-09-19. The bundled-fonts round, which lands **before** round C of
the text work (owner, 2026-09-17, option A). Context:
`docs/plans/2026-09-17-font-bundle-findings.md` (font choice, sizes, delivery
spike), `docs/plans/2026-09-17-resource-packaging-mechanism.md` (mechanism,
converter, license layers) and `docs/plans/2026-09-17-fonts-module-touchpoint.md`
(owner decisions). Order: **bundled fonts (this round)** → C face + shaping +
layout, whose micro-plan is revised on top of the accessor pinned here.

## Scope

1. **`buildSrc` converter** — a deterministic, cacheable task that embeds
   resource files into Kotlin source as chunked base64, plus a registration
   helper. It stays KGP-free and knows nothing about fonts.
2. **New data-only module `kge-font-roboto`** (jvm/js/wasmJs, browser-only web,
   no engine dependency) shipping the Roboto and Roboto Mono variable fonts:
   chunked base64, provenance constants and license texts.
3. The committed upstream font + license files, the JVM license resource layer,
   the module's tests, and the CI step that runs the `buildSrc` tests.

**No text/rendering API, no decode helper, no `ByteArray`/`ByteBuffer` on the
surface** — materialisation belongs to the consumer (`kge-text-ttf` round C and
`kge-core`'s `BufferService`).

## Converter (`buildSrc`)

Files: `src/main/kotlin/EmbeddedResources.kt` (pure model + renderer + chunker)
and `src/main/kotlin/EmbedResourcesTask.kt` (the Gradle shell + helper).

```kotlin
data class EmbeddedFamilySpec(
    val accessorName: String,   // generated object name, e.g. "RobotoMono"
    val family: String,         // human family name, e.g. "Roboto Mono"
    val version: String,        // font name-table version, e.g. "3.001"
    val licenseId: String,      // e.g. "OFL-1.1"
    val source: String,         // upstream directory URL
    val font: String,           // path under the resource dir
    val license: String,        // path under the resource dir
) : Serializable

fun chunkBase64(base64: String, chunkSize: Int): List<String>
fun renderEmbeddedFonts(packageName: String, families: List<EmbeddedFontFamily>, chunkSize: Int): String

fun Project.embedResources(
    taskName: String,
    packageName: String,
    resourceDir: Directory,
    outputDir: Provider<Directory>,
    families: List<EmbeddedFamilySpec>,
): TaskProvider<EmbedResourcesTask>
```

- **The renderer is pure and is what the unit tests exercise**; the task only
  resolves paths, reads bytes, hashes them and writes the file. There is no
  TestKit test for the shell: the module's real build executes it, and the
  module's tests assert its product (decision 5).
- `@CacheableTask`, `@InputDirectory @PathSensitive(RELATIVE)`,
  `@OutputDirectory`, `@Input` for the package and families, `@Input
  chunkSize` default **32768**; the accessor file name is fixed
  (`EmbeddedFonts.kt`), so no property exists without an effect. Deterministic
  by construction: families emitted
  sorted by `accessorName`; the provenance header carries relative paths, byte
  sizes and sha256 — never an absolute path or a timestamp.
- Duplicate `accessorName` and missing/blank inputs fail with a message naming
  the offender (renderer and task respectively).
- The generated string literals escape `\`, `"`, `$`, `\n`, `\r`, `\t` and
  `\uXXXX` for the remaining control characters — a Kotlin literal is the
  product, so escaping is part of the contract, not a detail.

## Public API (owner-confirmed 2026-09-19)

One object per family in `dev.staticsanches.kge.font.roboto`, one generated
file `EmbeddedFonts.kt`:

```kotlin
object Roboto {
    const val FAMILY: String = "Roboto"
    const val VERSION: String = "3.015"
    const val LICENSE_ID: String = "OFL-1.1"
    const val SOURCE: String = "https://github.com/google/fonts/tree/main/ofl/roboto"
    val variableFont: List<String>   // chunked base64, 32768-char chunks
    val licenseText: String          // verbatim OFL-1.1
}

object RobotoMono { /* Roboto Mono, 3.001, robotomono/ */ }
```

The generated file opens with a provenance header (family, version, license id,
per-file relative path, byte size, sha256) and the chunk lists are
`persistentListOf(...)`; the public type stays `List<String>`, so
`kotlinx-collections-immutable` is an `implementation` dependency and not part
of the ABI.

## Committed assets and layout

```
kge-font-roboto/
  PROVENANCE.md                                  # reviewer-facing: URL, date, sha256, license
  fonts/roboto/Roboto[wdth,wght].ttf
  fonts/roboto/OFL.txt
  fonts/roboto-mono/RobotoMono[wght].ttf
  fonts/roboto-mono/OFL.txt
  src/jvmMain/resources/META-INF/licenses/roboto/OFL.txt
  src/jvmMain/resources/META-INF/licenses/roboto-mono/OFL.txt
  src/commonTest/kotlin/dev/staticsanches/kge/font/roboto/BundledFontsTest.kt
  src/jvmTest/kotlin/dev/staticsanches/kge/font/roboto/JvmLicenseResourcesTest.kt
```

`fonts/` is a generator input at the module root, **not** a source set
resource root: it must stay out of KMP's `processedResources` (mechanism §1).
The two `jvmMain/resources` files are committed copies, not copied by a Gradle
task — the converter stays out of moving files, and an equality test makes drift
impossible to miss (decision 6).

## Provenance of the committed files (measured 2026-09-19)

| file | bytes | sha256 | version |
|---|---|---|---|
| `fonts/roboto/Roboto[wdth,wght].ttf` | 488,584 | `d7598e12c5dbef095ff8272cfc55da0250bd07fbdecbac8a530b9b277872a134` | 3.015 |
| `fonts/roboto-mono/RobotoMono[wght].ttf` | 183,700 | `66a80e79d17e4c7cabd162e2916578a4cc08fd19eef6e2a643305eae9c567b2b` | 3.001 |
| `fonts/roboto/OFL.txt` | 4,394 | `061402327a96aadb0bfb694a960ed289ecd38d383e396243831ab81feb109c41` | — |
| `fonts/roboto-mono/OFL.txt` | 4,395 | `50ab8dd54680d3473f649c9db86fece88434d097c7834475c1c72d2f8c429215` | — |

```bash
curl -fsSLO https://raw.githubusercontent.com/google/fonts/main/ofl/roboto/Roboto%5Bwdth,wght%5D.ttf
curl -fsSLO https://raw.githubusercontent.com/google/fonts/main/ofl/roboto/OFL.txt
curl -fsSLO https://raw.githubusercontent.com/google/fonts/main/ofl/robotomono/RobotoMono%5Bwght%5D.ttf
curl -fsSLO https://raw.githubusercontent.com/google/fonts/main/ofl/robotomono/OFL.txt
```

**Correction to the findings record:** the variable builds are Roboto **3.015**
and Roboto Mono **3.001** (both name tables read 2026-09-19), not "3.016"; sizes
match the spike exactly (488,584 / 183,700 B), so the spike measured these very
files and only the version label was wrong. The licenses are the two verbatim
upstream OFL files (`Copyright 2011 The Roboto Project Authors`,
`Copyright 2015 The Roboto Mono Project Authors`), each ending in a single LF.
Base64: 651,448 chars (20 chunks, last 28,856) and 244,936 chars (8 chunks, last
15,560); gzip at `-9` gives 280,260 / 128,824 B — the A′ escalation numbers.

## Test contract

`commonTest` runs on all three targets; the payload fingerprint is FNV-1a 64
computed in the test, because common Kotlin has no SHA-256 (the sha256 is a
build-time record, emitted into the generated header and `PROVENANCE.md`).

```kotlin
package dev.staticsanches.kge.font.roboto

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.io.encoding.Base64

class BundledFontsTest : FunSpec({
    test("Roboto variable font decodes to the pinned bytes") {
        val bytes = decode(Roboto.variableFont)
        bytes.size shouldBe 488_584
        bytes.copyOfRange(0, 4).map { it.toInt() } shouldBe listOf(0, 1, 0, 0) // sfnt 0x00010000
        fnv1a64(bytes) shouldBe -4_869_763_841_651_235_108L
    }

    test("Roboto Mono variable font decodes to the pinned bytes") {
        val bytes = decode(RobotoMono.variableFont)
        bytes.size shouldBe 183_700
        bytes.copyOfRange(0, 4).map { it.toInt() } shouldBe listOf(0, 1, 0, 0)
        fnv1a64(bytes) shouldBe 17_806_645_914_498_892L
    }

    test("the payload is chunked at 32768 characters, remainder last") {
        Roboto.variableFont.size shouldBe 20
        Roboto.variableFont.dropLast(1).forEach { it.length shouldBe 32_768 }
        Roboto.variableFont.last().length shouldBe 28_856
        RobotoMono.variableFont.size shouldBe 8
        RobotoMono.variableFont.dropLast(1).forEach { it.length shouldBe 32_768 }
        RobotoMono.variableFont.last().length shouldBe 15_560
    }

    test("provenance constants identify the shipped builds") {
        Roboto.FAMILY shouldBe "Roboto"
        Roboto.VERSION shouldBe "3.015"
        Roboto.LICENSE_ID shouldBe "OFL-1.1"
        Roboto.SOURCE shouldBe "https://github.com/google/fonts/tree/main/ofl/roboto"
        RobotoMono.FAMILY shouldBe "Roboto Mono"
        RobotoMono.VERSION shouldBe "3.001"
        RobotoMono.SOURCE shouldBe "https://github.com/google/fonts/tree/main/ofl/robotomono"
    }

    test("license texts are the verbatim upstream OFL files") {
        Roboto.licenseText.length shouldBe 4_394
        Roboto.licenseText.startsWith("Copyright 2011 The Roboto Project Authors") shouldBe true
        Roboto.licenseText.contains("SIL Open Font License, Version 1.1") shouldBe true
        RobotoMono.licenseText.length shouldBe 4_395
        RobotoMono.licenseText.startsWith("Copyright 2015 The Roboto Mono Project Authors") shouldBe true
    }
})

private fun decode(chunks: List<String>): ByteArray = Base64.Default.decode(chunks.joinToString(""))

/** FNV-1a 64 (offset basis 0xcbf29ce484222325, prime 0x100000001b3). */
private fun fnv1a64(bytes: ByteArray): Long {
    var hash = -3_750_763_034_362_895_579L
    for (byte in bytes) hash = (hash xor (byte.toLong() and 0xFF)) * 1_099_511_628_211L
    return hash
}
```

`jvmTest` closes the license chain, and a `check`-wired task closes the jar one:

```kotlin
class JvmLicenseResourcesTest : FunSpec({
    test("each family's license ships verbatim in jvmMain resources") {
        resource("/META-INF/licenses/roboto/OFL.txt") shouldBe Roboto.licenseText
        resource("/META-INF/licenses/roboto-mono/OFL.txt") shouldBe RobotoMono.licenseText
    }
})

private fun resource(path: String): String =
    checkNotNull(JvmLicenseResourcesTest::class.java.getResourceAsStream(path))
        .use { it.readBytes().decodeToString() }
```

`VerifyJarLicenseEntriesTask` (private abstract class in the module build
script) opens `jvmJar`'s archive, asserts both entries exist and that each
entry's text equals the committed `fonts/<family>/OFL.txt`; it is wired
`check.dependsOn(verifyJvmLicenseJar)` with
`verifyJvmLicenseJar.dependsOn(jvmJar)`. Content is asserted, never mere
presence — the phantom-green lesson of the mechanism doc §1.

## Module build wiring

Mirror `kge-text-ttf`: `kotlin.multiplatform` + `ksp` + `ktlint` + `kotest`,
`jvm()` with `JVM_11`, `js(IR)`/`wasmJs` browser with ChromeHeadless,
`useJUnitPlatform()`, and the ktlint `/build/generated/` exclusion on both the
extension and the check tasks. Dependencies: `commonMain` —
`implementation(libs.kotlinx.collections.immutable)`; `commonTest` — kotest
framework + assertions; `jvmTest` — kotest runner. Nothing else; no `kge-core`,
no image/text/engine dependency.

The generator is registered once and wired into the production source set:

```kotlin
val generateEmbeddedFonts = embedResources(
    taskName = "generateEmbeddedFonts",
    packageName = "dev.staticsanches.kge.font.roboto",
    resourceDir = layout.projectDirectory.dir("fonts"),
    outputDir = layout.buildDirectory.dir("generated/font/commonMain/kotlin"),
    families = listOf(
        EmbeddedFamilySpec("Roboto", "Roboto", "3.015", "OFL-1.1",
            "https://github.com/google/fonts/tree/main/ofl/roboto",
            "roboto/Roboto[wdth,wght].ttf", "roboto/OFL.txt"),
        EmbeddedFamilySpec("RobotoMono", "Roboto Mono", "3.001", "OFL-1.1",
            "https://github.com/google/fonts/tree/main/ofl/robotomono",
            "roboto-mono/RobotoMono[wght].ttf", "roboto-mono/OFL.txt"),
    ),
)

kotlin { sourceSets { commonMain { kotlin.srcDir(generateEmbeddedFonts.flatMap { it.outputDir }) } } }
```

`settings.gradle.kts` gains `include("kge-font-roboto")`; CI
(`.github/workflows/build.yaml`) gains
`./gradlew -p buildSrc build --rerun-tasks --stacktrace` before the existing
build step, because the root `build` does **not** execute `buildSrc`'s own test
task (the touchpoint's "gate includes the `buildSrc` tests" is only true with
this second command).

## Decisions (recorded)

1. **Module and accessor shape** (owner, 2026-09-19): `kge-font-roboto` holding
   both families, one object per family with provenance constants, payload
   named `variableFont`.
2. **Explicit family specs, not names derived from the resource tree.** The
   upstream filenames (`Roboto[wdth,wght].ttf`) are not identifiers and derive
   no version/source; the spec keeps the public API legible and the provenance
   accurate while the emitted order stays deterministic (sorted by accessor).
3. **The `buildSrc` tests are run by their own command** (`-p buildSrc build`),
   in the gate and in CI: `buildSrc` is not part of the main build's task graph,
   so a single root `build` cannot cover them.
4. **FNV-1a 64 in `commonTest`, sha256 in the generated header.** Common Kotlin
   has no SHA-256, and a pin with a stdlib-only hash is enough to catch a lost
   chunk or a reordered seam on every target.
5. **No TestKit test for the task.** The pure renderer carries the behavioral
   tests; the shell is a thin I/O layer proven by the module's real build and by
   the content assertions on its product.
6. **The JVM license copies are committed, not copied by Gradle.** The
   converter's contract is "render source from inputs", not "move files"; the
   equality test (resource ↔ embedded text) makes the duplication safe.
7. **`SOURCE` is public provenance.** The family, version, license id and
   upstream URL are exactly what an application's open-source-licenses screen
   needs; the sha256 stays in the generated header, where it belongs to the
   build rather than to consumers.

## Steps (TDD: red → green per feature)

1. **buildSrc test wiring + renderer (red → green).** Add
   `testImplementation(kotlin("test"))` and `useJUnitPlatform()` to
   `buildSrc/build.gradle.kts`; write
   `buildSrc/src/test/kotlin/EmbeddedResourcesTest.kt` (rendered object shape and
   constants; chunk seam and remainder; escaping; order independence;
   duplicate-name failure). Run `./gradlew -p buildSrc test --rerun-tasks` — red:
   the renderer does not exist. Implement `EmbeddedResources.kt`; green.
2. **Task + registration helper (config).** `EmbedResourcesTask` and
   `embedResources(...)` per the contract above. Verify
   `./gradlew -p buildSrc build --rerun-tasks` is green.
3. **Module scaffold + committed assets (config).** `include("kge-font-roboto")`,
   the module build script (no generator wiring yet), the four font/license
   files, the two `jvmMain/resources` copies and `PROVENANCE.md`. Verify
   `./gradlew :kge-font-roboto:build --rerun-tasks` is green.
4. **Data contract tests (red).** Add `BundledFontsTest.kt` and
   `JvmLicenseResourcesTest.kt` above. Run
   `./gradlew :kge-font-roboto:jvmTest --rerun-tasks` — red: the `Roboto` /
   `RobotoMono` accessors do not exist.
5. **Wire the generator (green).** Register `generateEmbeddedFonts` and the
   `commonMain` `srcDir`. Run `:kge-font-roboto:jvmTest`,
   `:kge-font-roboto:jsBrowserTest` and `:kge-font-roboto:wasmJsBrowserTest`
   with `--rerun-tasks`; all three must pass the same suite.
6. **Jar-layer assertion (config).** Add `VerifyJarLicenseEntriesTask` and its
   `check` wiring; run `./gradlew :kge-font-roboto:check --rerun-tasks`.
7. **CI + round close.** Add the `buildSrc` step to the workflow; run the gate
   (`./gradlew -p buildSrc build --rerun-tasks` then `./gradlew build
   --rerun-tasks`); two-axis review; new decisions chunk
   `docs/decisions/phase-1/31-font-bundle.md` + index row; one commit.

## Out of scope

- Round C proper: `Font`, HarfBuzz shaping, layout and its re-measured
  constants — its micro-plan is revised on this module, in its own round and
  commit.
- Publishing (Central Portal, POM `<licenses>`), A′ (`base64(gzip(raw))`),
  streaming sources and a source/URL entry point (mechanism §4–§5).
- Migrating the golden-image codegen onto the converter, image decoding, and any
  decode/materialize helper on the data module.
- Variable-axis selection: the payload is the font's default instance.

## Gate

`./gradlew -p buildSrc build --rerun-tasks` and `./gradlew build --rerun-tasks`
green (three targets, the new module, the generated source's metadata
compilation), then the two-axis review, then the decisions-log entry, then one
commit for the round. Gradle in this sandbox needs a one-shot
`danger-full-access` escalation (it writes under `~/.gradle`, outside the
workspace).
